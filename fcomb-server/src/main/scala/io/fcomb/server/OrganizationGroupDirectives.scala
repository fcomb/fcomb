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

package io.fcomb.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.models.OrganizationGroup
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationGroupsRepo
import io.fcomb.server.OrganizationDirectives._

object OrganizationGroupDirectives {
  def groupBySlugWithAcl(slug: Slug, groupSlug: Slug, userId: Int): Directive1[OrganizationGroup] =
    organizationBySlugWithAcl(slug, userId, Role.Admin).flatMap { org =>
      extractExecutionContext.flatMap { implicit ec =>
        onSuccess(OrganizationGroupsRepo.findBySlug(org.getId(), groupSlug)).flatMap(provideGroup)
      }
    }

  final def provideGroup(groupOpt: Option[OrganizationGroup]): Directive1[OrganizationGroup] =
    groupOpt match {
      case Some(group) => provide(group)
      case _           => complete(HttpResponse(StatusCodes.NotFound))
    }
}
