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

package io.fcomb.persist

import io.fcomb.PostgresProfile._
import io.fcomb.models.acl.{Action, MemberKind, Role}
import io.fcomb.models.docker.distribution.{BlobFileState, ImageBlobState, ImageVisibilityKind}
import io.fcomb.models.{ApplicationState, OwnerKind}

// TODO: keep only common mappings and move specific into own repo table
object EnumsMapping {
  implicit val applicationStateColumnType =
    createJdbcMapping("application_state", ApplicationState)

  implicit val ownerKindColumnType = createJdbcMapping("owner_kind", OwnerKind)

  implicit val aclActionColumnType = createJdbcMapping("acl_action", Action)

  implicit val aclRoleColumnType = createJdbcMapping("acl_role", Role)

  implicit val aclMemberKindColumnType = createJdbcMapping("acl_member_kind", MemberKind)

  implicit val distributionImageBlobStateColumnType =
    createJdbcMapping("dd_image_blob_state", ImageBlobState)

  implicit val distributionImageVisibilityKindColumnType =
    createJdbcMapping("dd_image_visibility_kind", ImageVisibilityKind)

  implicit val blobFileStateColumnType = createJdbcMapping("dd_blob_file_state", BlobFileState)
}
