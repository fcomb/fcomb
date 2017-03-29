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

package io.fcomb.models.common

import cats.Eq

trait EnumItem extends enumeratum.EnumEntry with enumeratum.EnumEntry.Snakecase {
  def value = entryName
}

trait Enum[T <: EnumItem] extends enumeratum.Enum[T] {
  final implicit val valueEq: Eq[T] = Eq.fromUniversalEquals

  final lazy val entryNames = values.map(_.entryName).mkString(", ")
}
