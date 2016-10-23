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
import io.fcomb.frontend.components.{FloatActionButtonComponent, LayoutComponent}
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
    // def render(props: Props) = {
    //   <.section(
    //     <.h2(s"Organization ${props.name}"),
    //     <.ul(
    //       <.li(props.ctl.link(DashboardRoute.OrganizationGroups(props.name))("Groups")),
    //       <.li(props.ctl.link(DashboardRoute.OrganizationSettings(props.name))("Settings"))
    //     ),
    //     <.div(<.h1("Repositories"),
    //         MuiFloatingActionButton(onTouchTap = setRoute(DashboardRoute.NewOrganizationOrganization(props.name)) _)(
    //           ContentAdd()()),
    //       <.section(
    //         RepositoriesComponent.apply(props.ctl, Namespace.Organization(props.name))))
    //   )
    // }

    def onChange(tab: OrganizationTab, e: ReactEventH, el: ReactElement): Callback =
      $.props.flatMap { props =>
        val route = tab match {
          case OrganizationTab.Repositories => DashboardRoute.Organization(props.slug)
          case OrganizationTab.Groups       => DashboardRoute.OrganizationGroups(props.slug)
          case OrganizationTab.Settings     => DashboardRoute.OrganizationSettings(props.slug)
        }
        props.ctl.set(route)
      }

    // def renderGroupsTab(props: Props, repo: OrganizationResponse) = {
    //   val component: ReactNode =
    //     if (props.tab === OrganizationTab.Permissions)
    //       OrganizationPermissionsComponent(props.ctl, repo.slug, repo.owner.kind)
    //     else <.div()
    //   MuiTab(key = "permissions", label = "Permissions", value = OrganizationTab.Permissions)(
    //     MuiCardText()(component))
    // }

    def renderTabs(props: Props, isManageable: Boolean) = {
      val manageTabs =
        /*if (isManageable)
          Seq(renderPermissionsTab(props, repo),
              MuiTab(key = "settings", label = "Settings", value = OrganizationTab.Settings)(
                MuiCardText()(OrganizationSettingsComponent(props.ctl, repo.slug))))
        else*/ Seq.empty

      MuiCardMedia(key = "tabs")(
        MuiTabs[OrganizationTab](value = props.tab, onChange = onChange _)(
          MuiTab(key = "repos", label = "Repositories", value = OrganizationTab.Repositories)(
            MuiCardText()(RepositoriesComponent(props.ctl, Namespace.Organization(props.slug)))),
          manageTabs))
    }

    def breadcrumbLink(ctl: RouterCtl[DashboardRoute], target: DashboardRoute, text: String) =
      <.a(LayoutComponent.linkAsTextStyle,
          ^.href := ctl.urlFor(target).value,
          ctl.setOnLinkClick(target))(text)

    def renderHeader(props: Props) = {
      val breadcrumbs = <.h1(
        App.cardTitle,
        breadcrumbLink(props.ctl, DashboardRoute.Organization(props.slug), props.slug))

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
        <.div(MuiCard(key = "repos")(renderHeader(props), renderTabs(props, isManageable)),
              FloatActionButtonComponent(props.ctl,
                                         DashboardRoute.NewOrganizationRepository(props.slug),
                                         "New repository"))
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
