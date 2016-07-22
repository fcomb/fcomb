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

package io.fcomb.models.acl

import cats.syntax.eq._
import io.fcomb.models.common.{Enum, EnumItem}

sealed trait Role extends EnumItem {
  def has(action: Role): Boolean = {
    action match {
      case Role.Member  => true
      case Role.Creator => this === Role.Creator || this === Role.Admin
      case Role.Admin   => this === Role.Admin
    }
  }
}

object Role extends Enum[Role] {
  final case object Admin   extends Role
  final case object Creator extends Role
  final case object Member  extends Role

  val values = findValues
}
