import sbt._

object Dependencies {
  object V {
    val akka = "2.4.6"
    val bouncyCastle = "1.53"
    val cats = "0.6.0"
    val circe = "0.5.0-SNAPSHOT"
    val enumeratum = "1.4.4"
    val slick = "3.1.1"
    val slickPg = "0.14.0"
    val quill = "0.6.0"
    val monocle = "1.2.1"
    val kamon = "0.6.1"
  }

  object Compile {
    val routeTrie       = "io.fcomb"                      %% "route-trie"                    % "0.4.0"
    val dbMigration     = "io.fcomb"                      %% "db-migration"                  % "0.3.1"

    val akkaActor       = "com.typesafe.akka"             %% "akka-actor"                    % V.akka
    val akkaClusterSharding = "com.typesafe.akka"         %% "akka-cluster-sharding"         % V.akka
    val akkaDistributedData = "com.typesafe.akka"         %% "akka-distributed-data-experimental" % V.akka
    val akkaContrib     = "com.typesafe.akka"             %% "akka-contrib"                  % V.akka
    val akkaStream      = "com.typesafe.akka"             %% "akka-stream"                   % V.akka
    val akkaHttpCore    = "com.typesafe.akka"             %% "akka-http-core"                % V.akka
    val akkaHttp        = "com.typesafe.akka"             %% "akka-http-experimental"        % V.akka
    val akkaHttpSwagger = "com.github.swagger-akka-http"  %% "swagger-akka-http"             % "0.6.2"
    val akkaHttpSprayJson = "com.typesafe.akka"           %% "akka-http-spray-json-experimental" % V.akka
    val akkaHttpCirceJson = "de.heikoseeberger"           %% "akka-http-circe"               % "1.6.0"
    val akkaSlf4j       = "com.typesafe.akka"             %% "akka-slf4j"                    % V.akka
    val akkaPersistence = "com.typesafe.akka"             %% "akka-persistence"              % V.akka
    val akkaPersistenceJdbc = "com.github.dnvriend"       %% "akka-persistence-jdbc"         % "2.2.8"
    // val akkaPersistenceCassandra = "com.github.krasserm"  %% "akka-persistence-cassandra"    % "0.5-SNAPSHOT" changing()
    // val akkaKryo        = "com.github.romix.akka"         %% "akka-kryo-serialization"       % "0.3.3"
    val akkaSse         = "de.heikoseeberger"             %% "akka-sse"                      % "1.6.1"

    val atmos           = "io.zman"                       %% "atmos"                         % "2.1"

    val dns4s           = "com.github.mkroli"             %% "dns4s-akka"                    % "0.8"

    val akkaTracing     = "com.github.levkhomich"         %% "akka-tracing-core"             % "0.5-SNAPSHOT" changing()
    val kamonScala      = "io.kamon"                      %% "kamon-scala"                   % V.kamon
    val kamonAkka       = "io.kamon"                      %% "kamon-akka"                    % V.kamon
    val kamonAkkaRemote = "io.kamon"                      %% "kamon-akka-remote"             % V.kamon
    val kamonStatsd     = "io.kamon"                      %% "kamon-statsd"                  % V.kamon
    val kamonJdbc       = "io.kamon"                      %% "kamon-jdbc"                    % V.kamon
    val kamonSystemMetrics = "io.kamon"                   %% "kamon-system-metrics"          % V.kamon
    val kamonLogReporter = "io.kamon"                     %% "kamon-log-reporter"            % V.kamon

    val javaCompat      = "org.scala-lang.modules"        %% "scala-java8-compat"            % "0.8.0-RC1"

    val clump           = "io.getclump"                   %% "clump-scala"                   % "0.0.12"

    val enumeration     = "com.beachape"                  %% "enumeratum"                    % V.enumeratum
    val enumerationCirce = "com.beachape"                 %% "enumeratum-circe"              % V.enumeratum

    // "com.github.romix.akka" %% "akka-kryo-serialization" % "0.3.3"

