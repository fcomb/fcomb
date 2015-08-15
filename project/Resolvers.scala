import sbt._

object Resolvers {
  lazy val seq = Seq(
    DefaultMavenRepository,
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.url("fcomb", url("https://dl.bintray.com/fcomb/maven"))(Resolver.ivyStylePatterns),
    "Kamon Repository" at "http://repo.kamon.io",
    "Java.net Maven2 Repository"       at "http://download.java.net/maven/2/",
    "Twitter Repository"               at "http://maven.twttr.com",
    "Websudos releases"                at "http://maven.websudos.co.uk/ext-release-local"
  )
}
