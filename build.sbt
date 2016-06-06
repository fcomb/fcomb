import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import de.heikoseeberger.sbtheader.license.Apache2_0
import scala.concurrent.duration._

lazy val buildSettings = Seq(
  organization := "io.fcomb",
  organizationName := "fcomb",
  description := "Cloud management stack",
  startYear := Option(2016),
  homepage := Option(url("https://fcomb.io")),
  organizationHomepage := Option(new URL("https://fcomb.io")),
  scalaVersion := "2.11.8",
  headers := Map(
    "scala" -> Apache2_0("2016", "fcomb. <https://fcomb.io>")
  ),
  scalafmtConfig := Some(file(".scalafmt"))
)

val akkaVersion = "2.4.7"
val akkaHttpCirceVersion = "1.6.0"
val bouncyCastleVersion = "1.53"
val catsVersion = "0.6.0"
val commonsVersion = "1.10"
val circeVersion = "0.5.0-M1"
val enumeratumVersion = "1.4.4"
val guavaVersion = "19.0"
val slickVersion = "3.1.1"
val slickPgVersion = "0.14.1"

val jdkVersion = "1.8"

lazy val commonSettings =
  reformatOnCompileSettings ++
    Seq(
      resolvers ++= Seq(
        Resolver.bintrayRepo("fcomb", "maven"),
        Resolver.bintrayRepo("etaty", "maven")
      ),
      libraryDependencies ++= Seq(
        "com.chuusai"   %% "shapeless" % "2.3.1",
        "org.typelevel" %% "cats"      % catsVersion
      ),
      scalacOptions ++= Seq(
        "-encoding",
        "UTF-8",
        "-unchecked",
        "-deprecation",
        "-feature",
        "-language:higherKinds",
        "-language:existentials",
        "-language:postfixOps",
        "-Xexperimental",
        "-Yopt:l:classpath",
        "-Yopt:unreachable-code",
        "-Ywarn-dead-code",
        "-Ywarn-infer-any",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused",
        "-Ywarn-unused-import" /*,
        "-Xfatal-warnings" */
      ),
      javacOptions ++= Seq(
        "-source",
        jdkVersion,
        "-target",
        jdkVersion,
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in packageDoc            := false,
      sources         in (Compile, doc)        := Seq.empty
    )

lazy val publishSettings = Seq(
  homepage := Some(url("https://fcomb.io")),
  scmInfo := Some(ScmInfo(url("https://github.com/fcomb/fcomb"), "https://github.com/fcomb/fcomb.git")),
  licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val allSettings = buildSettings ++ commonSettings ++ publishSettings

lazy val utils = project.in(file("fcomb-utils"))
  .settings(moduleName := "utils")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe"      %  "config"      % "1.3.0",
    "com.github.kxbmap" %% "configs"     % "0.4.2",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)

lazy val models = project.in(file("fcomb-models"))
  .settings(moduleName := "models")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.beachape"      %% "enumeratum"   % enumeratumVersion,
    "com.github.t3hnar" %% "scala-bcrypt" % "2.4"
  ))
  .enablePlugins(AutomateHeaderPlugin)

lazy val validations = project.in(file("fcomb-validations"))
  .settings(moduleName := "validations")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.slick" %% "slick" % slickVersion // TODO: move DBIO validation into persist module
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models)

lazy val persist = project.in(file("fcomb-persist"))
  .settings(moduleName := "persist")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "commons-codec"       %  "commons-codec"       % commonsVersion,
    "io.fcomb"            %% "db-migration"        % "0.3.1",
    "org.postgresql"      %  "postgresql"          % "9.4-1201-jdbc41" exclude("org.slf4j", "slf4j-simple"),
    "com.typesafe.akka"   %% "akka-http-core"      % akkaVersion,
    "com.typesafe.slick"  %% "slick"               % slickVersion,
    "com.typesafe.slick"  %% "slick-hikaricp"      % slickVersion,
    "com.github.tminglei" %% "slick-pg"            % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_date2"      % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_circe-json" % slickPgVersion,
    "com.zaxxer"          %  "HikariCP"            % "2.4.5",
    "com.etaty.rediscala" %% "rediscala"           % "1.5.0" // TODO: replace it by akka persistence
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models, utils, validations)

lazy val json = project.in(file("fcomb-json"))
  .settings(moduleName := "json")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.beachape" %% "enumeratum-circe" % enumeratumVersion,
    "io.circe"     %% "circe-generic"    % circeVersion,
    "io.circe"     %% "circe-parser"     % circeVersion,
    "io.circe"     %% "circe-java8"      % circeVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models)

lazy val crypto = project.in(file("fcomb-crypto"))
  .settings(moduleName := "crypto")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "commons-codec"     %  "commons-codec"  % commonsVersion,
    "org.bouncycastle"  %  "bcprov-jdk15on" % bouncyCastleVersion,
    "org.bouncycastle"  %  "bcpkix-jdk15on" % bouncyCastleVersion,
    "org.bitbucket.b_c" %  "jose4j"         % "0.5.0"
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models)

