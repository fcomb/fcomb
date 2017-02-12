/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.frontend.dispatcher

import diode.data.PotMap
import io.fcomb.models.SessionPayload
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import io.fcomb.rpc.OrganizationResponse

final case class RootModel(session: Option[String],
                           currentUser: Option[SessionPayload.User],
                           repos: PotMap[String, RepositoryResponse],
                           orgs: PotMap[String, OrganizationResponse])
