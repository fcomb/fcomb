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

package io.fcomb.frontend.components.repository

import io.fcomb.models.common.{Enum, EnumItem}

sealed trait RepositoryTab extends EnumItem

object RepositoryTab extends Enum[RepositoryTab] {
  case object Info        extends RepositoryTab
  case object Tags        extends RepositoryTab
  case object Permissions extends RepositoryTab
  case object Settings    extends RepositoryTab

  val values = findValues
}
