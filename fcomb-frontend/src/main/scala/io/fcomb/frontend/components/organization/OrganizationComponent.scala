/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.frontend.components.organization

import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.PotMap
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.components.repository.{Namespace, RepositoriesComponent}
import io.fcomb.frontend.components.{BreadcrumbsComponent, FloatActionButtonComponent}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.acl.Role
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scalacss.ScalaCssReact._

object OrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         tab: OrganizationTab,
                         orgs: ModelProxy[PotMap[String, OrganizationResponse]],
                         slug: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    def onChange(tab: OrganizationTab, e: ReactEventH, el: ReactElement): Callback =
      $.props.flatMap { props =>
        val route = tab match {
          case OrganizationTab.Repositories => DashboardRoute.Organization(props.slug)
          case OrganizationTab.Groups       => DashboardRoute.OrganizationGroups(props.slug)
          case OrganizationTab.Settings     => DashboardRoute.OrganizationSettings(props.slug)
        }
        props.ctl.set(route)
      }

    def renderGroupsTab(props: Props) = {
      val component: ReactNode =
        if (props.tab === OrganizationTab.Groups)
          GroupsComponent(props.ctl, props.slug)
        else <.div()
      MuiTab(key = "groups", label = "Groups", value = OrganizationTab.Groups)(
        MuiCardText()(component))
    }

    def renderReposTab(props: Props) = {
      val component: ReactNode =
        if (props.tab === OrganizationTab.Repositories)
          RepositoriesComponent(props.ctl, Namespace.Organization(props.slug))
        else <.div()
      MuiTab(key = "repos", label = "Repositories", value = OrganizationTab.Repositories)(
        MuiCardText()(component))
    }

    def renderTabs(props: Props, isManageable: Boolean) = {
      val manageTabs =
        if (isManageable)
          Seq(renderGroupsTab(props),
              MuiTab(key = "settings", label = "Settings", value = OrganizationTab.Settings)(
                MuiCardText()(OrganizationSettingsComponent(props.ctl, props.slug))))
        else Seq.empty

      MuiCardMedia(key = "tabs")(
        MuiTabs[OrganizationTab](value = props.tab, onChange = onChange _)(renderReposTab(props),
                                                                           manageTabs))
    }

    def renderHeader(props: Props) = {
      val breadcrumbs =
        BreadcrumbsComponent(props.ctl, Seq((props.slug, DashboardRoute.Organization(props.slug))))

      <.div(^.key := "header",
            App.cardTitleBlock,
            MuiCardTitle(key = "title")(
              <.div(^.`class` := "row",
                    ^.key := "title",
                    <.div(^.`class` := "col-xs-12", breadcrumbs))))
    }

    def render(props: Props): ReactElement = {
      val organization = props.orgs().get(props.slug)
      <.section(organization.render { org =>
        val isManageable = org.role.contains(Role.Admin)
        val fab: ReactNode =
          if (isManageable)
            FloatActionButtonComponent(props.ctl,
                                       DashboardRoute.NewOrganizationRepository(props.slug),
                                       "New repository")
          else <.div()
        <.div(MuiCard(key = "repos")(renderHeader(props), renderTabs(props, isManageable)), fab)
      })
    }
  }

  private val component = ReactComponentB[Props]("Organization").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            tab: OrganizationTab,
            orgs: ModelProxy[PotMap[String, OrganizationResponse]],
            slug: String) =
    component(Props(ctl, tab, orgs, slug))
}
