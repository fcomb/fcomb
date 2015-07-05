import sbt._

object Dependencies {
  object V {
    val akka = "2.4-M2"
    val akkaExperimental = "1.0-RC4"
    val scalaz = "7.1.2"
    val algebra = "0.2.0-SNAPSHOT"
    val cats = "0.1.0-SNAPSHOT"
    val scalikeJdbc = "2.2.6"
    val hazelcast = "3.4.2"
    val kamon = "0.4.0"
  }

  object Compile {
    val akkaActor       = "com.typesafe.akka"             %% "akka-actor"                    % V.akka
    val akkaKernel      = "com.typesafe.akka"             %% "akka-kernel"                   % V.akka
    val akkaStream      = "com.typesafe.akka"             %% "akka-stream-experimental"      % V.akkaExperimental
    val akkaStreamExtensions = "com.mfglabs"              %% "akka-stream-extensions"        % "0.7.3"
    val akkaHttp        = "com.typesafe.akka"             %% "akka-http-experimental"        % V.akkaExperimental
    val akkaSlf4j       = "com.typesafe.akka"             %% "akka-slf4j"                    % V.akka
    val akkaPersistence = "com.typesafe.akka"             %% "akka-persistence-experimental" % V.akka

    val akkaTracing     = "com.github.levkhomich"         %% "akka-tracing-core"             % "0.5-SNAPSHOT" changing()
    val kamonScala      = "io.kamon"                      %% "kamon-scala"                   % V.kamon
    val kamonAkka       = "io.kamon"                      %% "kamon-akka"                    % V.kamon
    val kamonAkkaRemote = "io.kamon"                      %% "kamon-akka-remote"             % V.kamon
    val kamonNewrelic   = "io.kamon"                      %% "kamon-newrelic"                % V.kamon
    val kamonStatsd     = "io.kamon"                      %% "kamon-statsd"                  % V.kamon
    val kamonJdbc       = "io.kamon"                      %% "kamon-jdbc"                    % V.kamon

    val dispatch        = "net.databinder.dispatch"       %% "dispatch-core"                 % "0.11.2"
    val finagle         = "com.github.finagle"            %% "finch-core"                    % "0.7.1"
    val finagleArgonaut = "com.github.finagle"            %% "finch-argonaut"                % "0.7.1"
    val clump           = "io.getclump"                   %% "clump-scala"                   % "0.0.12"

    val json4sJackson   = "org.json4s"                    %% "json4s-jackson"                % "3.2.11"
    val upickle         = "com.lihaoyi"                   %% "upickle"                       % "0.2.8"
    val argonaut        = "io.argonaut"                   %% "argonaut"                      % "6.1"
    val argonautShapeless = "com.github.alexarchambault"  %% "argonaut-shapeless_6.1"        % "0.3.0"

    val pickling        = "org.scala-lang.modules"        %% "scala-pickling"                % "0.10.1"

    val config          = "com.typesafe"                  %  "config"                        % "1.3.0"

    val postgresJdbc    = "org.postgresql"                %  "postgresql"                    % "9.4-1201-jdbc41" exclude("org.slf4j", "slf4j-simple")
    val scalikeJdbc     = "org.scalikejdbc"               %% "scalikejdbc"                   % V.scalikeJdbc
    val scalikeJdbcMacros = "org.scalikejdbc"             %% "scalikejdbc-syntax-support-macro" % V.scalikeJdbc
    val scalikeJdbcAsync = "org.scalikejdbc"              %% "scalikejdbc-async"             % "0.5.+"
    val postgresAsync    = "com.github.mauricio"          %% "postgresql-async"              % "0.2.+"
    val flywayCore      = "org.flywaydb"                  %  "flyway-core"                   % "3.2.1"
    val hikariCp        = "com.zaxxer"                    %  "HikariCP"                      % "2.3.8"

