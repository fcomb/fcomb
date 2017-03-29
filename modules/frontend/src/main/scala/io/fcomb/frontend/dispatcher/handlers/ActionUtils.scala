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

package io.fcomb.frontend.dispatcher.handlers

import diode.data.{AsyncAction, Failed, Pending, Pot, PotMap, PotState}
import diode.{ActionHandler, ActionResult, Effect}
import scala.concurrent.ExecutionContext

object ActionUtils {
  def distinctKeys[K, R](action: AsyncAction[_, _], map: => PotMap[K, R], keys: Set[K]): Set[K] =
    if (action.state == PotState.PotEmpty) {
      val m = map.seq.toMap
      keys.foldLeft(Set.empty[K]) {
        case (set, key) =>
          m.get(key) match {
            case Some(v) if v.isPending => set
            case _                      => set + key
          }
      }
    } else keys

  def handleWithKeys[K, R, M](action: AsyncAction[Map[K, Pot[R]], _],
                              map: PotMap[K, R],
                              keys: Set[K])(f: Set[K] => ActionResult[M]): ActionResult[M] = {
    val uniqueKeys = distinctKeys(action, map, keys)
    if (uniqueKeys.isEmpty) ActionResult.NoChange
    else f(uniqueKeys)
  }

  def handleWithKey[K, R, M](action: AsyncAction[Pot[R], _], map: PotMap[K, R], key: K)(
      f: K => ActionResult[M]): ActionResult[M] =
    distinctKeys(action, map, Set(key)).headOption match {
      case Some(uniqueKey) => f(uniqueKey)
      case None            => ActionResult.NoChange
    }

  def mapHandler[K, V, A <: Pot[V], M, P <: AsyncAction[A, P]](key: K)(
      action: AsyncAction[A, P],
      handler: ActionHandler[M, PotMap[K, V]],
      updateEffect: Effect)(implicit ec: ExecutionContext): ActionResult[M] = {
    import PotState._
    import handler._

    def updateInCollection(f: Pot[V] => Pot[V], default: Pot[V]): PotMap[K, V] =
      value.map((k, v) => if (key == k) f(v) else v) ++ (Set(key) -- value.keySet).map(k =>
        k -> default)

    action.state match {
      case PotEmpty       => updated(updateInCollection(_.pending(), Pending()), updateEffect)
      case PotPending     => noChange
      case PotUnavailable => noChange
      case PotReady       => updated(value.updated(key, action.result.get))
      case PotFailed =>
        val ex = action.result.failed.get
        updated(updateInCollection(_.fail(ex), Failed(ex)))
    }
  }
}
