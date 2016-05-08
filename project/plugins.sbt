resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Classpaths.sbtPluginReleases
)

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M11-1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.6")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.10")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.4")

addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.14")

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.12")

addSbtPlugin("com.softwaremill.clippy" % "plugin-sbt" % "0.2.5")
