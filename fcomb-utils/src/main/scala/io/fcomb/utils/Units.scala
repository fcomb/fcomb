package io.fcomb.utils

object Units {
  private val size = 1000L

  implicit class UnitsHelper(val v: Long) extends AnyVal {
    @inline
    def KB() = v * size

    @inline
    def MB() = v.KB() * size

    @inline
    def GB() = v.MB() * size

    @inline
    def TB() = v.GB() * size

    @inline
    def PB() = v.TB() * size
  }
}
