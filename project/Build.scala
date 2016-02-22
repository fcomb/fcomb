import sbt._
import sbt.Keys._
import spray.revolver._
import spray.revolver.RevolverPlugin.autoImport.{reStart, reStartArgs}
import com.typesafe.sbt.SbtNativePackager, SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtAspectj, SbtAspectj.AspectjKeys
import play.twirl.sbt._, Import._
import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport.scapegoatVersion
import net.virtualvoid.sbt.graph.DependencyGraphSettings
// import io.ino.sbtpillar.Plugin.PillarKeys._

object Build extends sbt.Build {
  val Organization = "io.fcomb"
  val Version = "0.2.0-SNAPSHOT"
  val ScalaVersion = "2.11.7"

  lazy val buildSettings =
    super.settings ++
      Seq(
        organization := Organization,
        version := Version,
        scalaVersion := ScalaVersion,
        crossPaths := false,
        organizationName := Organization,
        organizationHomepage := Some(url("https://fcomb.io")),
        ivyScala := ivyScala.value.map(_.copy(
          overrideScalaVersion = true
        )),
        scapegoatVersion := "1.1.1"
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
    "-Xms4g",
    "-Xmx4g",
    "-Xss6m",
    "-XX:NewSize=1024m",
    "-XX:+UseNUMA",
    "-XX:+TieredCompilation",
    "-XX:+UseG1GC",
    "-XX:+AlwaysPreTouch",
    "-XX:MaxGCPauseMillis=200",
    "-XX:ParallelGCThreads=20",
    "-XX:ConcGCThreads=5",
    "-XX:InitiatingHeapOccupancyPercent=70",
    "-XX:-UseBiasedLocking"
  )

  val javaVersion = "1.8"

  lazy val defaultSettings =
    buildSettings ++
      DependencyGraphSettings.graphSettings ++
      Seq(
        resolvers ++= Resolvers.seq,
        scalacOptions in Compile ++= Seq(
          "-encoding",
          "UTF-8",
          "-deprecation",
          "-unchecked",
          "-feature",
          "-language:higherKinds"
        ) ++ compilerFlags,
        javaOptions ++= Seq("-Dfile.encoding=UTF-8", "-Dscalac.patmat.analysisBudget=off"),
        javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion, "-Xlint:unchecked", "-Xlint:deprecation"),
        parallelExecution in Test := false,
        fork in Test := true
      )

  lazy val root = Project(
    "server",
    file("."),
    settings =
      defaultSettings ++
      SbtNativePackager.packageArchetype.java_application ++
      SbtAspectj.aspectjSettings ++
      RevolverPlugin.settings ++
        Seq(
          libraryDependencies ++= Dependencies.root,
          reStartArgs :=  Seq("io.fcomb.Main"),
          mainClass in reStart := Some("io.fcomb.Main"),
          autoCompilerPlugins := true,
          scalacOptions in (Compile,doc) := Seq(
            "-groups",
            "-implicits",
            "-diagrams"
          ) ++ compilerFlags,
          executableScriptName := "start",
          javaOptions in reStart <++= AspectjKeys.weaverOptions in SbtAspectj.Aspectj,
          javaOptions in run <++= AspectjKeys.weaverOptions in SbtAspectj.Aspectj,
          javaOptions in Universal ++= javaRunOptions.map { o => s"-J$o" },
          packageName in Universal := "dist",
          mappings in Universal <++= (AspectjKeys.weaver in SbtAspectj.Aspectj).map { path =>
            Seq(path.get -> "aspectj/weaver.jar")
          },
          bashScriptExtraDefines += """addJava "-javaagent:${app_home}/../aspectj/weaver.jar"""",
          scriptClasspath ~= (cp => "../config" +: cp),
          fork in run := true,
          fork in reStart := true
        )
  ).dependsOn(api/*, proxy*/, dockerRegistry)
    .enablePlugins(SbtNativePackager)
    .aggregate(tests)

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
  ).dependsOn(persist, utils, docker, crypto, templates)

  lazy val templates = Project(
    id = "templates",
    base = file("fcomb-templates"),
    settings = defaultSettings ++
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
        libraryDependencies ++= Dependencies.proxy
      )
  ).dependsOn(persist)

  lazy val docker = Project(
    id = "docker",
    base = file("fcomb-docker"),
    settings =
      defaultSettings ++ Seq(
        libraryDependencies ++= Dependencies.docker
      )
  ).dependsOn(json, utils, crypto)

  lazy val dockerRegistry = Project(
    id = "docker-registry",
    base = file("fcomb-docker-registry"),
    settings =
      defaultSettings ++ Seq(
        libraryDependencies ++= Dependencies.dockerRegistry
      )
  ).dependsOn(api)

  lazy val crypto = Project(
    id = "crypto",
    base = file("fcomb-crypto"),
    settings =
      defaultSettings ++ Seq(
        libraryDependencies ++= Dependencies.crypto
      )
  )

  lazy val tests = Project(
    id = "tests",
    base = file("fcomb-tests"),
    settings =
      defaultSettings ++ Seq(
        libraryDependencies ++= Dependencies.tests
      )
  ).dependsOn(api)
}
