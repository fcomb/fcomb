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

import java.io.{ByteArrayInputStream, InputStream}
import net.jpountz.xxhash.XXHashFactory

object Hash {
  private val seed: Long    = 0x9747b28c
  private val xxhashFactory = XXHashFactory.fastestInstance

  def xxhash(stream: InputStream): Long = {
    val hash64 = xxhashFactory.newStreamingHash64(seed)
    val buf    = Array.ofDim[Byte](256)
    while (stream.available() > 0) hash64.update(buf, 0, stream.read(buf))
    hash64.getValue()
  }

  def xxhash(s: String): Long =
    xxhash(new ByteArrayInputStream(s.getBytes()))
}
