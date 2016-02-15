resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Classpaths.sbtPluginReleases
)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.6")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.10")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.3")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

// addSbtPlugin("io.ino" %% "sbt-pillar-plugin" % "1.0.4")

addCompilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.14")

// addSbtPlugin("com.typesafe" % "sbt-abide" % "0.1-SNAPSHOT")

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.12")

addSbtPlugin("com.trueaccord.scalapb" % "sbt-scalapb" % "0.4.19")

// addCompilerPlugin("com.softwaremill.clippy" % "plugin" % "0.1" cross CrossVersion.full)
