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

package io.fcomb.frontend.api

import io.fcomb.models.acl.MemberKind
import io.fcomb.models.docker.distribution.ImageVisibilityKind

object Resource {
  val prefix = "/api/v1"

  val sessions = prefix + "/sessions"

  val repositories = prefix + "/repositories"
  def repository(imageName: String) = s"$repositories/$imageName"

  def repositoryTags(imageName: String)             = repository(imageName) + "/tags"
  def repositoryTag(imageName: String, tag: String) = repositoryTags(imageName) + s"/$tag"

  def repositoryPermissions(imageName: String) = repository(imageName) + "/permissions"
  def repositoryPermission(imageName: String, kind: MemberKind, slug: String) =
    repositoryPermissions(imageName) + s"/${kind.entryName}/$slug"

  def repositoryVisibility(imageName: String, kind: ImageVisibilityKind) =
    repository(imageName) + s"/visibility/${kind.entryName}"

  val organizations = prefix + "/organizations"
  def organization(orgName: String) = s"$organizations/$orgName"

  def organizationGroups(orgName: String) = organization(orgName) + "/groups"

  val users  = prefix + "/users"
  val signUp = users + "/sign_up"

  val user              = prefix + "/user"
  val userRepositories  = user + "/repositories"
  val userOrganizations = user + "/organizations"
}
