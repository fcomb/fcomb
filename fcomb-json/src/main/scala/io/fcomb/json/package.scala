package io.fcomb

import io.fcomb.models._
import argonaut._, Argonaut._, Shapeless._
import scalaz._, Scalaz._
import java.time.LocalDateTime
import java.util.UUID

package object json {
  implicit def DateTimeEncodeJson: EncodeJson[LocalDateTime] =
    EncodeJson((d: LocalDateTime) => jString(d.toString))

  implicit def UuidEncodeJson: EncodeJson[UUID] =
    EncodeJson((uuid: UUID) => jString(uuid.toString))

  implicit def UserEncodeJson: EncodeJson[User] =
    jencode4L((u: User) =>
      (u.id, u.email, u.username, u.fullName))("id", "email", "username", "fullName")

  private object shapelessImplicits {
    val userRequestDecodeJson: DecodeJson[UserRequest] =
      implicitly[DecodeJson[UserRequest]]

    val userResponseEncodeJson: EncodeJson[UserResponse] =
      implicitly[EncodeJson[UserResponse]]
  }

  implicit val userRequestDecodeJson: DecodeJson[UserRequest] =
    shapelessImplicits.userRequestDecodeJson

  implicit val userResponseEncodeJson: EncodeJson[UserResponse] =
    shapelessImplicits.userResponseEncodeJson

  // implicit def DecodeUser: DecodeJson[User] =
  //   DecodeJson(j => for {
  //     a <- (j --\ "a").as[Option[String]]
  //     l <- (j --\ "l").as[Option[List[Int]]]
  //   } yield User(a, l.getOrElse(List.empty)))
}
