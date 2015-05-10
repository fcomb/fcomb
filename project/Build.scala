import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager, SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

object Build extends sbt.Build {
  val Organization = "io.fcomb"
  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.11.6"

  lazy val buildSettings =
    Defaults.defaultSettings ++
      Seq(
        organization         := Organization,
        version              := Version,
        scalaVersion         := ScalaVersion,
        crossPaths           := false,
        organizationName     := Organization,
        organizationHomepage := Some(url("https://fcomb.io"))
      )

  val compilerFlags = Seq(
    "-Ywarn-dead-code",
    "-Ywarn-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused"
    // "-Xfatal-warnings"
  )

  lazy val defaultSettings =
    buildSettings ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings ++
      Seq(
        resolvers                 ++= Resolvers.seq,
        scalacOptions in Compile  ++= Seq(
          "-encoding",
          "UTF-8",
          "-deprecation",
          "-unchecked",
          "-feature",
          "-language:higherKinds"
        ) ++ compilerWarnings,
        javaOptions               ++= Seq("-Dfile.encoding=UTF-8", "-Dscalac.patmat.analysisBudget=off"),
        javacOptions              ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
        parallelExecution in Test :=  false,
        fork              in Test :=  true
      )

  lazy val root = Project(
    "fcomb-server",
    file("."),
    settings =
      defaultSettings               ++
      SbtNativePackager.packageArchetype.java_application ++
      Revolver.settings             ++
      Seq(
        libraryDependencies                       ++= Dependencies.root,
        Revolver.reStartArgs                      :=  Seq("io.fcomb.server.Main"),
        mainClass            in Revolver.reStart  :=  Some("io.fcomb.server.Main"),
        autoCompilerPlugins                       :=  true,
        scalacOptions        in (Compile,doc)     :=  Seq(
          "-groups",
          "-implicits",
          "-diagrams"
        ) ++ compilerWarnings,
        executableScriptName                      := "start",
        javaOptions in Universal                  ++= Seq(
          "-server",
          "-Xms2048M",
          "-Xmx2048M",
          "-Xss6M",
          "-XX:+CMSClassUnloadingEnabled"
        ),
        packageName in Universal := "dist",
        scriptClasspath ~= (cp => "../config" +: cp)
      )
  ).dependsOn(api)
    .enablePlugins(SbtNativePackager)

  lazy val api = Project(
     id = "api",
     base = file("fcomb-api"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.api
     )
  ).dependsOn(persist, utils)

  lazy val models = Project(
     id = "fcomb-models",
     base = file("fcomb-models"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.models
     )
  )

  lazy val utils = Project(
     id = "utils",
     base = file("fcomb-utils"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.utils
     )
  )

  lazy val persist = Project(
     id = "persist",
     base = file("fcomb-persist"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.persist
     )
  ).dependsOn(models, utils)
}
