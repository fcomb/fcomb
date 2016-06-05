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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Implicits {
  object global {
    private var sys: ActorSystem       = _
    private var mat: ActorMaterializer = _

    def system: ActorSystem             = sys
    def materializer: ActorMaterializer = mat

    def apply(system: ActorSystem, materializer: ActorMaterializer) = {
      sys = system
      mat = materializer
    }
  }
}
