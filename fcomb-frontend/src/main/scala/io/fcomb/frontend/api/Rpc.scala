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

import cats.syntax.either._
import diode.data._
import io.circe.scalajs.decodeJs
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import io.fcomb.frontend.components.repository.Namespace
import io.fcomb.frontend.dispatcher.actions.LogOut
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.utils.PaginationUtils
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.{Action, MemberKind, Role}
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.models.errors.{Error, Errors, ErrorsException}
import io.fcomb.models.{Owner, OwnerKind, PaginationData, Session, SortOrder}
import io.fcomb.rpc.acl._
import io.fcomb.rpc.docker.distribution._
import io.fcomb.rpc._
import org.scalajs.dom.ext.{Ajax, AjaxException}
import org.scalajs.dom.window
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.{JSON, URIUtils}

sealed trait RpcMethod

object RpcMethod {
  final case object GET    extends RpcMethod
  final case object HEAD   extends RpcMethod
  final case object POST   extends RpcMethod
  final case object PUT    extends RpcMethod
  final case object DELETE extends RpcMethod
}

object Rpc {
  def getRepository(slug: String)(implicit ec: ExecutionContext) =
    call[RepositoryResponse](RpcMethod.GET, Resource.repository(slug)).map(toPot)

  def createRepository(owner: Owner,
                       name: String,
                       visibilityKind: ImageVisibilityKind,
                       description: String)(implicit ec: ExecutionContext) = {
    val url = owner match {
      case Owner(_, OwnerKind.User) => Resource.userSelfRepositories
      case Owner(id, OwnerKind.Organization) =>
        Resource.organizationRepositories(id.toString)
    }
    val req = ImageCreateRequest(name, visibilityKind, Some(description))
    callWith[ImageCreateRequest, RepositoryResponse](RpcMethod.POST, url, req)
  }

  def updateRepository(slug: String, visibilityKind: ImageVisibilityKind, description: String)(
      implicit ec: ExecutionContext) = {
    val req = ImageUpdateRequest(visibilityKind, description)
    callWith[ImageUpdateRequest, RepositoryResponse](RpcMethod.PUT, Resource.repository(slug), req)
  }

