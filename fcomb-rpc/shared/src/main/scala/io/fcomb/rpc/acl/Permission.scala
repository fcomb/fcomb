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

final case class PermissionUserCreateRequest(
    member: PermissionUserRequest,
    action: Action
)

sealed trait PermissionMemberResponse {
  val id: Int
  val kind: MemberKind
  val name: String
}

final case class PermissionUserMemberResponse(
    id: Int,
    kind: MemberKind,
    isOwner: Boolean,
    name: String,
    fullName: Option[String]
) extends PermissionMemberResponse

final case class PermissionResponse(
    member: PermissionUserMemberResponse,
    action: Action,
    createdAt: String,
    updatedAt: Option[String]
)