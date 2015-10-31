resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Classpaths.sbtPluginReleases
)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.5")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.9")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.0")

// addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

// addSbtPlugin("io.ino" %% "sbt-pillar-plugin" % "1.0.4")

addCompilerPlugin("org.scalamacros" %% "paradise" % "2.0.1" cross CrossVersion.full)

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

addSbtPlugin("org.brianmckenna" % "sbt-wartremover" % "0.14")

// addSbtPlugin("com.typesafe" % "sbt-abide" % "0.1-SNAPSHOT")

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.12")

addSbtPlugin("com.trueaccord.scalapb" % "sbt-scalapb" % "0.4.19")
