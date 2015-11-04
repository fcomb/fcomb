package io.fcomb.utils

object Units {
  private val size = 1024L

  implicit class UnitsHelper(val v: Long) extends AnyVal {
    def KB() = v * size

    def MB() = v.KB() * size

    def GB() = v.MB() * size

    def TB() = v.GB() * size

    def PB() = v.TB() * size
  }
}