  private def getRepositoriesByUrl(url: String,
                                   sortColumn: String,
                                   sortOrder: SortOrder,
                                   page: Int,
                                   limit: Int)(implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[RepositoryResponse]](RpcMethod.GET, url, queryParams)
  }

  def getNamespaceRepositories(namespace: Namespace,
                               sortColumn: String,
                               sortOrder: SortOrder,
                               page: Int,
                               limit: Int)(implicit ec: ExecutionContext) = {
    val url = namespace match {
      case Namespace.All => Resource.userSelfRepositoriesAvailable
      case ns @ Namespace.User(slug, _) =>
        if (ns.isCurrentUser) Resource.userSelfRepositories
        else Resource.userRepositories(slug)
      case Namespace.Organization(slug, _) => Resource.organizationRepositories(slug)
    }
    getRepositoriesByUrl(url, sortColumn, sortOrder, page, limit)
  }

  def getRepositoryTags(slug: String,
                        sortColumn: String,
                        sortOrder: SortOrder,
                        page: Int,
                        limit: Int)(implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[RepositoryTagResponse]](RpcMethod.GET,
                                                Resource.repositoryTags(slug),
                                                queryParams)
  }

  def getRepositoryPermissions(slug: String,
                               sortColumn: String,
                               sortOrder: SortOrder,
                               page: Int,
                               limit: Int)(implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[PermissionResponse]](RpcMethod.GET,
                                             Resource.repositoryPermissions(slug),
                                             queryParams)
  }

  def getRepositoryPermissionsMembers(slug: String, q: String)(implicit ec: ExecutionContext) =
    call[DataResponse[PermissionMemberResponse]](
      RpcMethod.GET,
      Resource.repositoryPermissionsSuggestionsMembers(slug),
      Map("q" -> q))

  def upsertPermission(slug: String, name: String, kind: MemberKind, action: Action)(
      implicit ec: ExecutionContext) = {
    val member = kind match {
      case MemberKind.User  => PermissionUsernameRequest(name)
      case MemberKind.Group => PermissionGroupNameRequest(name)
    }
    val req = PermissionCreateRequest(member, action)
    callWith[PermissionCreateRequest, PermissionResponse](RpcMethod.PUT,
                                                          Resource.repositoryPermissions(slug),
                                                          req)
  }

  def deletRepositoryPermission(slug: String, name: String, kind: MemberKind)(
      implicit ec: ExecutionContext) = {
    val url = Resource.repositoryPermission(slug, kind, name)
    call[Unit](RpcMethod.DELETE, url)
  }

  def deleteRepository(slug: String)(implicit ec: ExecutionContext) =
    call[Unit](RpcMethod.DELETE, Resource.repository(slug))

  private def toQueryParams(sortColumn: String,
                            sortOrder: SortOrder,
                            page: Int,
                            limit: Int): Map[String, String] =
    PaginationUtils.getParams(page, limit) ++ SortOrder.toQueryParams(Seq((sortColumn, sortOrder)))

  def createOrganization(name: String)(implicit ec: ExecutionContext) = {
    val req = OrganizationCreateRequest(name)
    callWith[OrganizationCreateRequest, OrganizationResponse](RpcMethod.POST,
                                                              Resource.organizations,
                                                              req)
  }

  def getOrganization(slug: String)(implicit ec: ExecutionContext) =
    call[OrganizationResponse](RpcMethod.GET, Resource.organization(slug)).map(toPot)

  def getOrganizations(sortColumn: String, sortOrder: SortOrder, page: Int, limit: Int)(
      implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[OrganizationResponse]](RpcMethod.GET,
                                               Resource.userSelfOrganizations,
                                               queryParams)
  }

  def getUserSelfOrganizations(canCreateRoleOnly: Boolean, limit: Int)(
      implicit ec: ExecutionContext) = {
    val role =
      if (canCreateRoleOnly) Seq(Role.Admin, Role.Creator).map(_.entryName).mkString(",")
      else ""
    val params = Map(
      "role"  -> role,
      "limit" -> limit.toString()
    ).filter(_._2.nonEmpty)
    call[PaginationData[OrganizationResponse]](RpcMethod.GET,
                                               Resource.userSelfOrganizations,
                                               params)
  }

  def deleteOrganization(slug: String)(implicit ec: ExecutionContext) =
    call[Unit](RpcMethod.DELETE, Resource.organization(slug))

  def getOrgaizationGroups(slug: String,
                           sortColumn: String,
                           sortOrder: SortOrder,
                           page: Int,
                           limit: Int)(implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[OrganizationGroupResponse]](RpcMethod.GET,
                                                    Resource.organizationGroups(slug),
                                                    queryParams)
  }

  def createOrganizationGroup(slug: String, group: String, role: Role)(
      implicit ec: ExecutionContext) = {
    val req = OrganizationGroupRequest(group, role)
    callWith[OrganizationGroupRequest, OrganizationGroupResponse](
      RpcMethod.POST,
      Resource.organizationGroups(slug),
      req)
  }

  def getOrgaizationGroup(slug: String, group: String)(implicit ec: ExecutionContext) =
    call[OrganizationGroupResponse](RpcMethod.GET, Resource.organizationGroup(slug, group))

  def updateOrganizationGroup(slug: String, group: String, name: String, role: Role)(
      implicit ec: ExecutionContext) = {
    val req = OrganizationGroupRequest(name, role)
    callWith[OrganizationGroupRequest, OrganizationGroupResponse](
      RpcMethod.PUT,
      Resource.organizationGroup(slug, group),
      req)
  }

  def deleteOrganizationGroup(slug: String, group: String)(implicit ec: ExecutionContext) =
    call[Unit](RpcMethod.DELETE, Resource.organizationGroup(slug, group))

  def getOrgaizationGroupMembers(slug: String,
                                 group: String,
                                 sortColumn: String,
                                 sortOrder: SortOrder,
                                 page: Int,
                                 limit: Int)(implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[UserResponse]](RpcMethod.GET,
                                       Resource.organizationGroupMembers(slug, group),
                                       queryParams)
  }

  def upsertOrganizationGroupMember(slug: String, group: String, username: String)(
      implicit ec: ExecutionContext) =
    callWith[MemberUsernameRequest, Unit](RpcMethod.PUT,
                                          Resource.organizationGroupMembers(slug, group),
                                          MemberUsernameRequest(username))

  def deleteOrganizationGroupMember(slug: String, group: String, username: String)(
      implicit ec: ExecutionContext) =
    call[Unit](RpcMethod.DELETE, Resource.organizationGroupMember(slug, group, username))

  def getOrganizationGroupMembers(slug: String, group: String, q: String)(
      implicit ec: ExecutionContext) =
    call[DataResponse[UserResponse]](RpcMethod.GET,
                                     Resource.organizationGroupSuggestionsMembers(slug, group),
                                     Map("q" -> q))

  def getUsers(sortColumn: String, sortOrder: SortOrder, page: Int, limit: Int)(
      implicit ec: ExecutionContext) = {
    val queryParams = toQueryParams(sortColumn, sortOrder, page, limit)
    call[PaginationData[UserProfileResponse]](RpcMethod.GET, Resource.users, queryParams)
  }

  def signUp(email: String, password: String, username: String, fullName: Option[String])(
      implicit ec: ExecutionContext) = {
    val req = UserSignUpRequest(
      email = email,
      password = password,
      username = username,
      fullName = fullName
    )
    callWith[UserSignUpRequest, Unit](RpcMethod.POST, Resource.signUp, req)
  }

  def signIn(email: String, password: String)(implicit ec: ExecutionContext) = {
    val req = SessionCreateRequest(
      email = email.trim(),
      password = password.trim()
    )
    callWith[SessionCreateRequest, Session](RpcMethod.POST, Resource.sessions, req)
  }

  private def callWith[T: Encoder, U: Decoder](
      method: RpcMethod,
      url: String,
      req: T,
      queryParams: Map[String, String] = Map.empty,
      headers: Map[String, String] = Map.empty,
      timeout: Int = 0)(implicit ec: ExecutionContext): Future[Either[Seq[Error], U]] = {
    val hm        = if (headers.isEmpty) defaultHeaders else headers ++ defaultHeaders
    val reqBody   = req.asJson.noSpaces
    val urlParams = queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    val targetUrl = URIUtils.encodeURI(s"$url?$urlParams")
    Ajax
      .apply(method.toString, targetUrl, reqBody, timeout, hm, withCredentials = false, "")
      .map { res =>
        if (res.status == 401) unauthorized()
        else decode[U](res.responseText)
      }
      .recover {
        case AjaxException(res) =>
          if (res.status == 401) unauthorized()
          else decode[Errors](res.responseText).flatMap(res => Left(res.errors))
        case e =>
          window.console.error(s"${e.toString}: ${e.getMessage}")
          Left(Seq(Errors.unknown))
      }
  }

  private def toPot[U](xor: Either[Seq[Error], U]): Pot[U] =
    xor match {
      case Right(res) => Ready(res)
      case Left(seq)  => Failed(ErrorsException(seq))
    }

  private def decode[U](responseText: String)(implicit dec: Decoder[U]): Either[Seq[Error], U] = {
    val body = if (responseText.nonEmpty) responseText else "null"
    val json = JSON.parse(body)
    decodeJs[U](json).leftMap(_ => Seq(Errors.deserialization()))
  }

  private def call[U](method: RpcMethod,
                      url: String,
                      queryParams: Map[String, String] = Map.empty,
                      headers: Map[String, String] = Map.empty,
                      timeout: Int = 0)(implicit ec: ExecutionContext,
                                        decoder: Decoder[U]): Future[Either[Seq[Error], U]] =
    callWith[Unit, U](method, url, (), queryParams, headers, timeout)

  private def unauthorized[U](): Either[Seq[Error], U] = {
    AppCircuit.dispatch(LogOut)
    Left(Seq(Errors.unauthorized))
  }

  private val contentTypeHeader = Map("Content-Type" -> "application/json")

  private def defaultHeaders: Map[String, String] =
    AppCircuit.session match {
      case Some(sessionToken) =>
        contentTypeHeader + (("Authorization", s"Bearer $sessionToken"))
      case None => contentTypeHeader
    }
}
