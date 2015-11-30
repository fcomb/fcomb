import sbt._

object Dependencies {
  object V {
    val akka = "2.4.1"
    val akkaExperimental = "2.0-M2"
    val bouncyCastle = "1.53"
    val cats = "0.3.0"
    val circle = "0.1.1"
    val slick = "3.1.0"
    val slickPg = "0.10.1"
    val scalaz = "7.1.5"
    val scalikeJdbc = "2.2.8"
    val kamon = "0.5.2"
  }

  object Compile {
    val routeTrie       = "io.fcomb"                      %% "route-trie"                    % "0.4.0"
    val dbMigration     = "io.fcomb"                      %% "db-migration"                  % "0.2.2"

    val akkaActor       = "com.typesafe.akka"             %% "akka-actor"                    % V.akka
    val akkaClusterSharding = "com.typesafe.akka"         %% "akka-cluster-sharding"         % V.akka
    val akkaDistributedData = "com.typesafe.akka"         %% "akka-distributed-data-experimental" % V.akka
    val akkaContrib     = "com.typesafe.akka"             %% "akka-contrib"                  % V.akka
    val akkaStream      = "com.typesafe.akka"             %% "akka-stream-experimental"      % V.akkaExperimental
    val akkaHttp        = "com.typesafe.akka"             %% "akka-http-experimental"        % V.akkaExperimental
    val akkaSlf4j       = "com.typesafe.akka"             %% "akka-slf4j"                    % V.akka
    val akkaPersistence = "com.typesafe.akka"             %% "akka-persistence"              % V.akka
    val akkaPersistenceJdbc = "com.github.dnvriend"       %% "akka-persistence-jdbc"         % "1.2.2"
    // val akkaPersistenceCassandra = "com.github.krasserm"  %% "akka-persistence-cassandra"    % "0.5-SNAPSHOT" changing()
    // val akkaKryo        = "com.github.romix.akka"         %% "akka-kryo-serialization"       % "0.3.3"

    val dns4s           = "com.github.mkroli"             %% "dns4s-akka"                    % "0.8"

    val akkaTracing     = "com.github.levkhomich"         %% "akka-tracing-core"             % "0.5-SNAPSHOT" changing()
    val kamonScala      = "io.kamon"                      %% "kamon-scala"                   % V.kamon
    val kamonAkka       = "io.kamon"                      %% "kamon-akka"                    % V.kamon
    val kamonAkkaRemote = "io.kamon"                      %% "kamon-akka-remote"             % V.kamon
    val kamonStatsd     = "io.kamon"                      %% "kamon-statsd"                  % V.kamon
    val kamonJdbc       = "io.kamon"                      %% "kamon-jdbc"                    % V.kamon
    val kamonSystemMetrics = "io.kamon"                   %% "kamon-system-metrics"          % V.kamon
    val kamonLogReporter = "io.kamon"                     %% "kamon-log-reporter"            % V.kamon

    val javaCompat      = "org.scala-lang.modules"        %% "scala-java8-compat"            % "0.7.0"

    val clump           = "io.getclump"                   %% "clump-scala"                   % "0.0.12"

    // "com.github.romix.akka" %% "akka-kryo-serialization" % "0.3.3"

    // val upickle         = "com.lihaoyi"                   %% "upickle"                       % "0.3.5"
    // val pprint          = "com.lihaoyi"                   %% "pprint"                        % "0.3.5"
    val sprayJson       = "io.spray"                      %% "spray-json"                   % "1.3.2"
    val sprayJsonShapeless = "com.github.fommil"          %% "spray-json-shapeless"          % "1.1.0"
    // val circleCore      = "io.circe"                      %% "circe-core"                    % V.circle
    // val circleGeneric   = "io.circe"                      %% "circe-generic"                 % V.circle
    // val circleJawn      = "io.circe"                      %% "circe-jawn"                    % V.circle

    val pickling        = "org.scala-lang.modules"        %% "scala-pickling"                % "0.10.1"

    val googleLru       = "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"
    val caffeine        = "com.github.ben-manes.caffeine" % "caffeine"                       % "1.3.3"

    val config          = "com.typesafe"                  %  "config"                        % "1.3.0"
    val configs         = "com.github.kxbmap"             %% "configs"                       % "0.3.0"

    val postgresJdbc    = "org.postgresql"                %  "postgresql"                    % "9.4-1204-jdbc42" exclude("org.slf4j", "slf4j-simple")
    val slick           = "com.typesafe.slick"            %% "slick"                         % V.slick
    val slickHikariCp   = "com.typesafe.slick"            %% "slick-hikaricp"                % V.slick exclude("com.zaxxer", "HikariCP-java6")
    val slickPg         = "com.github.tminglei"           %% "slick-pg"                      % V.slickPg exclude("org.postgresql", "postgresql")
    val slickPgSprayJson = "com.github.tminglei"          %% "slick-pg_spray-json"           % V.slickPg
    val slickJdbc       = "com.github.tarao"              %% "slick-jdbc-extension"          % "0.0.7"
    // val slickless       = "io.underscore"                 %% "slickless"                     % "0.1.1"
    val hikariCp        = "com.zaxxer"                    %  "HikariCP"                      % "2.4.1"
    // val relate          = "com.lucidchart"                %% "relate"                        % "1.7.1"
    // val scalikeJdbc     = "org.scalikejdbc"               %% "scalikejdbc"                   % V.scalikeJdbc
    // val scalikeJdbcMacros = "org.scalikejdbc"             %% "scalikejdbc-syntax-support-macro" % V.scalikeJdbc
    // val scalikeJdbcAsync = "org.scalikejdbc"              %% "scalikejdbc-async"             % "0.5.5"
    // val postgresAsync    = "com.github.mauricio"          %% "postgresql-async"              % "0.2.15"

