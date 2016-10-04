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

package io.fcomb.frontend

sealed trait Route {
  def title: String
}

object Route {
  final case class Dashboard(route: DashboardRoute) extends Route {
    def title = route.title
  }

  final case object SignIn extends Route {
    def title = "Login"
  }

  final case object SignUp extends Route {
    def title = "Registration"
  }

  final case object SignOut extends Route {
    def title = "Sign Out"
  }
}

sealed trait DashboardRoute {
  def title: String
}

object DashboardRoute {
  final case object Root extends DashboardRoute {
    def title = "Dashboard"
  }

  final case object Repositories extends DashboardRoute {
    def title = "Repositories"
  }
  final case object NewRepository extends DashboardRoute {
    def title = s"${Repositories.title} – New"
  }
  sealed trait RepositoryRoute extends DashboardRoute {
    val slug: String

    def title = s"${Repositories.title} – $slug"
  }
  final case class Repository(slug: String)         extends RepositoryRoute
  final case class EditRepository(slug: String)     extends RepositoryRoute
  final case class RepositoryTags(slug: String)     extends RepositoryRoute
  final case class RepositorySettings(slug: String) extends RepositoryRoute

  final case object Organizations extends DashboardRoute {
    def title = "Organizations"
  }
  final case object NewOrganization extends DashboardRoute {
    def title = s"${Organizations.title} – New"
  }
  sealed trait OrganizationRoute extends DashboardRoute {
    val orgName: String

    def title = s"${Organizations.title} – $orgName"
  }
  final case class Organization(orgName: String)                    extends OrganizationRoute
  final case class OrganizationSettings(orgName: String)            extends OrganizationRoute
  final case class OrganizationGroups(orgName: String)              extends OrganizationRoute
  final case class NewOrganizationGroup(orgName: String)            extends OrganizationRoute
  final case class OrganizationGroup(orgName: String, name: String) extends OrganizationRoute
  final case class NewOrganizationRepository(orgName: String)       extends OrganizationRoute
}
