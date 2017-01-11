import com.typesafe.sbt.packager.MappingsHelper._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
import de.heikoseeberger.sbtheader.license.Apache2_0

lazy val akkaHttpVersion     = "10.0.1"
lazy val akkaVersion         = "2.4.16"
lazy val bouncyCastleVersion = "1.56"
lazy val catsVersion         = "0.8.1"
lazy val circeVersion        = "0.7.0-M2"
lazy val commonsVersion      = "1.10"
lazy val enumeratumVersion   = "1.5.6"
lazy val guavaVersion        = "20.0"
lazy val slickPgVersion      = "0.15.0-M4_0.7.0-M1"
lazy val slickVersion        = "3.2.0-M2"

lazy val buildSettings = Seq(
  organization := "io.fcomb",
  organizationName := "fcomb",
  description := "Cloud management stack",
  startYear := Option(2016),
  homepage := Option(url("https://fcomb.io")),
  organizationHomepage := Option(new URL("https://fcomb.io")),
  scalaVersion in ThisBuild := "2.12.1",
  headers := Map("scala" -> Apache2_0("2016", "fcomb. <https://fcomb.io>"))
)

lazy val commonSettings =
  // reformatOnCompileSettings ++
  Seq(
    resolvers ++= Seq(
      "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
      Resolver.jcenterRepo,
      Resolver.sonatypeRepo("releases"),
      Resolver.sonatypeRepo("snapshots"),
      Resolver.bintrayRepo("fcomb", "maven"),
      Resolver.bintrayRepo("tek", "maven")
    ),
    libraryDependencies ++= Seq(
      "com.chuusai"                %% "shapeless"     % "2.3.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "org.typelevel"              %% "cats"          % catsVersion
    ),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:higherKinds",
      "-language:existentials",
      "-language:postfixOps",
      "-opt:l:classpath",
      // "-opt-warnings:_",
      "-Xexperimental",
      "-Xlint",
      // "-Xfatal-warnings",
      "-Xfuture",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-Ywarn-infer-any",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-unused-import"
    ),
    addCompilerPlugin("tryp" %% "splain" % "0.1.14"),
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    sources in (Compile, doc) := Seq.empty /*,
      wartremoverWarnings ++= Warts.all */
  )

lazy val publishSettings = Seq(
  homepage := Some(url("https://fcomb.io")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/fcomb/fcomb"), "https://github.com/fcomb/fcomb.git")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val allSettings = buildSettings ++ commonSettings ++ publishSettings

lazy val utils = project
  .in(file("fcomb-utils"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe"      % "config"       % "1.3.1",
      "com.typesafe.akka" %% "akka-stream" % akkaVersion
    ))

lazy val models = crossProject
  .in(file("fcomb-models"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum" % enumeratumVersion
    ))
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.t3hnar" %% "scala-bcrypt" % "3.0"
    ))

lazy val modelsJVM = models.jvm
lazy val modelsJS  = models.js

lazy val rpc = crossProject
  .in(file("fcomb-rpc"))
  .dependsOn(models)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)

lazy val rpcJVM = rpc.jvm
lazy val rpcJS  = rpc.js

lazy val validation = project
  .in(file("fcomb-validation"))
  .dependsOn(modelsJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick"     % slickVersion,
      "org.typelevel"      %% "cats-free" % catsVersion
    ))

lazy val persist = project
  .in(file("fcomb-persist"))
  .dependsOn(modelsJVM, rpcJVM, jsonJVM, utils, validation)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "commons-codec"       % "commons-codec"        % commonsVersion,
    "io.fcomb"            %% "db-migration"        % "0.3.5",
    "org.postgresql"      % "postgresql"           % "9.4.1212" exclude ("org.slf4j", "slf4j-simple"),
    "com.typesafe.akka"   %% "akka-http"           % akkaHttpVersion,
    "com.typesafe.slick"  %% "slick"               % slickVersion,
    "com.typesafe.slick"  %% "slick-hikaricp"      % slickVersion exclude ("com.zaxxer", "HikariCP-java6"),
    "com.github.tminglei" %% "slick-pg"            % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_circe-json" % slickPgVersion,
    "com.zaxxer"          % "HikariCP"             % "2.5.1"
  ))

lazy val json = crossProject
  .in(file("fcomb-json"))
  .dependsOn(models, rpc)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum-circe" % enumeratumVersion,
      "io.circe"     %%% "circe-parser"     % circeVersion,
      "io.circe"     %%% "circe-generic"    % circeVersion
    ))
  .jvmSettings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-java8" % circeVersion,
      "io.circe" %% "circe-jawn"  % circeVersion
    ))

lazy val jsonJVM = json.jvm
lazy val jsonJS  = json.js

lazy val crypto = project
  .in(file("fcomb-crypto"))
  .dependsOn(modelsJVM, jsonJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "commons-codec"     % "commons-codec"  % commonsVersion,
    "org.bouncycastle"  % "bcprov-jdk15on" % bouncyCastleVersion,
    "org.bouncycastle"  % "bcpkix-jdk15on" % bouncyCastleVersion,
    "org.bitbucket.b_c" % "jose4j"         % "0.5.4",
    "io.circe"          %% "circe-parser"  % circeVersion,
    "com.pauldijou"     %% "jwt-circe"     % "0.10.0_0.7.0-M1"
  ))

lazy val services = project
  .in(file("fcomb-services"))
  .dependsOn(persist, utils, crypto)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-distributed-data-experimental" % akkaVersion,
      "com.typesafe.akka" %% "akka-http"                          % akkaHttpVersion
    ))

lazy val server = project
  .in(file("fcomb-server"))
  .dependsOn(persist, utils, jsonJVM, validation, services)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"       % akkaHttpVersion,
      "io.fcomb"          %% "akka-http-circe" % s"${akkaHttpVersion}_$circeVersion"
    ))

