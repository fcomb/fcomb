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

package io.fcomb.rpc.acl

import io.fcomb.models.acl.{Action, MemberKind}

sealed trait PermissionMemberRequest {
  val kind: MemberKind
}

sealed trait PermissionUserRequest extends PermissionMemberRequest {
  val kind = MemberKind.User
}

final case class PermissionUserIdRequest(id: Int) extends PermissionUserRequest

final case class PermissionUsernameRequest(username: String) extends PermissionUserRequest

sealed trait PermissionGroupRequest extends PermissionMemberRequest {
  val kind = MemberKind.Group
}

final case class PermissionGroupIdRequest(id: Int) extends PermissionGroupRequest

final case class PermissionGroupNameRequest(name: String) extends PermissionGroupRequest

final case class PermissionCreateRequest(
    member: PermissionMemberRequest,
    action: Action
)

sealed trait PermissionMemberResponse {
  val id: Int
  val kind: MemberKind

  def name: String
  def isOwner: Boolean
}

final case class PermissionUserMemberResponse(
    id: Int,
    isOwner: Boolean,
    username: String,
    fullName: Option[String]
) extends PermissionMemberResponse {
  val kind = MemberKind.User

  def name = username + fullName.map(n => s"($n)").getOrElse("")
}

final case class PermissionGroupMemberResponse(
    id: Int,
    name: String
) extends PermissionMemberResponse {
  val kind = MemberKind.Group

  def isOwner = false
}

final case class PermissionResponse(
    member: PermissionMemberResponse,
    action: Action,
    createdAt: String,
    updatedAt: Option[String]
)
