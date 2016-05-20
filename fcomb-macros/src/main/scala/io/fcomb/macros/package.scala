package io.fcomb

package object macros {
  import scala.language.experimental.macros

  implicit def materializeMappable[T]: Mappable[T] = macro Mappable.materializeMappableImpl[T]

  // def materialize[T: Mappable](map: Map[String, Any]): T =
  //   implicitly[Mappable[T]].fromMap(map)

  def materialize[T: Mappable](map: T): Map[String, Any] =
    implicitly[Mappable[T]].toMap(map)
}
