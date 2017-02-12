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

package io.fcomb.frontend.api

import io.fcomb.models.acl.MemberKind
import io.fcomb.models.docker.distribution.ImageVisibilityKind

object Resource {
  val prefix = "/v1"

  val sessions = s"$prefix/sessions"

  val repositories                                  = s"$prefix/repositories"
  def repository(imageName: String)                 = s"$repositories/$imageName"
  def repositoryTags(imageName: String)             = s"${repository(imageName)}/tags"
  def repositoryTag(imageName: String, tag: String) = s"${repositoryTags(imageName)}/$tag"
  def repositoryPermissions(imageName: String)      = s"${repository(imageName)}/permissions"
  def repositoryPermissionsSuggestionsMembers(imageName: String) =
    s"${repositoryPermissions(imageName)}/suggestions/members"
  def repositoryPermission(imageName: String, kind: MemberKind, slug: String) =
    s"${repositoryPermissions(imageName)}/${kind.entryName}/$slug"
  def repositoryVisibility(imageName: String, kind: ImageVisibilityKind) =
    s"${repository(imageName)}/visibility/${kind.entryName}"

  val organizations                                     = s"$prefix/organizations"
  def organization(orgName: String)                     = s"$organizations/$orgName"
  def organizationGroups(orgName: String)               = s"${organization(orgName)}/groups"
  def organizationGroup(orgName: String, group: String) = s"${organizationGroups(orgName)}/$group"
  def organizationGroupMembers(orgName: String, group: String) =
    s"${organizationGroup(orgName, group)}/members"
  def organizationGroupMember(orgName: String, group: String, slug: String) =
    s"${organizationGroupMembers(orgName, group)}/$slug"
  def organizationGroupSuggestionsMembers(orgName: String, group: String) =
    s"${organizationGroup(orgName, group)}/suggestions/members"
  def organizationRepositories(orgName: String) = s"${organization(orgName)}/repositories"

  val users  = s"$prefix/users"
  val signUp = s"$users/sign_up"

  val userSelf                      = s"$prefix/user"
  val userSelfRepositories          = s"$userSelf/repositories"
  val userSelfRepositoriesAvailable = s"$userSelf/repositories/available"
  val userSelfOrganizations         = s"$userSelf/organizations"

  def user(slug: String)             = s"$users/$slug"
  def userRepositories(slug: String) = s"${user(slug)}/repositories"
}