lazy val templates = project.in(file("fcomb-templates"))
  .settings(moduleName := "templates")
  .settings(allSettings:_*)
  .settings(TwirlKeys.templateImports += "io.fcomb.templates._, io.fcomb.utils._")
  .enablePlugins(AutomateHeaderPlugin, SbtTwirl)
  .dependsOn(utils)

lazy val services = project.in(file("fcomb-services"))
  .settings(moduleName := "services")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core"                     % akkaVersion,
    "de.heikoseeberger" %% "akka-http-circe"                    % akkaHttpCirceVersion,
    "io.circe"          %% "circe-generic"                      % circeVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(persist, utils, crypto, templates)

lazy val server = project.in(file("fcomb-server"))
  .settings(moduleName := "server")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(persist, utils, json, validations, services)

lazy val dockerDistribution = project.in(file("fcomb-docker-distribution"))
  .settings(moduleName := "docker-distribution")
  .settings(allSettings:_*)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.google.guava"  %  "guava"                 % guavaVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(server)

lazy val tests = project.in(file("fcomb-tests"))
  .settings(moduleName := "tests")
  .settings(allSettings:_*)
  .settings(
    libraryDependencies       ++= Seq(
      "com.typesafe.akka"  %% "akka-testkit"      % akkaVersion % "test",
      "com.typesafe.akka"  %% "akka-http-testkit" % akkaVersion % "test",
      "com.typesafe.akka"  %% "akka-slf4j"        % akkaVersion,
      "org.scalacheck"     %% "scalacheck"        % "1.13.1" % "test",
      "org.specs2"         %% "specs2-core"       % "3.8.3" % "test",
      "org.scalatest"      %% "scalatest"         % "3.0.0-RC1" % "test",
      "com.ironcorelabs"   %% "cats-scalatest"    % "1.3.0" % "test",
      "com.typesafe.slick" %% "slick-testkit"     % slickVersion % "test",
      "ch.qos.logback"     %  "logback-classic"   % "1.1.7"
    ),
    parallelExecution in Test := false,
    fork in Test              := true
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(server, dockerDistribution)

lazy val frontendBundle = taskKey[File]("bundle.js path")
lazy val frontendBundleBuild = taskKey[Unit]("Build frontend bundle.js")
lazy val frontendBundleCreate = taskKey[Unit]("Create frontend bundle.js if it does not exist")

lazy val frontend = project.in(file("fcomb-frontend"))
  .settings(moduleName := "frontend")
  .settings(allSettings:_*)
  .settings(
    frontendBundle := baseDirectory.value / "src" / "main" / "resources" / "bundle.js",
    frontendBundleBuild := {
      (JsEngineKeys.npmNodeModules in Assets).value
      val in = (baseDirectory.value / "lib.js").getAbsolutePath
      val out = frontendBundle.value
      out.getParentFile.mkdirs()
      val nodeModules = (baseDirectory.value / "node_modules").getAbsolutePath
      SbtJsTask.executeJs(state.value, JsEngineKeys.engineType.value, None, Seq(nodeModules),
        baseDirectory.value / "browserify.js", Seq(in, out.getAbsolutePath), 30.seconds)
    },
    frontendBundleCreate := {
      Def.taskDyn {
        if (frontendBundle.value.exists) Def.task {}
        else frontendBundleBuild
      }.value
    },
    compile in Compile <<= (compile in Compile).dependsOn(frontendBundleCreate),
    libraryDependencies           ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra" % "0.11.1"
    ),
    jsDependencies                += ProvidedJS / "bundle.js",
    scalaJSUseRhino in Global     := false,
    skip in packageJSDependencies := false,
    persistLauncher in Compile    := true,
    persistLauncher in Test       := false
  )
  .enablePlugins(AutomateHeaderPlugin, ScalaJSPlugin, SbtWeb, SbtJsEngine)

lazy val root = project.in(file("."))
  .aggregate(tests)
  .dependsOn(server, dockerDistribution, frontend)
  .settings(allSettings:_*)
  .settings(noPublishSettings)
  .settings(RevolverPlugin.settings)
  .settings(SbtNativePackager.packageArchetype.java_application)
  .settings(
    autoCompilerPlugins :=  true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion,
      "ch.qos.logback"    %  "logback-classic"        % "1.1.7"
    ),
    aggregate in reStart       :=  false,
    mainClass in reStart       :=  Some("io.fcomb.application.Main"),
    mainClass in Compile       :=  Some("io.fcomb.application.Main"),
    executableScriptName       :=  "start",
    javaOptions in Universal   ++= javaRunOptions.map(o => s"-J$o"),
    packageName in Universal   :=  "dist",
    scriptClasspath            ~=  (cp => "../config" +: cp),
    javaOptions in (Test, run) ++= javaRunOptions,
    parallelExecution          :=  true,
    fork in run                :=  true,
    fork in reStart            :=  true
  )
  .enablePlugins(SbtNativePackager)

lazy val javaRunOptions = Seq(
  "-server",
  "-Xms2g",
  "-Xmx2g",
  "-Xss6m",
  "-XX:NewSize=512m",
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
