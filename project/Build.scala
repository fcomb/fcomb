import sbt._
import sbt.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtNativePackager, SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
// import com.typesafe.sbt.SbtAspectj, SbtAspectj.AspectjKeys
import play.twirl.sbt._, Import._
// import io.ino.sbtpillar.Plugin.PillarKeys._

object Build extends sbt.Build {
  val Organization = "io.fcomb"
  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.11.7"

  lazy val buildSettings =
    Defaults.defaultSettings ++
      Seq(
        organization         := Organization,
        version              := Version,
        scalaVersion         := ScalaVersion,
        crossPaths           := false,
        organizationName     := Organization,
        organizationHomepage := Some(url("https://fcomb.io")),
        ivyScala             := ivyScala.value.map(_.copy(
          overrideScalaVersion = true
        ))
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
  )

  val javaVersion = "1.8"

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
        javacOptions              ++= Seq("-source", javaVersion, "-target", javaVersion, "-Xlint:unchecked", "-Xlint:deprecation"),
        parallelExecution in Test :=  false,
        fork              in Test :=  true
      )

  lazy val root = Project(
    "server",
    file("."),
    settings =
      defaultSettings               ++
      SbtNativePackager.packageArchetype.java_application ++
      // SbtAspectj.aspectjSettings ++
      Revolver.settings             ++
      Seq(
        Revolver.reStartArgs                      :=  Seq("io.fcomb.Main"),
        mainClass            in Revolver.reStart  :=  Some("io.fcomb.Main"),
        autoCompilerPlugins                       :=  true,
        scalacOptions        in (Compile,doc)     :=  Seq(
          "-groups",
          "-implicits",
          "-diagrams"
        ) ++ compilerFlags,
        executableScriptName                      := "start",
        javaOptions in Universal                  ++= javaRunOptions.map { o => s"-J$o" },
        packageName in Universal                  := "dist",
        // mappings in Universal <++= (AspectjKeys.weaver in SbtAspectj.Aspectj).map { path =>
        //   Seq(path.get -> "aspectj/weaver.jar")
        // },
        // bashScriptExtraDefines                    += """addJava "-javaagent:${app_home}/../aspectj/weaver.jar"""",
        scriptClasspath                           ~= (cp => "../config" +: cp),
        fork in run                               := true
      )
  ).dependsOn(api/*, proxy*/)
    .enablePlugins(SbtNativePackager)

  lazy val api = Project(
     id = "api",
     base = file("fcomb-api"),
     settings = defaultSettings ++ Seq(
       libraryDependencies ++= Dependencies.api
     )
  ).dependsOn(persist, utils, json, validations, services)

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
  ).dependsOn(json)

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
     settings = defaultSettings ++ /*pillarSettings ++*/ Seq(
       libraryDependencies ++= Dependencies.persist // ,
       // pillarConfigFile := file("conf/application.conf"),
       // pillarConfigKey := "cassandra.url",
       // pillarReplicationStrategyConfigKey := "cassandra.replicationStrategy",
       // pillarReplicationFactorConfigKey := "cassandra.replicationFactor",
       // pillarDefaultConsistencyLevelConfigKey := "cassandra.defaultConsistencyLevel",
       // pillarMigrationsDir := file("conf/migrations")
     )
  ).dependsOn(macros, models, utils, validations)

  lazy val json = Project(
    id = "json",
    base = file("fcomb-json"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.json
    )
  ).dependsOn(models, request, response)

  lazy val request = Project(
    id = "request",
    base = file("fcomb-request"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.request
    )
  ).dependsOn(models)

  lazy val response = Project(
    id = "response",
    base = file("fcomb-response"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.response
    )
  ).dependsOn(models)

  lazy val validations = Project(
    id = "validations",
    base = file("fcomb-validations"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.validations
    )
  ).dependsOn(models)

  lazy val services = Project(
    id = "services",
    base = file("fcomb-services"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.services
    )
  ).dependsOn(persist, utils, templates)

  lazy val templates = Project(
    id = "templates",
    base = file("fcomb-templates"),
    settings = defaultSettings ++
      SbtTwirl.projectSettings ++
        Seq(
          TwirlKeys.templateImports += "io.fcomb.templates._, io.fcomb.utils._"
        )
  )
    .dependsOn(utils)
    .enablePlugins(SbtTwirl)

  private def getJamm = update map { report =>
    report.matching(moduleFilter(organization = "com.github.jbellis", name = "jamm")).headOption
  }

  lazy val proxy = Project(
    id = "proxy",
    base = file("fcomb-proxy"),
    settings =
      defaultSettings ++ Seq(
        libraryDependencies             ++= Dependencies.proxy
      )
  ).dependsOn(persist)
}
