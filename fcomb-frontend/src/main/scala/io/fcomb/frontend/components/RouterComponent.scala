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

package io.fcomb.frontend.components

import io.fcomb.frontend.components.auth._
import io.fcomb.frontend.components.dashboard._
import io.fcomb.frontend.components.organization._
import io.fcomb.frontend.components.repository._
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react._

object RouterComponent {
  val baseUrl = BaseUrl.fromWindowOrigin / "#"

  val dashboardRouterConfig = RouterConfigDsl[DashboardRoute].buildRule { dsl =>
    import dsl._

    val repositoryNameFormat = """[\w\-\.]+/[\w\-\.]+"""
    val repositoryNamePath   = "repositories" / string(repositoryNameFormat)

    val slugFormat           = """[\w\-\.]+"""
    val organizationNamePath = "organizations" / string(slugFormat)

    // format: OFF
    trimSlashes |
    staticRoute(root, DashboardRoute.Root) ~> redirectToPage(DashboardRoute.Repositories)(
      Redirect.Replace) |
    staticRoute("repositories", DashboardRoute.Repositories) ~> renderR(
      ctl => DashboardRepositoriesComponent.apply(ctl)) |
    staticRoute("repositories" / "new", DashboardRoute.NewRepository) ~> renderR(
      ctl => UserNewRepositoryComponent.apply(ctl)) |
    dynamicRouteCT(repositoryNamePath.caseClass[DashboardRoute.Repository]) ~> dynRenderR((r, ctl) => RepositoryComponent.apply(ctl, r.name)) |
    dynamicRouteCT((repositoryNamePath / "tags").caseClass[DashboardRoute.RepositoryTags]) ~> dynRenderR((r, ctl) => TagsComponent.apply(ctl, r.name)) |
    dynamicRouteCT((repositoryNamePath / "settings").caseClass[DashboardRoute.RepositorySettings]) ~> dynRenderR((r, ctl) => RepositorySettingsComponent.apply(ctl, r.name)) |
    staticRoute("organizations", DashboardRoute.Organizations) ~> renderR(
      ctl => OrganizationsComponent.apply(ctl)) |
    staticRoute("organizations" / "new", DashboardRoute.NewOrganization) ~> renderR(
      ctl => NewOrganizationComponent.apply(ctl)) |
    dynamicRouteCT((organizationNamePath / "settings").caseClass[DashboardRoute.OrganizationSettings]) ~> dynRenderR(
      (o, ctl) => OrganizationSettingsComponent.apply(ctl, o.orgName)) |
    dynamicRouteCT((organizationNamePath / "repositories" / "new").caseClass[DashboardRoute.NewOrganizationRepository]) ~> dynRenderR((o, ctl) => NewOrganizationRepositoryComponent.apply(ctl, o.orgName)) |
    dynamicRouteCT(organizationNamePath.caseClass[DashboardRoute.Organization]) ~> dynRenderR((o, ctl) => OrganizationComponent.apply(ctl, o.orgName)) |
    dynamicRouteCT((organizationNamePath / "groups" / "new").caseClass[DashboardRoute.NewOrganizationGroup]) ~> dynRenderR((o, ctl) => NewGroupComponent.apply(ctl, o.orgName)) |
    dynamicRouteCT((organizationNamePath / "groups").caseClass[DashboardRoute.OrganizationGroups]) ~> dynRenderR((o, ctl) => GroupsComponent.apply(ctl, o.orgName)) |
    dynamicRouteCT((organizationNamePath / "groups" / string(slugFormat)).caseClass[DashboardRoute.OrganizationGroup]) ~> dynRenderR((og, ctl) => GroupComponent.apply(ctl, og.orgName, og.name))
    // format: ON
  }

  val routerConfig: RouterConfig[Route] = RouterConfigDsl[Route].buildConfig { dsl =>
    import dsl._

    val dashboardRoutes =
      dashboardRouterConfig.prefixPath_/("dashboard").pmap[Route](Route.Dashboard) {
        case Route.Dashboard(r) => r
      }

    val routes =
      // format: OFF
      trimSlashes |
      dashboardRoutes |
      staticRoute("sign_in", Route.SignIn) ~> renderR(AuthComponent.apply(_, AuthTab.SignIn)) |
      staticRoute("sign_up", Route.SignUp) ~> renderR(AuthComponent.apply(_, AuthTab.SignUp)) |
      staticRoute("sign_out", Route.SignOut) ~> renderR(SignOutComponent.apply(_))
      // format: ON

    routes
      .notFound(redirectToPage(Route.Dashboard(DashboardRoute.Root))(Redirect.Replace))
      .renderWith(LayoutComponent.apply)
  }

  private val component = Router(baseUrl, routerConfig.logToConsole)

  def apply(): ReactComponentU[Unit, Resolution[Route], Any, TopNode] =
    component.apply()
}