    // val phantom         = "com.websudos"                  %% "phantom-dsl"                   % V.phantom
    // val phantomUdt      = "com.websudos"                  %% "phantom-udt"                   % V.phantom

    val levelDb         = "org.iq80.leveldb"              %  "leveldb"                        % "0.7"
    val levelDbJni      = "org.fusesource.leveldbjni"     %  "leveldbjni-all"                 % "1.8"

    val redis           = "com.etaty.rediscala"           %% "rediscala"                     % "1.5.0"

    val bcProvider      = "org.bouncycastle"              %  "bcprov-jdk15on"                % V.bouncyCastle
    val bcPkix          = "org.bouncycastle"              %  "bcpkix-jdk15on"                % V.bouncyCastle

    val oauth           = "com.nulab-inc"                 %% "scala-oauth2-core"             % "0.13.1"
    val bcrypt          = "com.github.t3hnar"             %% "scala-bcrypt"                  % "2.4"
    val nacl4s          = "com.github.emstlk"             %% "nacl4s"                        % "1.0.0" 

    val s3              = "com.amazonaws"                 %  "aws-java-sdk-s3"               % "1.10.14"
    val awsWrap         = "com.github.dwhjames"           %% "aws-wrap"                      % "0.7.2"

    val scalazCore      = "org.scalaz"                    %% "scalaz-core"                   % V.scalaz
    val scalazConcurrent = "org.scalaz"                   %% "scalaz-concurrent"             % V.scalaz
    val scalazStream    = "org.scalaz.stream"             %% "scalaz-stream"                 % "0.8"
    val shapeless       = "com.chuusai"                   %% "shapeless"                     % "2.2.5"
    val shapelessScalaz = "org.typelevel"                 %% "shapeless-scalaz"              % "0.4"

    // val raptureCore     = "com.propensive"                %% "rapture-core"                  % "1.1.0"
    // val raptureIo       = "com.propensive"                %% "rapture-io"                    % "0.9.0"
    //
    // val algebra         = "org.spire-math"                %% "algebra"                       % V.algebra
    // val algebraStd      = "org.spire-math"                %% "algebra-std"                   % V.algebra
    //
    val cats            = "org.spire-math"                %% "cats-core"                     % V.cats
    val catsState       = "org.spire-math"                %% "cats-state"                    % V.cats
    //
    // val monocleCore     = "com.github.julien-truffaut"    %%  "monocle-core"                 % V.monocle
    // val monocleGeneric  = "com.github.julien-truffaut"    %%  "monocle-generic"              % V.monocle
    // val monocleMacro    = "com.github.julien-truffaut"    %%  "monocle-macro"                % V.monocle
    // val monocleState    = "com.github.julien-truffaut"    %%  "monocle-state"                % V.monocle

    val scodecBits      = "org.scodec"                    %% "scodec-bits"                   % "1.0.10"
    val scodecCore      = "org.scodec"                    %% "scodec-core"                   % "1.8.2"
    val scodecScalaz    = "org.scodec"                    %% "scodec-scalaz"                 % "1.1.0"
    val scodecStream    = "org.scodec"                    %% "scodec-stream"                 % "0.10.0"
    val scodecProtocols = "org.scodec"                    %% "scodec-protocols"              % "0.7.0"
    val scodecAkka      = "org.scodec"                    %% "scodec-akka"                   % "0.1.0-SNAPSHOT" changing()

    val logbackClassic  = "ch.qos.logback"                %  "logback-classic"               % "1.1.3"
    val scalaLogging    = "com.typesafe.scala-logging"    %% "scala-logging"                 % "3.1.0"

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
  }

  object Test {
    val akkaTestkit     = "com.typesafe.akka"             %% "akka-testkit"                  % V.akka % "test"
    val scalacheck      = "org.scalacheck"                %% "scalacheck"                    % "1.12.5" % "test"
    val specs2          = "org.specs2"                    %% "specs2-core"                   % "3.6.5" % "test"
    val scalatest       = "org.scalatest"                 %% "scalatest"                     % "2.2.5" % "test"
  }

  import Compile._, Test._

  val common = Seq(
    javaCompat,
    logbackClassic, scalaLogging,
    config, configs,
    // pickling, upickle,
    // pprint,
    sprayJson, sprayJsonShapeless,
    // circleCore, circleGeneric, circleJawn,
    scalazCore, scalazConcurrent, scalazStream,
    cats, catsState,
    shapeless, shapelessScalaz /*,
    jamm */
    , objectsize
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
    akkaStream, akkaHttp,
    akkaSlf4j,
    akkaPersistence, akkaPersistenceJdbc /*akkaStreamExtensions, akkaPersistenceCassandra*/
  )

  val scodec = Seq(
    scodecCore, scodecBits, scodecScalaz, scodecStream,
    scodecProtocols, scodecAkka
  )

  val root = common // ++ monitoring

  val api = common ++ akka ++ Seq(oauth)

  val tests = common ++ Seq(akkaTestkit, scalacheck, specs2, scalatest)

  val data = common ++ Seq(xml, scalazCore, shapeless, shapelessScalaz)

  val models = common ++ Seq(
    bcrypt, routeTrie
  )

  val persist = common ++ Seq(
    postgresJdbc,
    dbMigration,
    slick, slickHikariCp, slickPg, slickPgSprayJson, slickJdbc, // slickless,
    // relate,
    // scalikeJdbc, scalikeJdbcMacros, scalikeJdbcAsync, postgresAsync,
    // phantom, phantomUdt,
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

  val crypto = common ++ Seq(bcProvider, bcPkix)

  val validations = common ++ Seq(slick)

  val services = api

  val proxy = common ++ akka ++ Seq(routeTrie)
}
