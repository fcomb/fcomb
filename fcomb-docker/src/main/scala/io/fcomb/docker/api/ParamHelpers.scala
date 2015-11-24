package io.fcomb.docker.api

import methods.ImageMethods.Registry

private[api] object ParamHelpers {
  implicit class OptionStringHelpers(val opt: Option[String]) extends AnyVal {
    def toParam(default: String = "") =
      opt.getOrElse(default)
  }

  implicit class OptionIntHelpers(val opt: Option[Int]) extends AnyVal {
    def toParam() =
      opt.map(_.toString).getOrElse("")

    def toParam(default: Int) =
      opt.map(_.toString).getOrElse(default.toString)
  }

  implicit class OptionLongHelpers(val opt: Option[Long]) extends AnyVal {
    def toParam() =
      opt.map(_.toString).getOrElse("")

    def toParam(default: Long) =
      opt.map(_.toString).getOrElse(default.toString)

    def toMemorySwapParam() =
      opt.getOrElse(-1L).toString
  }

  implicit class OptionRegistryHelpers(val opt: Option[Registry]) extends AnyVal {
    def toParam() =
      opt.map(_.toParam).getOrElse("")
  }
}
