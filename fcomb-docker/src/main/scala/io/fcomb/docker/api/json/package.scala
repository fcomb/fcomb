package io.fcomb.docker.api

import spray.json._
import spray.json.DefaultJsonProtocol.{listFormat ⇒ _, _}
import io.fcomb.json._
import java.time.{LocalDateTime, ZonedDateTime}

package object json {
  implicit object OptStringFormat extends JsonFormat[Option[String]] {
    def write(o: Option[String]) = o match {
      case Some(s) ⇒ JsString(s)
      case None    ⇒ JsNull
    }

    def read(v: JsValue) = v match {
      case JsString(s) ⇒
        if (s.isEmpty) None
        else Some(s)
      case JsNull ⇒ None
      case x      ⇒ deserializationError(s"Expected value as JsString, but got $x")
    }
  }

  implicit object OptZonedDateTimeFormat extends JsonFormat[Option[ZonedDateTime]] {
    def write(o: Option[ZonedDateTime]) = o match {
      case Some(d) ⇒ JsString(d.toString)
      case None    ⇒ JsNull
    }

    def read(v: JsValue) = v match {
      case JsString(s) ⇒
        val date = ZonedDateTime.parse(s)
        if (date.getYear() == 1) None
        else Some(date)
      case JsNull ⇒ None
      case x      ⇒ deserializationError(s"Expected date as JsString, but got $x")
    }
  }

  implicit def listFormat[T: JsonFormat] =
    new RootJsonFormat[List[T]] {
      def write(list: List[T]) = list match {
        case Nil ⇒ JsNull
        case xs  ⇒ JsArray(xs.map(_.toJson).toVector)
      }

      def read(value: JsValue): List[T] = value match {
        case JsArray(xs) ⇒ xs.map(_.convertTo[T])(collection.breakOut)
        case JsNull      ⇒ List.empty
        case x           ⇒ deserializationError(s"Expected List as JsArray, but got $x")
      }
    }

  implicit def mapFormat[K: JsonFormat, V: JsonFormat] =
    new RootJsonFormat[Map[K, V]] {
      def write(m: Map[K, V]) = JsObject {
        m.map { field ⇒
          field._1.toJson match {
            case JsString(x) ⇒ x → field._2.toJson
            case x           ⇒ throw new SerializationException(s"Map key must be formatted as JsString, not '$x'")
          }
        }
      }

      def read(value: JsValue) = value match {
        case x: JsObject ⇒ x.fields.map { field ⇒
          (JsString(field._1).convertTo[K], field._2.convertTo[V])
        }(collection.breakOut)
        case JsNull ⇒ Map.empty
        case x      ⇒ deserializationError(s"Expected Map as JsObject, but got $x")
      }
    }

  object ZeroOptLongFormat extends JsonFormat[Option[Long]] {
    def write(opt: Option[Long]) = opt match {
      case Some(n) if n > 0L ⇒ JsNumber(n)
      case _                 ⇒ JsNumber(0)
    }

    def read(v: JsValue) = v match {
      case JsNumber(jn) ⇒ jn.toLong match {
        case 0L ⇒ None
        case n  ⇒ Some(n)
      }
      case JsNull ⇒ None
      case x      ⇒ deserializationError(s"Expected value as JsNumber, but got $x")
    }
  }

  object ZeroOptIntFormat extends JsonFormat[Option[Int]] {
    def write(opt: Option[Int]) = opt match {
      case Some(n) if n > 0 ⇒ JsNumber(n)
      case _                ⇒ JsNumber(0)
    }

    def read(v: JsValue) = v match {
      case JsNumber(jn) ⇒ jn.toInt match {
        case 0 ⇒ None
        case n ⇒ Some(n)
      }
      case JsNull ⇒ None
      case x      ⇒ deserializationError(s"Expected value as JsNumber, but got $x")
    }
  }
}
