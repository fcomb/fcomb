import sbt._

object Dependencies {
  object V {
    val akka = "2.3.10"
    val akkaExperimental = "1.0-RC2"
    val scalaz = "7.1.2"
    val slick = "3.0.0"
  }

  object Compile {
    val akkaActor       = "com.typesafe.akka"             %% "akka-actor"                    % V.akka
    val akkaKernel      = "com.typesafe.akka"             %% "akka-kernel"                   % V.akka
    val akkaStream      = "com.typesafe.akka"             %% "akka-stream-experimental"      % V.akkaExperimental
    val akkaHttpCore    = "com.typesafe.akka"             %% "akka-http-core-experimental"   % V.akkaExperimental;
    val akkaHttp        = "com.typesafe.akka"             %% "akka-http-scala-experimental"  % V.akkaExperimental
    val akkaHttpSpray   = "com.typesafe.akka"             %% "akka-http-spray-json-experimental" % V.akkaExperimental
    val akkaSlf4j       = "com.typesafe.akka"             %% "akka-slf4j"                    % V.akka

    val akkaTracing     = "com.github.levkhomich"         %% "akka-tracing-core"             % "0.5-SNAPSHOT" changing()
    val kamon           = "io.kamon"                      %% "kamon-core"                    % "0.3.5"

    val sprayJson       = "io.spray"                      %% "spray-json"                    % "1.3.1"
    val json4sJackson   = "org.json4s"                    %% "json4s-jackson"                % "3.2.11"
    val upickle         = "com.lihaoyi"                   %% "upickle"                       % "0.2.8"
    val argonaut        = "io.argonaut"                   %% "argonaut"                      % "6.1"

    val pickling        = "org.scala-lang.modules"        %% "scala-pickling"                % "0.10.0"

    val config          = "com.typesafe"                  %  "config"                        % "1.2.1"

    val postgresJdbc    = "org.postgresql"                %  "postgresql"                    % "9.4-1201-jdbc41" exclude("org.slf4j", "slf4j-simple")
    val slick           = "com.typesafe.slick"            %% "slick"                         % V.slick
    val slickPg         = "com.github.tminglei"           %% "slick-pg"                      % "0.9.0"
    val slickJoda       = "com.github.tototoshi"          %% "slick-joda-mapper"             % "2.0.0"
    val flywayCore      = "org.flywaydb"                  %  "flyway-core"                   % "3.2.1"
    val hikariCp        = "com.zaxxer"                    %  "HikariCP"                      % "2.3.6"

    val oauth           = "com.nulab-inc"                 %% "scala-oauth2-core"             % "0.13.1"
    val bcrypt          = "com.github.t3hnar"             %% "scala-bcrypt"                  % "2.4"

    val scalazCore      = "org.scalaz"                    %% "scalaz-core"                   % V.scalaz
    val scalazConcurrent = "org.scalaz"                   %% "scalaz-concurrent"             % V.scalaz
    val shapeless       = "com.chuusai"                   %% "shapeless"                     % "2.2.0-RC4"
    val shapelessScalaz = "org.typelevel"                 %% "shapeless-scalaz"              % "0.3"

    val logbackClassic  = "ch.qos.logback"                %  "logback-classic"               % "1.1.3"
    val scalaLogging    = "com.typesafe.scala-logging"    %% "scala-logging"                 % "3.1.0"

    val jodaTime        = "joda-time"                     %  "joda-time"                     % "2.7"
    val jodaConvert     = "org.joda"                      %  "joda-convert"                  % "1.7"

    val xml             = "org.scala-lang.modules"        %% "scala-xml"                     % "1.0.3"

    val commonsCodec    = "commons-codec"                 %  "commons-codec"                 % "1.10"
  }

  object Test {
    val akkaTestkit     = "com.typesafe.akka"             %% "akka-testkit"                  % V.akka % "test"
    val scalacheck      = "org.scalacheck"                %% "scalacheck"                    % "1.12.2" % "test"
    val specs2          = "org.specs2"                    %% "specs2-core"                   % "2.4.15" % "test"
    val slickTestkit    = "com.typesafe.slick"            %% "slick-testkit"                 % V.slick % "test"
  }

  import Compile._, Test._

  val common = Seq(logbackClassic, scalaLogging, jodaTime, jodaConvert, config, json4sJackson)

  val akka = Seq(akkaSlf4j, akkaActor, akkaKernel, akkaStream, akkaHttp, akkaHttpCore, akkaHttpSpray)

  val root = common ++ akka ++ Seq(
    postgresJdbc, slick, slickJoda, flywayCore, hikariCp,
    sprayJson, upickle,
    commonsCodec,
    scalazCore, scalazConcurrent, shapeless, shapelessScalaz
  )

  val api = common ++ akka ++ Seq(oauth)

  val tests = common ++ Seq(akkaTestkit, scalacheck, specs2, slickTestkit)

  val data = common ++ Seq(xml, scalazCore, shapeless, shapelessScalaz)

  val models = common ++ Seq()

  val persist = common ++ Seq(postgresJdbc, slick, slickJoda, flywayCore, hikariCp, bcrypt)

  val utils = common ++ Seq(scalazCore, scalazConcurrent, shapeless, shapelessScalaz)
}
