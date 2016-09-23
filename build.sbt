import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import de.heikoseeberger.sbtheader.license.Apache2_0

lazy val akkaVersion         = "2.4.10"
lazy val bouncyCastleVersion = "1.55"
lazy val catsVersion         = "0.7.2"
lazy val circeVersion        = "0.5.2"
lazy val commonsVersion      = "1.10"
lazy val enumeratumVersion   = "1.4.14"
lazy val guavaVersion        = "19.0"
lazy val slickPgVersion      = "0.15.0-M2"
lazy val slickVersion        = "3.2.0-M1"

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

lazy val commonSettings =
  reformatOnCompileSettings ++
    Seq(
      resolvers ++= Seq(
        "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
        Resolver.jcenterRepo,
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots"),
        Resolver.bintrayRepo("fcomb", "maven"),
        Resolver.bintrayRepo("etaty", "maven")
      ),
      libraryDependencies ++= Seq(
        "com.chuusai"                %% "shapeless"     % "2.3.2",
        "org.typelevel"              %% "cats"          % catsVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
      ),
      scalacOptions ++= Seq(
        "-encoding",
        "UTF-8",
        "-target:jvm-1.8",
        "-unchecked",
        "-deprecation",
        "-feature",
        "-language:higherKinds",
        "-language:existentials",
        "-language:postfixOps",
        "-Xexperimental",
        "-Xlint",
        // "-Xfatal-warnings",
        "-Xfuture",
        "-Ybackend:GenBCode",
        "-Ydelambdafy:method",
        "-Yno-adapted-args",
        "-Yopt-warnings",
        "-Yopt:l:classpath",
        "-Yopt:unreachable-code",
        "-Ywarn-dead-code",
        "-Ywarn-infer-any",
        "-Ywarn-numeric-widen",
        "-Ywarn-unused",
        "-Ywarn-unused-import",
        "-Ywarn-value-discard"
      ),
      javaOptions ++= Seq("-Dfile.encoding=UTF-8"),
      javacOptions ++= Seq(
        "-source",
        "1.8",
        "-target",
        "1.8",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in packageDoc := false,
      sources in (Compile, doc) := Seq.empty /*,
      wartremoverWarnings ++= Warts.all */
    )

lazy val publishSettings = Seq(
  homepage := Some(url("https://fcomb.io")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/fcomb/fcomb"), "https://github.com/fcomb/fcomb.git")),
  licenses := Seq(
    "Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val allSettings = buildSettings ++ commonSettings ++ publishSettings

lazy val utils = project
  .in(file("fcomb-utils"))
  .settings(moduleName := "utils")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe"      % "config"       % "1.3.0",
      "com.github.kxbmap" %% "configs"     % "0.4.2",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    ))
  .enablePlugins(AutomateHeaderPlugin)

lazy val models = crossProject
  .in(file("fcomb-models"))
  .settings(moduleName := "models")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum" % enumeratumVersion
    ))
  .jvmSettings(libraryDependencies ++= Seq(
    "com.github.t3hnar" %% "scala-bcrypt" % "2.6"
  ))
  .enablePlugins(AutomateHeaderPlugin)

lazy val modelsJVM = models.jvm
lazy val modelsJS  = models.js

lazy val rpc = crossProject
  .in(file("fcomb-rpc"))
  .settings(moduleName := "rpc")
  .settings(allSettings: _*)
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models)

lazy val rpcJVM = rpc.jvm
lazy val rpcJS  = rpc.js

lazy val validation = project
  .in(file("fcomb-validation"))
  .settings(moduleName := "validation")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick"     % slickVersion,
      "org.typelevel"      %% "cats-free" % catsVersion
    ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(modelsJVM)

lazy val persist = project
  .in(file("fcomb-persist"))
  .settings(moduleName := "persist")
  .settings(allSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "commons-codec"       % "commons-codec"           % commonsVersion,
    "io.fcomb"            %% "db-migration"           % "0.3.1",
    "org.postgresql"      % "postgresql"              % "9.4.1211" exclude ("org.slf4j", "slf4j-simple"),
    "com.typesafe.akka"   %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.slick"  %% "slick"                  % slickVersion,
    "com.typesafe.slick"  %% "slick-hikaricp"         % slickVersion exclude ("com.zaxxer", "HikariCP-java6"),
    "com.github.tminglei" %% "slick-pg"               % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_date2"         % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_circe-json"    % slickPgVersion,
    "com.zaxxer"          % "HikariCP"                % "2.5.1"
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(modelsJVM, rpcJVM, jsonJVM, utils, validation)

lazy val json = crossProject
  .in(file("fcomb-json"))
  .settings(moduleName := "json")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum-circe" % enumeratumVersion,
      "io.circe" %%% "circe-parser"         % circeVersion,
      "io.circe" %%% "circe-generic"        % circeVersion
    ))
  .jvmSettings(libraryDependencies ++= Seq(
    "io.circe" %% "circe-java8" % circeVersion
  ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(models, rpc)

lazy val jsonJVM = json.jvm
lazy val jsonJS  = json.js

lazy val crypto = project
  .in(file("fcomb-crypto"))
  .settings(moduleName := "crypto")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "commons-codec"     % "commons-codec"  % commonsVersion,
      "org.bouncycastle"  % "bcprov-jdk15on" % bouncyCastleVersion,
      "org.bouncycastle"  % "bcpkix-jdk15on" % bouncyCastleVersion,
      "org.bitbucket.b_c" % "jose4j"         % "0.5.2",
      "io.circe"          %% "circe-parser"  % circeVersion,
      "com.pauldijou"     %% "jwt-circe"     % "0.8.1"
    ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(modelsJVM, jsonJVM)

lazy val templates = project
  .in(file("fcomb-templates"))
  .settings(moduleName := "templates")
  .settings(allSettings: _*)
  .settings(TwirlKeys.templateImports += "io.fcomb.templates._, io.fcomb.utils._")
  .enablePlugins(AutomateHeaderPlugin, SbtTwirl)
  .dependsOn(utils)

lazy val services = project
  .in(file("fcomb-services"))
  .settings(moduleName := "services")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-distributed-data-experimental" % akkaVersion,
      "com.typesafe.akka"  %% "akka-http-core"                     % akkaVersion,
      "de.heikoseeberger"  %% "akka-http-circe"                    % "1.10.0",
      "org.apache.commons" % "commons-email"                       % "1.4"
    ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(persist, utils, crypto, templates)

lazy val server = project
  .in(file("fcomb-server"))
  .settings(moduleName := "server")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion
    ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(persist, utils, jsonJVM, validation, services)

lazy val dockerDistribution = project
  .in(file("fcomb-docker-distribution"))
  .settings(moduleName := "docker-distribution")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.google.guava"  % "guava"                  % guavaVersion
    ))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(server)

