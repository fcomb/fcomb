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

package io.fcomb.frontend.dispatcher.handlers

import diode.data.{AsyncAction, Pot, PotMap, PotState}
import diode.ActionResult

object ActionUtils {
  def distinctKeys[K, R](action: AsyncAction[Map[K, Pot[R]], _],
                         map: => PotMap[K, R],
                         keys: Set[K]): Set[K] =
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
}
