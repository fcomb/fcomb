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
import io.fcomb.models.Organization
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationsRepo

trait OrganizationDirectives {
  final def organizationBySlugWithAcl(slug: Slug,
                                      userId: Int,
                                      role: Role): Directive1[Organization] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(OrganizationsRepo.findBySlugWithAcl(slug, userId, role))
        .flatMap(provideOrganization)
    }
  }

  private final def provideOrganization(orgOpt: Option[Organization]): Directive1[Organization] = {
    orgOpt match {
      case Some(org) => provide(org)
      case _         => complete(HttpResponse(StatusCodes.NotFound))
    }
  }
}

object OrganizationDirectives extends OrganizationDirectives
