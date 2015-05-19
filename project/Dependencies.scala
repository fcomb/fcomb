import sbt._

object Dependencies {
  object V {
    val akka = "2.3.11"
    val akkaExperimental = "1.0-RC2"
    val scalaz = "7.1.2"
    val hazelcast = "3.4.2"
    val kamon = "0.4.0"
  }

  object Compile {
    val akkaActor       = "com.typesafe.akka"             %% "akka-actor"                    % V.akka
    val akkaKernel      = "com.typesafe.akka"             %% "akka-kernel"                   % V.akka
    val akkaStream      = "com.typesafe.akka"             %% "akka-stream-experimental"      % V.akkaExperimental
    val akkaStreamExtensions = "com.mfglabs"              %% "akka-stream-extensions"        % "0.7.1"
    val akkaHttpCore    = "com.typesafe.akka"             %% "akka-http-core-experimental"   % V.akkaExperimental
    val akkaHttp        = "com.typesafe.akka"             %% "akka-http-scala-experimental"  % V.akkaExperimental
    val akkaHttpSpray   = "com.typesafe.akka"             %% "akka-http-spray-json-experimental" % V.akkaExperimental
    val akkaSlf4j       = "com.typesafe.akka"             %% "akka-slf4j"                    % V.akka

    val akkaTracing     = "com.github.levkhomich"         %% "akka-tracing-core"             % "0.5-SNAPSHOT" changing()
    val kamonScala      = "io.kamon"                      %% "kamon-scala"                   % V.kamon
    val kamonAkka       = "io.kamon"                      %% "kamon-akka"                    % V.kamon
    val kamonAkkaRemote = "io.kamon"                      %% "kamon-akka-remote"             % V.kamon
    val kamonNewrelic   = "io.kamon"                      %% "kamon-newrelic"                % V.kamon
    val kamonStatsd     = "io.kamon"                      %% "kamon-statsd"                  % V.kamon
    val kamonJdbc       = "io.kamon"                      %% "kamon-jdbc"                    % V.kamon

    val dispatch        = "net.databinder.dispatch"       %% "dispatch-core"                 % "0.11.2"
    val clump           = "io.getclump"                   %% "clump-scala"                   % "0.0.12"

    val sprayJson       = "io.spray"                      %% "spray-json"                    % "1.3.2"
    val json4sJackson   = "org.json4s"                    %% "json4s-jackson"                % "3.2.11"
    val upickle         = "com.lihaoyi"                   %% "upickle"                       % "0.2.8"
    val argonaut        = "io.argonaut"                   %% "argonaut"                      % "6.1"

    val pickling        = "org.scala-lang.modules"        %% "scala-pickling"                % "0.10.0"

    val config          = "com.typesafe"                  %  "config"                        % "1.3.0"

    val postgresJdbc    = "org.postgresql"                %  "postgresql"                    % "9.4-1201-jdbc41" exclude("org.slf4j", "slf4j-simple")
    val scalikeJdbc     = "org.scalikejdbc"               %% "scalikejdbc"                   % "2.2.6"
    val scalikeJdbcAsync = "org.scalikejdbc"              %% "scalikejdbc-async"             % "0.5.+"
    val postgresAsync    = "com.github.mauricio"          %% "postgresql-async"              % "0.2.+"
    val flywayCore      = "org.flywaydb"                  %  "flyway-core"                   % "3.1"
    val hikariCp        = "com.zaxxer"                    %  "HikariCP"                      % "2.3.7"

    val scredis         = "com.livestream"                %% "scredis"                       % "2.0.7-RC1"

    val oauth           = "com.nulab-inc"                 %% "scala-oauth2-core"             % "0.13.1"
    val bcrypt          = "com.github.t3hnar"             %% "scala-bcrypt"                  % "2.4"

    val aws             = "com.github.seratch"            %% "awscala"                       % "0.5.0" // TODO: replace by aws-wrap

    val scalazCore      = "org.scalaz"                    %% "scalaz-core"                   % V.scalaz
    val scalazConcurrent = "org.scalaz"                   %% "scalaz-concurrent"             % V.scalaz
    val shapeless       = "com.chuusai"                   %% "shapeless"                     % "2.2.0-RC6"
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
  }

  import Compile._, Test._

  val common = Seq(
    logbackClassic, scalaLogging, jodaTime, jodaConvert,
    config, json4sJackson, pickling, argonaut,
    sprayJson
  )

  val kamon = Seq(kamonScala, kamonNewrelic, /*kamonStatsd,*/ kamonAkka, kamonAkkaRemote, kamonJdbc)

  val akka = Seq(
    akkaSlf4j, akkaActor, akkaKernel, akkaStream, akkaHttp,
    akkaHttpCore, akkaHttpSpray, akkaStreamExtensions
  )

  val root = common ++ akka ++ Seq(
    postgresJdbc, flywayCore, hikariCp,
    sprayJson, upickle,
    commonsCodec,
    scalazCore, scalazConcurrent, shapeless, shapelessScalaz
  )

  val api = common ++ akka ++ Seq(oauth)

  val tests = common ++ Seq(akkaTestkit, scalacheck, specs2)

  val data = common ++ Seq(xml, scalazCore, shapeless, shapelessScalaz)

  val models = common ++ Seq()

  val persist = common ++ Seq(postgresJdbc, flywayCore, hikariCp, bcrypt)

  val utils = common ++ Seq(scalazCore, scalazConcurrent, shapeless, shapelessScalaz)
}
