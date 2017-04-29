/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import cats.Eq

sealed trait Route {
  def title: String
}

object Route {
  final case class Dashboard(route: DashboardRoute) extends Route {
    def title = route.title
  }

  case object SignIn extends Route {
    def title = "Sign in"
  }

  case object SignUp extends Route {
    def title = "Sign up"
  }

  case object SignOut extends Route {
    def title = "Sign out"
  }

  final implicit val valueEq: Eq[Route] = Eq.fromUniversalEquals
}

sealed trait DashboardRoute {
  def title: String
}

object DashboardRoute {
  case object Root extends DashboardRoute {
    def title = "Dashboard"
  }

  sealed trait RepositoryRoute extends DashboardRoute {
    val slug: String

    def title = s"${Repositories.title} – $slug"
  }
  case object Repositories extends DashboardRoute {
    def title = "Repositories"
  }
  case object NewRepository extends DashboardRoute {
    def title = s"${Repositories.title} – New"
  }
  final case class Repository(slug: String)            extends RepositoryRoute
  final case class EditRepository(slug: String)        extends RepositoryRoute
  final case class RepositoryTags(slug: String)        extends RepositoryRoute
  final case class RepositorySettings(slug: String)    extends RepositoryRoute
  final case class RepositoryPermissions(slug: String) extends RepositoryRoute

  sealed trait OrganizationRoute extends DashboardRoute {
    val slug: String

    def title = s"${Organizations.title} – $slug"
  }
  case object Organizations extends DashboardRoute {
    def title = "Organizations"
  }
  case object NewOrganization extends DashboardRoute {
    def title = s"${Organizations.title} – New"
  }
  final case class Organization(slug: String)                         extends OrganizationRoute
  final case class OrganizationSettings(slug: String)                 extends OrganizationRoute
  final case class OrganizationGroups(slug: String)                   extends OrganizationRoute
  final case class OrganizationGroup(slug: String, group: String)     extends OrganizationRoute
  final case class EditOrganizationGroup(slug: String, group: String) extends OrganizationRoute
  final case class NewOrganizationRepository(slug: String)            extends OrganizationRoute

  sealed trait UserRoute extends DashboardRoute {
    val slug: String

    def title = s"Users – $slug"
  }
  final case class User(slug: String) extends UserRoute

  sealed trait SettingsRoute extends DashboardRoute
  case object Users extends SettingsRoute {
    override def title = "Users"
  }
  case object NewUser extends SettingsRoute {
    override def title = Users.title
  }
  final case class EditUser(slug: String) extends SettingsRoute {
    override def title = Users.title
  }
  // case object GarbageCollectorSettings  extends SettingsRoute
  // case object SecuritySettings          extends SettingsRoute
  // case object TlsSettings               extends SettingsRoute

  case object Global extends SettingsRoute {
    override def title = "Global"
  }

  final implicit val valueEq: Eq[DashboardRoute] = Eq.fromUniversalEquals
}
