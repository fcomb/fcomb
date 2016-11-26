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
import io.fcomb.frontend.components.settings._
import io.fcomb.frontend.dispatcher.{AppCircuit => AC}
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._

object RouterComponent {
  val baseUrl = BaseUrl.fromWindowOrigin / "#"

  val dashboardRouterConfig = RouterConfigDsl[DashboardRoute].buildRule { dsl =>
    import dsl._

    val repositorySlugFormat = """[\w\-\.]+/[\w\-\.]+"""
    val repositorySlugPath   = "repositories" / string(repositorySlugFormat)

    val slugFormat           = """[\w\-\.]+"""
    val organizationSlugPath = "organizations" / string(slugFormat)

    val userSlugPath = "settings" / "users" / string(slugFormat)

    def repo(ctl: RouterCtl[DashboardRoute], tab: RepositoryTab, slug: String) =
      AC.repos(RepositoryComponent(ctl, tab, _, slug))

    def org(ctl: RouterCtl[DashboardRoute], tab: OrganizationTab, slug: String) =
      AC.orgs(OrganizationComponent(ctl, tab, _, slug))

    // format: OFF
    trimSlashes |
    staticRoute(root, DashboardRoute.Root) ~> redirectToPage(DashboardRoute.Repositories)(
      Redirect.Replace) |
    // repos
    staticRoute("repositories", DashboardRoute.Repositories) ~>
      renderR(ctl => DashboardRepositoriesComponent(ctl)) |
    staticRoute("repositories" / "new", DashboardRoute.NewRepository) ~>
      renderR(ctl => UserNewRepositoryComponent(ctl)) |
    dynamicRouteCT(repositorySlugPath.caseClass[DashboardRoute.Repository]) ~>
      dynRenderR((r, ctl) => repo(ctl, RepositoryTab.Info, r.slug)) |
    dynamicRouteCT((repositorySlugPath / "edit").caseClass[DashboardRoute.EditRepository]) ~>
      dynRenderR((r, ctl) => AC.repos(EditRepositoryComponent(ctl, _, r.slug))) |
    dynamicRouteCT((repositorySlugPath / "tags").caseClass[DashboardRoute.RepositoryTags]) ~>
      dynRenderR((r, ctl) => repo(ctl, RepositoryTab.Tags, r.slug)) |
    dynamicRouteCT((repositorySlugPath / "settings").caseClass[DashboardRoute.RepositorySettings]) ~>
      dynRenderR((r, ctl) => repo(ctl, RepositoryTab.Settings, r.slug)) |
    dynamicRouteCT((repositorySlugPath / "permissions").caseClass[DashboardRoute.RepositoryPermissions]) ~>
      dynRenderR((r, ctl) => repo(ctl, RepositoryTab.Permissions, r.slug)) |
    // orgs
    staticRoute("organizations", DashboardRoute.Organizations) ~>
      renderR(ctl => OrganizationsComponent(ctl)) |
    staticRoute("organizations" / "new", DashboardRoute.NewOrganization) ~>
      renderR(ctl => NewOrganizationComponent(ctl)) |
    dynamicRouteCT((organizationSlugPath / "settings").caseClass[DashboardRoute.OrganizationSettings]) ~>
      dynRenderR((o, ctl) => org(ctl, OrganizationTab.Settings, o.slug)) |
    dynamicRouteCT((organizationSlugPath / "repositories" / "new").caseClass[DashboardRoute.NewOrganizationRepository]) ~>
      dynRenderR((o, ctl) => NewOrganizationRepositoryComponent(ctl, o.slug)) |
    dynamicRouteCT(organizationSlugPath.caseClass[DashboardRoute.Organization]) ~>
      dynRenderR((o, ctl) => org(ctl, OrganizationTab.Repositories, o.slug)) |
    dynamicRouteCT((organizationSlugPath / "groups").caseClass[DashboardRoute.OrganizationGroups]) ~>
      dynRenderR((o, ctl) => org(ctl, OrganizationTab.Groups, o.slug)) |
    dynamicRouteCT((organizationSlugPath / "groups" / string(slugFormat)).caseClass[DashboardRoute.OrganizationGroup]) ~>
      dynRenderR((og, ctl) => GroupComponent(ctl, og.slug, og.group)) |
    dynamicRouteCT((organizationSlugPath / "groups" / string(slugFormat) / "edit").caseClass[DashboardRoute.EditOrganizationGroup]) ~>
      dynRenderR((og, ctl) => EditGroupComponent(ctl, og.slug, og.group)) |
    // settings
    staticRoute("settings" / "users", DashboardRoute.Users) ~>
      renderR(ctl => UsersComponent(ctl)) |
    staticRoute("settings" / "users" / "new", DashboardRoute.NewUser) ~>
      renderR(ctl => NewUserComponent(ctl)) |
    dynamicRouteCT((userSlugPath / "edit").caseClass[DashboardRoute.EditUser]) ~>
      dynRenderR((u, ctl) => EditUserComponent(ctl, u.slug))
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
      staticRoute("sign_in", Route.SignIn) ~> renderR(SignInComponent(_)) |
      staticRoute("sign_up", Route.SignUp) ~> renderR(SignUpComponent(_)) |
      staticRoute("sign_out", Route.SignOut) ~> renderR(SignOutComponent(_))
      // format: ON

    routes
      .notFound(redirectToPage(Route.Dashboard(DashboardRoute.Root))(Redirect.Replace))
      .renderWith(LayoutComponent.apply)
  }

  private val component = Router(baseUrl, routerConfig.logToConsole)

  def apply(): ReactComponentU[Unit, Resolution[Route], Any, TopNode] =
    component()
}