    // val upickle         = "com.lihaoyi"                   %% "upickle"                       % "0.3.5"
    val sprayJson       = "io.spray"                      %% "spray-json"                    % "1.3.2"
    val circeCore       = "io.circe"                      %% "circe-core"                    % V.circe
    val circeParser     = "io.circe"                      %% "circe-parser"                  % V.circe
    val circeGeneric    = "io.circe"                      %% "circe-generic"                 % V.circe
    val circeJawn       = "io.circe"                      %% "circe-jawn"                    % V.circe
    val circeJava8      = "io.circe"                      %% "circe-java8"                   % V.circe
    val circeOptics     = "io.circe"                      %% "circe-optics"                  % V.circe

    val pickling        = "org.scala-lang.modules"        %% "scala-pickling"                % "0.10.1"

    val googleLru       = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
    val caffeine        = "com.github.ben-manes.caffeine" % "caffeine"                       % "1.3.3"

    val config          = "com.typesafe"                  %  "config"                        % "1.3.0"
    val configs         = "com.github.kxbmap"             %% "configs"                       % "0.4.2"

    val postgresJdbc    = "org.postgresql"                %  "postgresql"                    % "9.4-1201-jdbc41" exclude("org.slf4j", "slf4j-simple")
    // val quillJdbc       = "io.getquill"                   %% "quill-jdbc"                    % V.quill
    // val quillAsync      = "io.getquill"                   %% "quill-async"                   % V.quill
    val slick           = "com.typesafe.slick"            %% "slick"                         % V.slick
    val slickHikariCp   = "com.typesafe.slick"            %% "slick-hikaricp"                % V.slick
    val slickPg         = "com.github.tminglei"           %% "slick-pg"                      % V.slickPg
    val slickPgDate2    = "com.github.tminglei"           %% "slick-pg_date2"                % V.slickPg
    val slickPgCirceJson = "com.github.tminglei"          %% "slick-pg_circe-json"           % V.slickPg
    val hikariCp        = "com.zaxxer"                    %  "HikariCP"                      % "2.4.5"

    // val phantom         = "com.websudos"                  %% "phantom-dsl"                   % V.phantom
    // val phantomUdt      = "com.websudos"                  %% "phantom-udt"                   % V.phantom

    val redis           = "com.etaty.rediscala"           %% "rediscala"                     % "1.5.0"

    val bcProvider      = "org.bouncycastle"              %  "bcprov-jdk15on"                % V.bouncyCastle
    val bcPkix          = "org.bouncycastle"              %  "bcpkix-jdk15on"                % V.bouncyCastle

    val jose4s          = "org.bitbucket.b_c"             %  "jose4j"                        % "0.5.0"
    val oauth           = "com.nulab-inc"                 %% "scala-oauth2-core"             % "0.13.1"
    val bcrypt          = "com.github.t3hnar"             %% "scala-bcrypt"                  % "2.4"

    val s3              = "com.amazonaws"                 %  "aws-java-sdk-s3"               % "1.10.14"
    val awsWrap         = "com.github.dwhjames"           %% "aws-wrap"                      % "0.7.2"

    val shapeless       = "com.chuusai"                   %% "shapeless"                     % "2.3.1"
    val cats            = "org.typelevel"                 %% "cats"                          % V.cats
    val dogs            = "org.typelevel"                 %% "dogs-core"                     % "0.2.2"
    val kittens         = "com.milessabin"                %% "kittens"                       % "1.0.0-M1"

    // val raptureCore     = "com.propensive"                %% "rapture-core"                  % "1.1.0"
    // val raptureIo       = "com.propensive"                %% "rapture-io"                    % "0.9.0"

    val monocleCore     = "com.github.julien-truffaut"    %%  "monocle-core"                 % V.monocle
    val monocleGeneric  = "com.github.julien-truffaut"    %%  "monocle-generic"              % V.monocle
    val monocleMacro    = "com.github.julien-truffaut"    %%  "monocle-macro"                % V.monocle
    val monocleState    = "com.github.julien-truffaut"    %%  "monocle-state"                % V.monocle

    val logbackClassic  = "ch.qos.logback"                %  "logback-classic"               % "1.1.7"
    val scalaLogging    = "com.typesafe.scala-logging"    %% "scala-logging"                 % "3.4.0"

