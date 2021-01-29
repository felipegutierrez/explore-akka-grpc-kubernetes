package com.example.helloworld

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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
      import system.dispatcher

      val name = "John"
      val expectedReply = HelloReply(s"Hello, $name -> ${mapHelloReply.getOrElse(name, "this person does not exist =(")}")

      val reply: Future[HelloReply] = client.sayHello(HelloRequest(name))
      Await.result(reply, 2 seconds)
      reply.onComplete {
        case Success(value) =>
          println(s"success: ${value.message}")
        case Failure(exception) =>
          println(s"failure $exception")
      }
      reply.futureValue should ===(expectedReply)
    }
    "reply to multiple requests" in {
      import GreeterServiceData._
      import system.dispatcher

      val names = List("John", "Martin", "Michael", "UnknownPerson")
      val expectedReplySeq: immutable.Seq[HelloReply] = names.map { name =>
        HelloReply(s"Hello, $name -> ${mapHelloReply.getOrElse(name, "this person does not exist =(")}")
      }
      // println(s"expectedReplySeq: ${expectedReplySeq.foreach(println)}")

      val requestStream: Source[HelloRequest, NotUsed] = Source(names).map(name => HelloRequest(name))
      val responseStream: Source[HelloReply, NotUsed] = client.sayHelloToAll(requestStream)

      //      val done: Future[Done] = responseStream.runForeach { reply: HelloReply =>
      //        // println(s"got streaming reply: ${reply.message}")
      //        assert(expectedReplySeq.contains(reply))
      //      }
      val sinkHelloReply = Sink.foreach[HelloReply] { e =>
        println(s"element: $e")
        assert(expectedReplySeq.contains(e))
      }
      responseStream.toMat(sinkHelloReply)(Keep.right).run().onComplete {
        case Success(value) => println(s"done")
        case Failure(exception) => println(s"exception $exception")
      }
    }
  }
}
