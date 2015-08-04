package io.fcomb.proxy

import scala.annotation.tailrec
import scala.collection.generic.{ CanBuildFrom, ImmutableMapFactory }
import scala.collection.immutable

sealed trait RouteKind

case object StaticRoute extends RouteKind // /uri

case class WildcardRoute( // /*param
  name: String
) extends RouteKind

case class ParamaterRoute( // /:param
  name: String
) extends RouteKind

sealed trait RouteTrie[+T] extends Traversable[(String, T)] {
  def nearest(key: String): RouteTrie[T]

  def get(key: String): Option[T]

  def apply(key: String) = get(key).get

  def `+`[T1 >: T](kv: (String, T1)): RouteTrie[T1]

  def updated[T1 >: T](key: String, value: T1) =
    this + (key, value)
}

object RouteTrie {
  def empty[T]: RouteTrie[T] = EmptyRouteTrie

  def apply[T](kvs: (String, T)*): RouteTrie[T] = {
    kvs.foldLeft[RouteTrie[T]](empty) {
      case (t, (k, v)) => t + (k, v)
    }
  }
}

object EmptyRouteTrie extends RouteTrie[Nothing] with Serializable {
  def get(key: String) = None

  def `+`[T](kv: (String, T)) =
    NonEmptyRouteTrie(kv._1, Some(kv._2), Map.empty)

  def nearest(key: String) = this

  override def size = 0

  def foreach[U](f: ((String, Nothing)) => U) {}
}

case class NonEmptyRouteTrie[+T](
    key:      String,
    value:    Option[T],
    children: Map[Char, RouteTrie[T]] = Map.empty
) extends RouteTrie[T] {
  def get(k: String) =
    getTrie(k).flatMap(_.value)

  private def getTrie(k: String): Option[NonEmptyRouteTrie[T]] =
    if (k == key) Some(this)
    else if (k.contains(key)) {
      children.get(k(key.length)) match {
        case Some(c: NonEmptyRouteTrie[T]) =>
          c.getTrie(k.substring(key.length))
        case _ => None
      }
    } else None

  def `+`[T1 >: T](kv: (String, T1)) = {
    val (k, v) = kv
    if (k == key) NonEmptyRouteTrie(k, Some(v), children)
    else if (k.contains(key)) {
      val newKey = k.substring(key.length)
      NonEmptyRouteTrie(
        key,
        value,
        children + (newKey(0) -> addToChildren(newKey, v))
      )
    } else if (key.contains(k)) {
      val newKey = key.substring(k.length)
      NonEmptyRouteTrie(
        k,
        Some(v),
        Map(
          newKey(0) -> NonEmptyRouteTrie(newKey, value, children)
        )
      )
    } else {
      val newKey = longestCommonPart(k, key)
      val k1 = key.substring(newKey.length)
      val k2 = k.substring(newKey.length)
      NonEmptyRouteTrie(
        newKey,
        None,
        Map(
          k1(0) -> NonEmptyRouteTrie(k1, value, children),
          k2(0) -> NonEmptyRouteTrie(k2, Some(v), Map.empty)
        )
      )
    }
  }

  private def addToChildren[T1 >: T](k: String, v: T1): RouteTrie[T1] =
    children.get(k(0)) match {
      case Some(n) => n + (k -> v)
      case None    => NonEmptyRouteTrie(k, Some(v), Map.empty)
    }

  private def longestCommonPart(a: String, b: String) = {
    @tailrec @inline
    def f(
      a:          String,
      b:          String,
      index:      Int,
      commonPart: StringBuffer
    ): String = {
      (a.headOption, b.headOption) match {
        case (Some(c1), Some(c2)) if c1 == c2 =>
          f(
            a.drop(1),
            b.drop(1),
            index + 1,
            commonPart.append(c1)
          )
        case _ => commonPart.toString
      }
    }

    f(a, b, 0, new StringBuffer)
  }

  def nearest(key: String) = this

  def foreach[U](f: ((String, T)) => U) {
    foreach(f, "")
  }

  private def foreach[U](f: ((String, T)) => U, keyPrefix: String) {
    val fullKey = keyPrefix + key
    value.foreach(v => f(fullKey -> v))
    children.foreach {
      case (_, c: NonEmptyRouteTrie[T]) =>
        c.foreach(f, fullKey)
    }
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: NonEmptyRouteTrie[T] =>
      that.toMap == this.toMap
    case _ => false
  }

  override def toString() =
    s"NonEmptyRouteTrie[$key -> $value, ${children.values.mkString(",")}]"
}