lazy val dockerDistribution = project
  .in(file("fcomb-docker-distribution"))
  .dependsOn(server)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.google.guava"  % "guava"                  % guavaVersion
    ))

lazy val runtime = project
  .in(file("fcomb-runtime"))
  .dependsOn(modelsJVM, persist)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.3.1"
    )
  )

lazy val tests = project
  .in(file("fcomb-tests"))
  .dependsOn(server, dockerDistribution)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-testkit"      % akkaVersion % Test,
      "com.typesafe.akka"  %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka"  %% "akka-slf4j"        % akkaVersion,
      "com.lihaoyi"        % "ammonite"           % "0.8.1" % Test cross CrossVersion.full,
      "org.scalacheck"     %% "scalacheck"        % "1.13.4" % Test,
      "org.scalatest"      %% "scalatest"         % "3.0.1" % Test,
      "com.ironcorelabs"   %% "cats-scalatest"    % "2.1.1" % Test,
      "com.typesafe.slick" %% "slick-testkit"     % slickVersion % Test exclude ("junit", "junit-dep"),
      "ch.qos.logback"     % "logback-classic"    % "1.1.8",
      "junit"              % "junit-dep"          % "4.10" % Test
    ),
    initialCommands in (Test, console) := "ammonite.Main().run()",
    parallelExecution in Test := false,
    fork in Test := true
  )

lazy val frontendAssetsDirectory = settingKey[File]("Assets directory path")
lazy val frontendBundleBuild =
  taskKey[Unit]("Build frontend assets through webpack")

lazy val frontend = project
  .in(file("fcomb-frontend"))
  .dependsOn(modelsJS, rpcJS, jsonJS)
  .enablePlugins(AutomateHeaderPlugin, ScalaJSPlugin)
  .settings(allSettings)
  .settings(
    frontendAssetsDirectory := baseDirectory.value / "src" / "main" / "resources" / "public",
    frontendBundleBuild := {
      assert(s"${baseDirectory.value}/build.sh".! == 0, "js build error")
    },
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalacss"                   %%% "ext-react"     % "0.5.1",
      "com.github.japgolly.scalajs-react"              %%% "extra"         % "0.11.3",
      "com.github.chandu0101.scalajs-react-components" %%% "core"          % "0.6.0-SNAPSHOT",
      "org.scala-js"                                   %%% "scalajs-dom"   % "0.9.1",
      "org.typelevel"                                  %%% "cats"          % catsVersion,
      "me.chrons"                                      %%% "diode-react"   % "1.1.0",
      "io.circe"                                       %%% "circe-scalajs" % circeVersion
    ),
    skip in packageJSDependencies := false,
    persistLauncher in Compile := true,
    persistLauncher in Test := false,
    artifactPath in (Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value /
      ((moduleName in fastOptJS).value + "-opt.js")),
    mappings in (Compile, packageBin) ~= { (ms: Seq[(File, String)]) =>
      val exts = Set(".js", ".html", ".ttf", ".woff", ".woff2", ".css", ".gz")
      ms.filter { case (_, p) => p.contains("resources") && exts.exists(p.endsWith) }
    },
    mappings in (Compile, packageBin) ++= {
      (fullOptJS in Compile).value
      (frontendBundleBuild in Compile).value
      directory(frontendAssetsDirectory.value)
    }
  )

lazy val docSettings = Seq(
  micrositeName := "fcomb",
  micrositeDescription := "Alternative to docker trusted registry and quay written in Scala",
  micrositeAuthor := "Timothy Klim",
  micrositeHighlightTheme := "atom-one-light",
  micrositeHomepage := "https://fcomb.io",
  micrositeGithubOwner := "fcomb",
  // micrositeExtraMdFiles := Map(file("CONTRIBUTING.md") -> "contributing.md"),
  micrositeGithubRepo := "fcomb",
  micrositePalette := Map(
    "brand-primary"   -> "#E05236",
    "brand-secondary" -> "#3F3242",
    "brand-tertiary"  -> "#2D232F",
    "gray-dark"       -> "#453E46",
    "gray"            -> "#837F84",
    "gray-light"      -> "#E3E2E3",
    "gray-lighter"    -> "#F4F3F4",
    "white-color"     -> "#FFFFFF"
  ),
  ghpagesNoJekyll := false,
  excludeFilter in cleanSite := "CNAME",
  fork in tut := true,
  git.remoteRepo := "git@github.com:fcomb/fcomb.git",
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.yml" | "*.md"
)

lazy val docs = project
  .in(file("fcomb-docs"))
  .enablePlugins(MicrositesPlugin)
  .settings(allSettings)
  .settings(noPublishSettings)
  .settings(ghpages.settings)
  .settings(docSettings)
  .settings(tutScalacOptions ~= (_.filterNot(Set("-Ywarn-unused-import", "-Ywarn-dead-code"))))

lazy val javaRunOptions = Seq(
  "-server",
  "-Xms1g",
  "-Xmx2g",
  "-Xss6m",
  "-XX:NewSize=256m",
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
  .aggregate(tests)
  .dependsOn(runtime, server, dockerDistribution, frontend)
  .enablePlugins(AutomateHeaderPlugin, JavaAppPackaging)
  .settings(allSettings)
  .settings(noPublishSettings)
  .settings(RevolverPlugin.settings)
  .settings(
    autoCompilerPlugins := true,
    libraryDependencies ++= Seq(
      "ch.qos.logback"    % "logback-classic" % "1.1.8",
      "com.typesafe.akka" %% "akka-http"      % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion
    ),
    aggregate in reStart := false,
    mainClass := Some("io.fcomb.application.Main"),
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
