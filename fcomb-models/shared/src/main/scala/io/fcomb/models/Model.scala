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

package io.fcomb.models

import java.util.UUID

sealed trait ModelWithPk {
  type PkType
  type IdType = Option[PkType]

  val id: IdType

  def getId() =
    id.getOrElse(
      throw new IllegalArgumentException("Column 'id' cannot be empty")
    )
}

trait ModelWithIntPk extends ModelWithPk {
  type PkType = Int
}

sealed trait ModelWithAutoPk { this: ModelWithPk =>
  def withId(id: PkType): ModelWithAutoPk
}

trait ModelWithAutoIntPk extends ModelWithIntPk with ModelWithAutoPk

trait ModelWithUuidPk extends ModelWithPk {
  type PkType = UUID
}

object ModelWithPk {
  final implicit val modelWithPkLens: IdLens[ModelWithPk] =
    new IdLens[ModelWithPk] { def getId(item) = item.getId().toString() }
}
