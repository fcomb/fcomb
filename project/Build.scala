import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager, SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtAspectj, SbtAspectj.AspectjKeys
import com.gilt.sbt.newrelic.NewRelic, NewRelic.autoImport._

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

  val javaRunOptions = Seq(
    "-server",
    "-Xms2048M",
    "-Xmx2048M",
    "-Xss6M",
    "-XX:+CMSClassUnloadingEnabled"
    // "-agentlib:TakipiAgent"
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
        ) ++ compilerFlags,
        javaOptions               ++= Seq("-Dfile.encoding=UTF-8", "-Dscalac.patmat.analysisBudget=off"),
        javacOptions              ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
        parallelExecution in Test :=  false,
        fork              in Test :=  true,
        unmanagedResourceDirectories in Compile <+= baseDirectory(_ / "src/main/scala")
      )

  private def getWeaver = update map { report =>
    report.matching(moduleFilter(organization = "org.aspectj", name = "aspectjweaver")).headOption
  }

  lazy val root = Project(
    "server",
    file("."),
    settings =
      defaultSettings               ++
      SbtNativePackager.packageArchetype.java_application ++
      SbtAspectj.aspectjSettings ++
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
        ) ++ compilerFlags,
        executableScriptName                      := "start",
        javaOptions in Universal                  ++= javaRunOptions.map { o => s"-J$o" },
        packageName in Universal                  := "dist",
        mappings in Universal <++= (AspectjKeys.weaver in SbtAspectj.Aspectj).map { path =>
          Seq(path.get -> "aspectj/weaver.jar")
        },
        bashScriptExtraDefines                    += """addJava "-javaagent:${app_home}/../aspectj/weaver.jar"""",
        scriptClasspath                           ~= (cp => "../config" +: cp),
        newrelicAppName                           := "fcomb-server",
        fork in run                               := true
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
     id = "models",
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

  lazy val macros = Project(
     id = "macros",
     base = file("fcomb-macros"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.macros
     )
  )

  lazy val persist = Project(
     id = "persist",
     base = file("fcomb-persist"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.persist
     )
  ).dependsOn(macros, models, utils)
}