    val pprint          = "com.lihaoyi"                   %% "pprint"                        % "0.4.0"

    val bridj           = "com.nativelibs4java"           %  "bridj"                         % "0.7.0"

    val xml             = "org.scala-lang.modules"        %% "scala-xml"                     % "1.0.3"

    val commonsCodec    = "commons-codec"                 %  "commons-codec"                 % "1.10"

    val ffi             = "com.nativelibs4java"           %  "bridj"                         % "0.7.0"

    val betterFiles     = "com.github.pathikrit"          %% "better-files"                  % "1.0.0"

    val objectsize      = "com.twitter.common"            %  "objectsize"                    % "0.0.10"
    val guava           = "com.google.guava"              %  "guava"                         % "18.0"

    val lz4             = "net.jpountz.lz4"               %  "lz4"                           % "1.3.0"

    // val jamm            = "com.github.jbellis"            % "jamm"                           % "0.3.1"
    // "com.github.jnr" % "jnr-unixsocket" % "0.10"

    val scalaMeter      = "com.storm-enroute"             %% "scalameter"                    % "0.7"
  }

  object Test {
    val akkaTestkit     = "com.typesafe.akka"             %% "akka-testkit"                  % V.akka % "test"
    val akkaHttpTestkit = "com.typesafe.akka"             %% "akka-http-testkit"             % V.akka % "test"
    val scalacheck      = "org.scalacheck"                %% "scalacheck"                    % "1.13.1" % "test"
    val specs2          = "org.specs2"                    %% "specs2-core"                   % "3.8.3" % "test"
    val scalatest       = "org.scalatest"                 %% "scalatest"                     % "2.2.6" % "test"
    val slickTestkit    = "com.typesafe.slick"            %% "slick-testkit"                 % V.slick % "test"
  }

  import Compile._, Test._

  val common = Seq(
    javaCompat,
    logbackClassic, scalaLogging,
    config, configs,
    // pickling, upickle,
    // pprint,
    enumeration, enumerationCirce,
    sprayJson,
    circeCore, circeGeneric, circeJawn, circeParser, circeOptics, circeJava8,
    cats,
    // dogs
    shapeless,
    kittens,
    monocleCore, monocleGeneric, monocleMacro, monocleState,
    pprint,
    atmos,
    objectsize
  )

  val monitoring = Seq(
    /* akkaTracing,*/
    kamonScala, kamonStatsd, kamonAkka,
    kamonAkkaRemote, kamonJdbc, kamonSystemMetrics,
    kamonLogReporter
  )

  val akka = Seq(
    akkaActor, akkaClusterSharding, akkaContrib,
    akkaDistributedData,
    akkaStream, akkaHttpCore, akkaHttp, akkaHttpSprayJson, akkaHttpCirceJson,
    // akkaHttpSwagger,
    akkaSlf4j,
    akkaPersistence, akkaPersistenceJdbc //,
    // akkaSse
  )

  val root = common // ++ monitoring

  val api = common ++ akka ++ Seq(oauth)

  val tests = common ++ Seq(
    akkaTestkit, akkaHttpTestkit,
    scalacheck, specs2, scalatest, slickTestkit
  )

  val data = common ++ Seq(xml, shapeless)

  val models = common ++ Seq(
    bcrypt, routeTrie
  )

  val persist = common ++ akka ++ Seq(
    akkaHttpCore,
    postgresJdbc,
    dbMigration,
    // quillJdbc, quillAsync,
    slick, slickHikariCp, slickPg, slickPgDate2, slickPgCirceJson,
    hikariCp, bcrypt, commonsCodec,
    routeTrie
  )

  val utils = common ++ Seq(
    akkaActor, akkaStream,
    redis, lz4,
    googleLru, caffeine
  )

  val macros = common

  val json = common

  val request = common

  val response = common

  val docker = common ++ Seq(
    akkaHttp, akkaStream,
    commonsCodec
  )

  val dockerDistribution = docker

  val crypto = akka ++ common ++ Seq(
    bcProvider, bcPkix, jose4s
  )

  val validations = common ++ Seq(akkaHttpCore, slick)

  val services = api

  val proxy = common ++ akka ++ Seq(routeTrie)
}
