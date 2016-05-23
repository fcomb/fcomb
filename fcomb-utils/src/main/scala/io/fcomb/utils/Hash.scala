package io.fcomb.utils

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import net.jpountz.xxhash.XXHashFactory

object Hash {
  private val seed = 0x9747b28c
  private val xxhashFactory = XXHashFactory.fastestInstance

  def xxhash(stream: InputStream): Int = {
    val hash32 = xxhashFactory.newStreamingHash32(seed)
    val buf = Array.ofDim[Byte](8)
    while (stream.available() > 0) hash32.update(buf, 0, stream.read(buf))
    hash32.getValue
  }

  def xxhash(s: String): Int =
    xxhash(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)))
}
