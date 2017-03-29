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

import cats.syntax.eq._
import diode.data.PotMap
import diode.react.ReactConnector
import diode.{Action, Circuit}
import io.fcomb.frontend.dispatcher.fetchers._
import io.fcomb.frontend.dispatcher.handlers._
import io.fcomb.models.UserRole
import japgolly.scalajs.react.{Callback, CallbackTo}

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  protected def actionHandler = composeHandlers(
    new AuthenticationHandler(zoomRW(m => (m.session, m.currentUser)) {
      case (m, (sv, scu)) => m.copy(session = sv, currentUser = scu)
    }),
    new RepositoriesHandler(zoomRW(_.repos)((m, v) => m.copy(repos = v))),
    new OrganizationsHandler(zoomRW(_.orgs)((m, v) => m.copy(orgs = v)))
  )

  private lazy val repositoryFetcher   = RepositoryFetcher(this)
  private lazy val organizationFetcher = OrganizationFetcher(this)

  protected def initialModel = RootModel(
    session = None,
    currentUser = None,
    repos = PotMap(repositoryFetcher),
    orgs = PotMap(organizationFetcher)
  )

  def dispatchCB(action: Action): Callback =
    CallbackTo(dispatch(action))

  def currentState = zoom(identity).value

  def session = currentState.session

  def currentUser = currentState.currentUser

  def currentUserIsAdmin =
    currentUser.exists(_.role === UserRole.Admin)

  lazy val repos = connect(_.repos)

  lazy val orgs = connect(_.orgs)
}
