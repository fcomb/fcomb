package io.fcomb.models.docker.distribution

sealed trait Reference {
  def value: String

  override def toString = value

  def isTag: Boolean
  def isDigest: Boolean
}

object Reference {
  final case class Tag(name: String) extends Reference {
    def value = name
    def isTag = true
    def isDigest = false
  }

  final case class Digest(digest: String) extends Reference {
    def value = s"${ImageManifest.sha256Prefix}$digest"
    def isTag = false
    def isDigest = true
  }

  def apply(s: String): Reference = {
    if (s.startsWith(ImageManifest.sha256Prefix)) Digest(getDigest(s))
    else Tag(s)
  }

  def getDigest(digest: String): String =
    digest.drop(ImageManifest.sha256Prefix.length)
}
