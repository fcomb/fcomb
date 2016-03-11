package io.fcomb.utils

import scala.util.{ Random => ScalaRandom }
import java.security.SecureRandom

object Random {
  val random = new ScalaRandom(new SecureRandom())
}
