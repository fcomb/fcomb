import sbt._

object Resolvers {
  lazy val seq = Seq(
    DefaultMavenRepository,
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.url("fcomb", url("https://dl.bintray.com/fcomb/maven"))(Resolver.ivyStylePatterns),
    Resolver.bintrayRepo("websudos", "oss-releases"),
    Resolver.bintrayRepo("underscoreio", "libraries"),
    Resolver.bintrayRepo("krasserm", "maven"),
    // "Kamon Repository" at "http://repo.kamon.io",
    "Akka Snapshot Repository"   at "http://repo.akka.io/snapshots/",
    "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
    "Twitter Repository"         at "http://maven.twttr.com"
  )
}
