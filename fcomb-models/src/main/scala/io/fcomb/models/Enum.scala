package io.fcomb.models

import cats.Eq

trait EnumItem extends enumeratum.EnumEntry with enumeratum.EnumEntry.Snakecase {
  def value = entryName
}

trait Enum[T <: EnumItem] extends enumeratum.Enum[T] {
  implicit val valueEq: Eq[T] = Eq.fromUniversalEquals
}
