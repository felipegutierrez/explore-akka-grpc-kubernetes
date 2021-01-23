name := "explore-akka-grpc-sample-kubernetes"
version := "1.0"
scalaVersion := "2.12.7"
//scalaVersion := "2.13.4" // not available for coveralls plugin

lazy val akkaVersion = "2.6.11"
lazy val discoveryVersion = "1.0.9"
lazy val akkaHttpVersion = "10.2.2"

lazy val root = (project in file(".")).aggregate(httpToGrpc, grpcService)

import NativePackagerHelper._

// Http front end that calls out to a gRPC back end
lazy val httpToGrpc = (project in file("http-to-grpc"))
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,

      "com.typesafe.akka" %% "akka-parsing" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,

      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % discoveryVersion,

      "ch.qos.logback" % "logback-classic" % "1.2.3",
    ),
    dockerExposedPorts := Seq(8080),
    version in Docker := "1.0",
    packageName in Docker := packageName.value,
    dockerLabels := Map("felipeogutierrez" -> "felipe.o.gutierrez@gmail.com"),
    dockerBaseImage := "openjdk:jre-alpine",
    dockerRepository := Some("felipeogutierrez"),
    defaultLinuxInstallLocation in Docker := "/usr/local",
    daemonUser in Docker := "daemon",
    mappings in Universal ++= directory( baseDirectory.value / "src" / "main" / "resources" ),
  )

lazy val grpcService = (project in file("grpc-service"))
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % akkaVersion,

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,

      "ch.qos.logback" % "logback-classic" % "1.2.3",
    ),
    dockerExposedPorts := Seq(8080),
    version in Docker := "1.0",
    packageName in Docker := packageName.value,
    dockerLabels := Map("felipeogutierrez" -> "felipe.o.gutierrez@gmail.com"),
    dockerBaseImage := "openjdk:jre-alpine",
    dockerRepository := Some("felipeogutierrez"),
    defaultLinuxInstallLocation in Docker := "/usr/local",
    daemonUser in Docker := "daemon",
    mappings in Universal ++= directory( baseDirectory.value / "src" / "main" / "resources" ),
  )


