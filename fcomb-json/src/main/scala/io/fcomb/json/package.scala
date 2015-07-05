package io.fcomb

import io.fcomb.models._
import argonaut._, Argonaut._, Shapeless._
import scalaz._, Scalaz._
import java.time.LocalDateTime
import java.util.UUID

package object json {
  implicit val dateTimeEncodeJson: EncodeJson[LocalDateTime] =
    EncodeJson((d: LocalDateTime) => jString(d.toString))

  implicit val uuidEncodeJson: EncodeJson[UUID] =
    EncodeJson((uuid: UUID) => jString(uuid.toString))

  private object shapelessImplicits {
    val userResponseEncodeJson: EncodeJson[UserResponse] =
      implicitly[EncodeJson[UserResponse]]
  }

  implicit val userRequestDecodeJson: DecodeJson[UserRequest] =
    DecodeJson(c => for {
      email <- (c --\ "email").as[String]
      password <- (c --\ "password").as[String]
      username <- (c --\ "username").as[String]
      fullName <- (c --\ "fullName").as[Option[String]]
    } yield UserRequest(
      email = email,
      password = password,
      username = username,
      fullName = fullName
    ))

  implicit val userResponseEncodeJson: EncodeJson[UserResponse] =
    shapelessImplicits.userResponseEncodeJson
}
