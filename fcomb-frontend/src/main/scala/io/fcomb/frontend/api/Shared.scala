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

package io.fcomb.frontend.api

import upickle.default._

// TODO: fcomb-rpc project for shared case classes

final case class Session(token: String)

final case class SessionCreateRequest(email: String, password: String)

final case class UserSignUpRequest(
    email: String,
    password: String,
    username: String,
    fullName: String
)

object Formats {
  final implicit val sessionPickler: Writer[Session] = macroW[Session]
  final implicit val sessionCreateRequestPickler: Writer[SessionCreateRequest] =
    macroW[SessionCreateRequest]
  final implicit val userSignUpRequestPickler: Writer[UserSignUpRequest] =
    macroW[UserSignUpRequest]
}
