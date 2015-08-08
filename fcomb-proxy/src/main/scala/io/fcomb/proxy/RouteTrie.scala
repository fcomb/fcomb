package io.fcomb.proxy

import scala.annotation.tailrec
import scala.collection.generic.{ CanBuildFrom, ImmutableMapFactory }
import scala.collection.immutable

sealed trait RouteKind

@SerialVersionUID(1L)
case object StaticRoute extends RouteKind // /uri

@SerialVersionUID(1L)
case class WildcardRoute( // /*param
    name: String
) extends RouteKind {
  require(name.nonEmpty, "name can't be empty")
}

@SerialVersionUID(1L)
case class ParameterRoute( // /:param
    name: String
) extends RouteKind {
  require(name.nonEmpty, "name can't be empty")
}

@SerialVersionUID(1L)
case class RouteNode[+T](
    key:            String,
    value:          Option[T],
    kind:           RouteKind,
    children:       Map[Char, RouteNode[T]],
    childParameter: Option[RouteNode[T]]
) extends Traversable[(String, T)] {
  require(key.nonEmpty, s"Key can't be empty: $this")
  if (kind == WildcardRoute)
    require(key.head != '*', s"Wildcard route node must start with prefix symbol '*': $this")
  if (kind == ParameterRoute)
    require(key.head != ':', s"Parameter route node must start with prefix symbol ':': $this")
  if (kind == StaticRoute)
    require(!key.exists(c => c == ':' || c == '*'), s"Static route node must not start with prefix symbol ':' or '*': $this")

  def get(k: String) =
    getTrie(cleanKey(k)).flatMap(_.value)

  @inline
  private def cleanKey(k: String) =
    if (k.last != '/') k + '/' else k

  private def getTrie(k: String): Option[RouteNode[T]] =
    if (k == key) Some(this)
    else if (k.contains(key)) {
      children.get(k(key.length)) match {
        case Some(c: RouteNode[T]) =>
          c.getTrie(k.drop(key.length))
        case _ => None
      }
    } else None

  def `+`[T1 >: T](kv: (String, T1)) = {
    require(kv._1.nonEmpty, s"Key can't be empty: $kv")

    val k = cleanKey(kv._1)
    val v = kv._2

    if (k.head == ':' || k.head == '*') addToChildParameter(k, v)
    else {
      if (k == key) RouteNode(k, Some(v), kind, children, childParameter)
      else if (k.startsWith(key)) {
        val newKey = k.drop(key.length)
        if (newKey.head == ':' || newKey.head == '*')
          addToChildParameter(newKey, v)
        else this.copy(
          children = children + (newKey(0) -> addToChildren(newKey, v))
        )
      } else if (key.startsWith(k)) {
        val newKey = key.drop(k.length)
        RouteNode(
          k,
          Some(v),
          StaticRoute,
          Map(
            newKey(0) -> RouteNode(newKey, value, kind, children, childParameter)
          ),
          None
        )
      } else {
        val newKey = longestCommonPart(k, key)
        val k1 = key.drop(newKey.length)
        val k2 = k.drop(newKey.length)
        RouteNode(
          newKey,
          None,
          StaticRoute,
          Map(
            k1(0) -> RouteNode(k1, value, kind, children, childParameter),
            k2(0) -> RouteNode(k2, Some(v), StaticRoute, Map.empty, None)
          ),
          None
        )
      }
    }
  }

  private def addToChildParameter[T1 >: T](k: String, v: T1) = {
    require(k.head == ':' || k.head == '*', s"Key '$k' must start with ':' or '*'")

    val keyName = k.takeWhile(_ != '/')
    val keyPostfix = k.drop(keyName.length)
    val route = keyName.head match {
      case ':' => ParameterRoute(keyName.drop(1))
      case '*' => WildcardRoute(keyName.drop(1))
    }
    val childNode = childParameter match {
      case Some(n) =>
        if (n.kind == route) n
        else throw new Exception(s"Conflict on ${n.kind} =!= $route")
      case None =>
        RouteNode(keyName, None, route, Map.empty, None)
    }
    val childNodeChildren = childNode.children + (
      keyPostfix(0) -> childNode.addToChildren(keyPostfix, v)
    )
    this.copy(
      childParameter = Some(childNode.copy(
        children = childNodeChildren
      ))
    )
  }

  private def addToChildren[T1 >: T](k: String, v: T1): RouteNode[T1] =
    children.get(k(0)) match {
      case Some(n) => n + (k -> v)
      case None =>
        val keyPrefix = k.takeWhile(c => c != ':' && c != '*')
        if (keyPrefix.length == k.length)
          RouteNode(keyPrefix, Some(v), StaticRoute, Map.empty, None)
        else if (keyPrefix.nonEmpty)
          RouteNode(keyPrefix, None, StaticRoute, Map.empty, None)
            .addToChildParameter(k.drop(keyPrefix.length), v)
        else
          addToChildParameter(k, v)
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

  def compact(): RouteNode[T] = this

  def foreach[U](f: ((String, T)) => U): Unit = {
    foreach(f, "")
  }

  private def foreach[U](f: ((String, T)) => U, keyPrefix: String): Unit = {
    val fullKey = keyPrefix + key
    value.foreach(v => f(fullKey -> v))
    children.foreach {
      case (_, c: RouteNode[T]) =>
        c.foreach(f, fullKey)
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
  def empty[T]: RouteNode[T] =
    RouteNode("/", None, StaticRoute, Map.empty, None)

  def apply[T](kvs: (String, T)*): RouteNode[T] =
    kvs
      .foldLeft[RouteNode[T]](empty) {
        case (t, (k, v)) => t + (k, v)
      }
      .compact
}
