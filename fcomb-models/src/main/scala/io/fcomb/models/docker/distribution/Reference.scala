package io.fcomb.models.docker.distribution

sealed trait Reference {
  def value: String

  override def toString = value
}

object Reference {
  final case class Tag(name: String) extends Reference {
    def value = name
  }

  final case class Digest(digest: String) extends Reference {
    def value = s"${ImageManifest.sha256Prefix}$digest"
  }

  def apply(s: String): Reference = {
    if (isDigest(s)) Digest(getDigest(s))
    else Tag(s)
  }

  def isDigest(s: String): Boolean =
    s.startsWith(ImageManifest.sha256Prefix)

  def getDigest(digest: String): String =
    digest.drop(ImageManifest.sha256Prefix.length)
}
