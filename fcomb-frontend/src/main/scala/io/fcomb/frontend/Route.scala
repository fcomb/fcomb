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

sealed trait Route

object Route {
  final case class Dashboard(route: DashboardRoute) extends Route
  final case object SignIn                          extends Route
  final case object SignUp                          extends Route
  final case object SignOut                         extends Route
}

sealed trait DashboardRoute

object DashboardRoute {
  final case object Root extends DashboardRoute

  final case object Repositories                    extends DashboardRoute
  final case object NewRepository                   extends DashboardRoute
  final case class Repository(name: String)         extends DashboardRoute
  final case class RepositoryTags(name: String)     extends DashboardRoute
  final case class RepositorySettings(name: String) extends DashboardRoute

  final case object Organizations                                   extends DashboardRoute
  final case object NewOrganization                                 extends DashboardRoute
  final case class Organization(name: String)                       extends DashboardRoute
  final case class OrganizationSettings(name: String)               extends DashboardRoute
  final case class OrganizationGroups(orgName: String)              extends DashboardRoute
  final case class NewOrganizationGroup(orgName: String)            extends DashboardRoute
  final case class OrganizationGroup(orgName: String, name: String) extends DashboardRoute
  final case class NewOrganizationRepository(orgName: String)       extends DashboardRoute
}
