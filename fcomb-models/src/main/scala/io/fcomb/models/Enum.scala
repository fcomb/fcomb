package io.fcomb.models

import cats.Eq

trait EnumItem {
  val value: String

  override def toString = value
}

trait Enum[T <: EnumItem] {
  def fromString(value: String): T

  implicit val valueEq: Eq[T] = Eq.fromUniversalEquals
}
