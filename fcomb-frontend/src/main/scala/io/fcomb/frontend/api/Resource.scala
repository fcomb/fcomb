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

object Resource {
  val prefix = "/api/v1"

  val sessions = prefix + "/sessions"

  val repositories = prefix + "/repositories"
  def repository(slug: String) = s"$repositories/$slug"
  def repository(id: Long)     = s"$repositories/$id"

  val users  = prefix + "/users"
  val signUp = users + "/sign_up"

  val user             = prefix + "/user"
  val userRepositories = user + "/repositories"
}
