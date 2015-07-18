package io.fcomb.utils

import scala.util.{ Random => ScRandom }
import java.security.SecureRandom

object Random {
  val rand = new ScRandom(new SecureRandom())
}
