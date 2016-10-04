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

package io.fcomb.utils

object StringUtils {
  def snakify(name: String) =
    name
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .toLowerCase

  def trim(s: String): Option[String] = {
    val value = s.trim
    if (value.isEmpty) None else Some(value)
  }

  def trim(opt: Option[String]): Option[String] =
    opt.flatMap(trim)

  def normalizeEmail(email: String) =
    email.trim.toLowerCase

  def equalSecure(s1: String, s2: String) =
    if (s1.length != s2.length) false
    else {
      val res = s1.zip(s2).foldLeft(0) {
        case (n, (c1, c2)) => n | (c1 ^ c2)
      }
      res == 0
    }

  private val hex = "0123456789abcdef"

  def hexify(bytes: Array[Byte]): String = {
    val builder = new java.lang.StringBuilder(bytes.length * 2)
    bytes.foreach { byte =>
      builder.append(hex.charAt((byte & 0xF0) >> 4)).append(hex.charAt(byte & 0xF))
    }
    builder.toString
  }
}
