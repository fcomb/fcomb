/*
 * Copyright 2016 fcomb. <https://fcomb.io>
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

package io.fcomb.json

import cats.data.Xor
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.{DateTimeFormatter, DateTimeParseException}
import org.threeten.bp.format.DateTimeFormatter.ISO_ZONED_DATE_TIME

object Java8TimeFormats {
  final def encodeZonedDateTime(formatter: DateTimeFormatter): Encoder[ZonedDateTime] =
    Encoder.instance(time => Json.fromString(time.format(formatter)))

  final def decodeZonedDateTime(formatter: DateTimeFormatter): Decoder[ZonedDateTime] =
    Decoder.instance { c =>
      c.as[String].flatMap { s =>
        try Xor.right(ZonedDateTime.parse(s, formatter))
        catch {
          case _: DateTimeParseException => Xor.left(DecodingFailure("ZonedDateTime", c.history))
        }
      }
    }

  implicit final val decodeZonedDateTimeDefault: Decoder[ZonedDateTime] = decodeZonedDateTime(
    ISO_ZONED_DATE_TIME)
  implicit final val encodeZonedDateTimeDefault: Encoder[ZonedDateTime] = encodeZonedDateTime(
    ISO_ZONED_DATE_TIME)
}