    val levelDb         = "org.iq80.leveldb"              % "leveldb"                        % "0.7"
    val levelDbJni      = "org.fusesource.leveldbjni"     % "leveldbjni-all"                 % "1.8"

    val scredis         = "com.livestream"                %% "scredis"                       % "2.0.7-RC1"

    val oauth           = "com.nulab-inc"                 %% "scala-oauth2-core"             % "0.13.1"
    val bcrypt          = "com.github.t3hnar"             %% "scala-bcrypt"                  % "2.4"
    val nacl4s          = "com.github.emstlk"             %% "nacl4s"                        % "1.0.0" 

    val aws             = "com.github.seratch"            %% "awscala"                       % "0.5.0" // TODO: replace by aws-wrap

    val scalazCore      = "org.scalaz"                    %% "scalaz-core"                   % V.scalaz
    val scalazConcurrent = "org.scalaz"                   %% "scalaz-concurrent"             % V.scalaz
    val shapeless       = "com.chuusai"                   %% "shapeless"                     % "2.2.3"
    val shapelessScalaz = "org.typelevel"                 %% "shapeless-scalaz"              % "0.4"

    val raptureCore     = "com.propensive"                %% "rapture-core"                  % "1.1.0"
    val raptureIo       = "com.propensive"                %% "rapture-io"                    % "0.9.0"

    val algebra         = "org.spire-math"                %% "algebra"                       % V.algebra
    val algebraStd      = "org.spire-math"                %% "algebra-std"                   % V.algebra

    val cats            = "org.spire-math"                %% "cats-core"                     % V.cats
    val catsStd         = "org.spire-math"                %% "cats-std"                      % V.cats

    val logbackClassic  = "ch.qos.logback"                %  "logback-classic"               % "1.1.3"
    val scalaLogging    = "com.typesafe.scala-logging"    %% "scala-logging"                 % "3.1.0"

    val bridj           = "com.nativelibs4java"           %  "bridj"                         % "0.7.0"

    val xml             = "org.scala-lang.modules"        %% "scala-xml"                     % "1.0.3"

    val commonsCodec    = "commons-codec"                 %  "commons-codec"                 % "1.10"

    val ffi             = "com.nativelibs4java"           % "bridj"                          % "0.7.0"
  }

  object Test {
    val akkaTestkit     = "com.typesafe.akka"             %% "akka-testkit"                  % V.akka % "test"
    val scalacheck      = "org.scalacheck"                %% "scalacheck"                    % "1.12.3" % "test"
    val specs2          = "org.specs2"                    %% "specs2-core"                   % "2.4.17" % "test"
  }

  import Compile._, Test._

  val common = Seq(
    logbackClassic, scalaLogging,
    config, json4sJackson, pickling, upickle,
    argonaut, argonautShapeless
  )

  val kamon = Seq(kamonScala/*, kamonNewrelic, kamonStatsd, kamonAkka, kamonAkkaRemote, kamonJdbc*/)

  val akka = Seq(
    akkaSlf4j, akkaActor, akkaKernel, akkaStream, akkaHttp,
    akkaStreamExtensions
  )

  val root = common ++ akka ++ Seq(
    postgresJdbc, flywayCore, hikariCp,
    commonsCodec,
    scalazCore, scalazConcurrent, shapeless, shapelessScalaz
  )

  val api = common ++ akka ++ Seq(oauth)

  val tests = common ++ Seq(akkaTestkit, scalacheck, specs2)

  val data = common ++ Seq(xml, scalazCore, shapeless, shapelessScalaz)

  val models = common ++ Seq(
    bcrypt
  )

  val persist = common ++ Seq(
    postgresJdbc, scalikeJdbc, scalikeJdbcMacros, scalikeJdbcAsync, postgresAsync,
    flywayCore, hikariCp, bcrypt, commonsCodec
  )

  val utils = common ++ Seq(
    scalazCore, scalazConcurrent,
    shapeless, shapelessScalaz,
    scredis
  )

  val macros = common

  val json = common

  val request = common

  val response = common
}
