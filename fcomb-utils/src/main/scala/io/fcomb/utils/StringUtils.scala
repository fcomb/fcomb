package io.fcomb.utils

object StringUtils {
  def snakify(name: String) =
    name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
      .toLowerCase

  def trim(opt: Option[String]) =
    opt.map(_.trim) match {
      case res @ Some(s) if s.nonEmpty ⇒ res
      case _                           ⇒ None
    }

  def normalizeEmail(email: String) =
    email.trim.toLowerCase

  def equalSecure(s1: String, s2: String) = {
    if (s1.length != s2.length) false
    else {
      val res = s1.zip(s2).foldLeft(0) {
        case (n, (c1, c2)) ⇒ n | (c1 ^ c2)
      }
      res == 0
    }
  }

  private val hex = "0123456789abcdef"

  def hexify(bytes: Array[Byte]): String = {
    val builder = new java.lang.StringBuilder(bytes.length * 2)
    bytes.foreach { byte ⇒
      builder
        .append(hex.charAt((byte & 0xF0) >> 4))
        .append(hex.charAt(byte & 0xF))
    }
    builder.toString
  }
}
