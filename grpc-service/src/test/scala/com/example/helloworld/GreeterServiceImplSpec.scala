package com.example.helloworld

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GreeterServiceConf {
  // important to enable HTTP/2 in server ActorSystem's config
  val configServer = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  // this configuration is not working
  val configString1 =
    """
      | akka.grpc.client {
      |   "helloworld.GreeterService" {
      |      host = 127.0.0.1
      |      port = 8080
      |      service-discovery {
      |         mechanism = "akka-dns"
      |         service-name = "grpcservice.default.svc.cluster.local"
      |         protocol = "tcp"
      |         port-name = "http"
      |      }
      |      client.use-tls = false
      |   }
      | }
      | akka {
      |   loglevel = "INFO"
      |   actor.allow-java-serialization = "on"
      |   discovery.method = "akka-dns"
      |   io.dns.resolver = "async-dns"
      | }
            """.stripMargin
  val configString2 =
    """
      |akka.grpc.client {
      |  "helloworld.GreeterService" {
      |    host = 127.0.0.1
      |    port = 8080
      |  }
      |}
      |""".stripMargin
  val configClient = ConfigFactory.parseString(configString2)
}

class GreeterServiceImplSpec extends TestKit(ActorSystem("GreeterServiceImplSpec", ConfigFactory.load(GreeterServiceConf.configServer)))
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  // val testKit = ActorTestKit(conf)
  val serverSystem: ActorSystem = system
  val bound = new GreeterServer(serverSystem).run()

  // make sure server is bound before using client
  bound.futureValue

  implicit val clientSystem: ActorSystem = ActorSystem("GreeterClient", ConfigFactory.load(GreeterServiceConf.configClient))

  val client = GreeterServiceClient(
    GrpcClientSettings
      .fromConfig("helloworld.GreeterService")
      .withTls(false)
  )

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
    TestKit.shutdownActorSystem(clientSystem)
  }

  "GreeterService" should {
    "reply to single request" in {
      import GreeterServiceData._
      val name = "John"
      val reply = client.sayHello(HelloRequest(name))
      val helloReply = HelloReply(s"Hello, $name -> ${mapHelloReply.getOrElse(name, "this person does not exist =(")}")

      import system.dispatcher

      Await.result(reply, 2 seconds)
      reply.onComplete {
        case Success(value) =>
          println(s"success: ${value.message}")
        case Failure(exception) =>
          println(s"failure $exception")
      }
      reply.futureValue should ===(helloReply)
    }
  }
}
