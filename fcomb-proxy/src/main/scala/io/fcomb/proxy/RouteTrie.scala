package io.fcomb.proxy

import scala.annotation.tailrec
import scala.collection.generic.{ CanBuildFrom, ImmutableMapFactory }
import scala.collection.mutable.OpenHashMap

sealed trait RouteKind

@SerialVersionUID(1L)
private case object StaticRoute extends RouteKind // /uri

@SerialVersionUID(1L)
private case class WildcardRoute( // /*param
    name: String
) extends RouteKind {
  require(name.nonEmpty, "name can't be empty")
}

@SerialVersionUID(1L)
private case class ParameterRoute( // /:param
    name: String
) extends RouteKind {
  require(name.nonEmpty, "name can't be empty")
}

@SerialVersionUID(1L)
private[proxy] case class RouteNode[T](
    key:            String,
    value:          Option[T],
    kind:           RouteKind,
    children:       OpenHashMap[Char, RouteNode[T]] = OpenHashMap.empty[Char, RouteNode[T]],
    childParameter: Option[RouteNode[T]]            = None
) extends Traversable[(String, T)] {
  require(key.nonEmpty, s"Key can't be empty: $this")
  if (kind == WildcardRoute)
    require(key.head != '*', s"Wildcard route node must start with prefix symbol '*': $this")
  if (kind == ParameterRoute)
    require(key.head != ':', s"Parameter route node must start with prefix symbol ':': $this")
  if (kind == StaticRoute)
    require(!key.exists(nonStaticPrefix), s"Static route node must not start with prefix symbol ':' or '*': $this")

  def get(k: String) =
    getTrie(cleanKey(k)).flatMap(_.value)

  @inline
  private def cleanKey(k: String) =
    if (k.last != '/') k + '/' else k

  @inline
  private def nonStaticPrefix(c: Char): Boolean =
    c == ':' || c == '*'

  @inline
  private def nonStaticPrefix(k: String): Boolean =
    nonStaticPrefix(k.head)

  @tailrec
  private def getTrie(k: String): Option[RouteNode[T]] =
    if (k == key) Some(this)
    else if (k.contains(key)) {
      children.get(k(key.length)) match {
        case Some(c: RouteNode[T]) => c.getTrie(k.drop(key.length))
        case _                     => None
      }
    } else None

  def `+`(kv: (String, T)) = {
    require(kv._1.nonEmpty, s"Key can't be empty: $kv")

    val k = cleanKey(kv._1)
    val v = kv._2

    if (nonStaticPrefix(k)) addToChildParameter(k, v)
    else {
      if (k == key) RouteNode(k, Some(v), kind, children, childParameter)
      else if (k.startsWith(key)) {
        val newKey = k.drop(key.length)
        if (nonStaticPrefix(newKey)) addToChildParameter(newKey, v)
        else {
          children += (newKey(0) -> addToChildren(newKey, v))
          this
        }
      } else if (key.startsWith(k)) {
        val newKey = key.drop(k.length)
        val cc = OpenHashMap(
          newKey(0) -> RouteNode(newKey, value, kind, children, childParameter)
        )
        RouteNode(k, Some(v), StaticRoute, cc, None)
      } else {
        val newKey = longestCommonPart(k, key)
        val k1 = key.drop(newKey.length)
        val k2 = k.drop(newKey.length)
        val cc = OpenHashMap(
          k1(0) -> RouteNode(k1, value, kind, children, childParameter),
          k2(0) -> RouteNode(k2, Some(v), StaticRoute)
        )
        RouteNode(newKey, None, StaticRoute, cc, None)
      }
    }
  }

  private def addToChildParameter(k: String, v: T) = {
    require(nonStaticPrefix(k), s"Key '$k' must start with ':' or '*'")

    val keyName = k.takeWhile(_ != '/')
    val keyPostfix = k.drop(keyName.length)
    val route = keyName.head match {
      case ':' => ParameterRoute(keyName.drop(1))
      case '*' => WildcardRoute(keyName.drop(1))
    }
    val childNode: RouteNode[T] = childParameter match {
      case Some(n) =>
        if (n.kind == route) n
        else throw new Exception(s"Conflict on ${n.kind} =!= $route")
      case None => RouteNode(keyName, None, route)
    }
    childNode.children += (
      keyPostfix(0) -> childNode.addToChildren(keyPostfix, v)
    )
    this.copy(childParameter = Some(childNode))
  }

  private def addToChildren(k: String, v: T): RouteNode[T] =
    children.get(k(0)) match {
      case Some(n) => n + (k -> v)
      case None =>
        val keyPrefix = k.takeWhile(!nonStaticPrefix(_))
        if (keyPrefix.length == k.length)
          RouteNode(keyPrefix, Some(v), StaticRoute)
        else if (keyPrefix.nonEmpty)
          RouteNode(keyPrefix, None, StaticRoute)
            .addToChildParameter(k.drop(keyPrefix.length), v)
        else addToChildParameter(k, v)
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
          f(a.drop(1), b.drop(1), index + 1, commonPart.append(c1))
        case _ => commonPart.toString
      }
    }

    f(a, b, 0, new StringBuffer)
  }

  def foreach[U](f: ((String, T)) => U): Unit = {
    foreach(f, "")
  }

  private def foreach[U](f: ((String, T)) => U, keyPrefix: String): Unit = {
    val fullKey = keyPrefix + key
    value.foreach(v => f(fullKey -> v))
    children.foreach {
      case (_, c: RouteNode[T]) => c.foreach(f, fullKey)
    }
    childParameter.foreach(_.foreach(f, fullKey))
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: RouteNode[T] => that.toMap == this.toMap
    case _                  => false
  }

  private def toString(padding: Int): String = {
    val p = "  " * 2 * padding
    val childrenS = children
      .map { case (k, v) => s"$p${v.toString(padding + 1)}" }
      .mkString
    val childS = childParameter
      .map(_.toString(padding + 1))
      .getOrElse("")
    s"""
$p$key -> $value, $kind =>
$p  children: $childrenS
$p  childParameter: $childS
""".stripMargin
  }

  override def toString(): String = toString(0)
}

object RouteTrie {
  def empty[T]: RouteNode[T] = RouteNode("/", None, StaticRoute)

  def apply[T](kvs: (String, T)*): RouteNode[T] =
    kvs.foldLeft[RouteNode[T]](empty) {
      case (t, (k, v)) => t + (k, v)
    }
}
