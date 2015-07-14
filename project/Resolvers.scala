import sbt._

object Resolvers {
  lazy val seq = Seq(
    DefaultMavenRepository,
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.url("dthub", url("https://dl.bintray.com/dthub/maven"))(Resolver.ivyStylePatterns),
    "Kamon Repository" at "http://repo.kamon.io"
  )
}
