package io.fcomb.utils

import scala.util.{ Random => ScalaRandom }
import java.security.SecureRandom

object Random {
  val sRandom = new SecureRandom()
  val random = new ScalaRandom(sRandom)
}