lazy val tests = project
  .in(file("fcomb-tests"))
  .settings(moduleName := "tests")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-testkit"      % akkaVersion % "test",
      "com.typesafe.akka"  %% "akka-http-testkit" % akkaVersion % "test",
      "com.typesafe.akka"  %% "akka-slf4j"        % akkaVersion,
      "org.scalacheck"     %% "scalacheck"        % "1.13.2" % "test",
      "org.specs2"         %% "specs2-core"       % "3.8.5" % "test",
      "org.scalatest"      %% "scalatest"         % "3.0.0" % "test",
      "com.ironcorelabs"   %% "cats-scalatest"    % "1.4.0" % "test",
      "com.typesafe.slick" %% "slick-testkit"     % slickVersion % "test" exclude ("junit", "junit-dep"),
      "ch.qos.logback"     % "logback-classic"    % "1.1.7",
      "junit"              % "junit-dep"          % "4.10" % "test"
    ),
    parallelExecution in Test := false,
    fork in Test := true
  )
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(server, dockerDistribution)

lazy val frontendAssetsDirectory = settingKey[File]("Assets directory path")
lazy val frontendBundleBuild     = taskKey[Unit]("Build frontend assets through webpack")

lazy val frontend = project
  .in(file("fcomb-frontend"))
  .settings(moduleName := "frontend")
  .settings(allSettings: _*)
  .settings(
    frontendAssetsDirectory := baseDirectory.value / "src" / "main" / "resources" / "public",
    frontendBundleBuild := {
      fullOptJS
      assert(s"${baseDirectory.value}/build.sh".! == 0, "js build error")
    },
    packageBin in Compile <<= (packageBin in Compile).dependsOn(frontendBundleBuild),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalacss" %%% "ext-react"              % "0.5.0",
      "com.github.japgolly.scalajs-react" %%% "extra"             % "0.11.1",
      "com.github.chandu0101.scalajs-react-components" %%% "core" % "0.5.0",
      "org.scala-js" %%% "scalajs-dom"                            % "0.9.1",
      "org.typelevel" %%% "cats"                                  % catsVersion,
      "me.chrons" %%% "diode-react"                               % "1.0.0",
      "io.circe" %%% "circe-scalajs"                              % circeVersion
    ),
    scalaJSUseRhino in Global := false,
    skip in packageJSDependencies := false,
    persistLauncher in Compile := true,
    persistLauncher in Test := false,
    crossTarget in (Compile, fullOptJS) := frontendAssetsDirectory.value,
    crossTarget in (Compile, fastOptJS) := frontendAssetsDirectory.value,
    artifactPath in (Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value /
      ((moduleName in fastOptJS).value + "-opt.js")),
    mappings in (Compile, packageBin) ~= { (ms: Seq[(File, String)]) =>
      val exts = Set(".js", ".html", ".ttf", ".woff", ".woff2")
      ms.filter { case (_, p) => exts.exists(p.endsWith) }
    }
  )
  .enablePlugins(AutomateHeaderPlugin, ScalaJSPlugin)
  .dependsOn(modelsJS, rpcJS, jsonJS)

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
  "-XX:-UseBiasedLocking",
  "-XX:ReservedCodeCacheSize=256m"
)

lazy val root = project
  .in(file("."))
  .settings(allSettings: _*)
  .settings(noPublishSettings)
  .settings(RevolverPlugin.settings)
  .settings(SbtNativePackager.packageArchetype.java_application)
  .settings(
    autoCompilerPlugins := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
      "com.lihaoyi"       % "ammonite-repl"   % "0.7.7" % "test" cross CrossVersion.full,
      "ch.qos.logback"    % "logback-classic" % "1.1.7"
    ),
    initialCommands in (Test, console) := """ammonite.repl.Main().run()""",
    aggregate in reStart := false,
    mainClass in reStart := Some("io.fcomb.application.Main"),
    mainClass in Compile := Some("io.fcomb.application.Main"),
    executableScriptName := "start",
    javaOptions in Universal ++= javaRunOptions.map(o => s"-J$o"),
    packageName in Universal := "dist",
    scriptClasspath ~= (cp => "../config" +: cp),
    javaOptions in (Test, run) ++= javaRunOptions,
    mappings in Universal ~= (_.filterNot(_._2.contains("sjs"))),
    parallelExecution := true,
    fork in run := true,
    fork in reStart := true
  )
  .aggregate(tests)
  .dependsOn(server, dockerDistribution, frontend)
  .enablePlugins(SbtNativePackager)
