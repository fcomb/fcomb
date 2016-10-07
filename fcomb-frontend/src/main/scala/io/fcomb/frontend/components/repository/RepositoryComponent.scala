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

package io.fcomb.frontend.components.repository

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.PotMap
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.App
import io.fcomb.models.OwnerKind
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         tab: RepositoryTab,
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         slug: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    def onChange(tab: RepositoryTab, e: ReactEventH, el: ReactElement): Callback =
      $.props.flatMap { props =>
        val route = tab match {
          case RepositoryTab.Description => DashboardRoute.Repository(props.slug)
          case RepositoryTab.Tags        => DashboardRoute.RepositoryTags(props.slug)
          case RepositoryTab.Permissions => DashboardRoute.RepositoryPermissions(props.slug)
          case RepositoryTab.Settings    => DashboardRoute.RepositorySettings(props.slug)
        }
        props.ctl.set(route)
      }

    def renderTabs(props: Props, repo: RepositoryResponse) =
      MuiTabs[RepositoryTab](value = props.tab, onChange = onChange _)(
        MuiTab(key = "description", label = "Description", value = RepositoryTab.Description)(
          RepositoryDescriptionComponent(repo)),
        MuiTab(key = "tags", label = "Tags", value = RepositoryTab.Tags)(
          RepositoryTagsComponent(repo.slug)),
        MuiTab(key = "permissions", label = "Permissions", value = RepositoryTab.Permissions)(
          RepositoryPermissionsComponent(props.ctl, repo.slug, repo.owner.kind)),
        MuiTab(key = "settings", label = "Settings", value = RepositoryTab.Settings)(
          RepositorySettingsComponent(props.ctl, repo.slug)))

    def render(props: Props): ReactElement = {
      val repository = props.repositories().get(props.slug)
      <.section(repository.renderReady { repo =>
        val route = repo.owner.kind match {
          case OwnerKind.Organization => DashboardRoute.Organization(repo.namespace)
          case OwnerKind.User =>
            if (AppCircuit.currentUser.exists(_.username == repo.namespace))
              DashboardRoute.Repositories
            else DashboardRoute.User(repo.namespace)
        }
        MuiCard()(<.div(^.key := "header",
                        App.cardTitleBlock,
                        MuiCardTitle(key = "title")(
                          <.h1(App.cardTitle, props.ctl.link(route)(repo.namespace), repo.name))),
                  MuiCardMedia(key = "tabs")(renderTabs(props, repo)))
      })
    }
  }

  private val component = ReactComponentB[Props]("Repository").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            tab: RepositoryTab,
            repositories: ModelProxy[PotMap[String, RepositoryResponse]],
            slug: String) =
    component(Props(ctl, tab, repositories, slug))
}
