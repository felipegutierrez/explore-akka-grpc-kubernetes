package com.example.helloworld

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success, Try}

case class SocialPost(owner: String, content: String)

object HttpToGrpc {

  val socialRequests = List(
    HelloRequest("Daniel"),
    HelloRequest("Martin"),
    HelloRequest("Felipe"),
    HelloRequest("Christopher"),
    HelloRequest("Fabio"),
    HelloRequest("Simone"),
    HelloRequest("John"),
    HelloRequest("Oscar"),
    HelloRequest("Bob"),
    HelloRequest("Jordan"),
    HelloRequest("Michael")
  )

  val html =
    """
      |<html>
      |    <head>
      |        <script>
      |            var mySocket = new WebSocket("ws://localhost:8080/api/socket");
      |            console.log("starting websocket...");
      |
      |            mySocket.onmessage = function(event) {
      |                var newChild = document.createElement("div");
      |                newChild.innerText = event.data;
      |                document.getElementById("1").appendChild(newChild);
      |            };
      |
      |            mySocket.onopen = function(event) {
      |                mySocket.send("socket seems to be open...");
      |            };
      |
      |            mySocket.send("socket says: hello, server!");
      |        </script>
      |    </head>
      |    <body>
      |        Starting websocket...
      |        <div id="1">
      |        </div>
      |    </body>
      |</html>
    """.stripMargin

  implicit val system: ActorSystem = ActorSystem("HttpToGrpc")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val log: LoggingAdapter = system.log

  val settings = GrpcClientSettings.fromConfig("helloworld.GreeterService")
  val client = GreeterServiceClient(settings)

  // dynamic web socket flow to update html without refreshing the page
  val dynamicSource = Source(Stream.from(1)).throttle(1, 1 second)
    .map { _ =>
      val helloRequest: HelloRequest = socialRequests(Random.nextInt(socialRequests.size))
      log.info(s"Scheduled say hello to ${helloRequest.name}")
      val responseClientGrpc: Future[HelloReply] = client.sayHello(helloRequest)
      val message: Future[Message] = responseClientGrpc.map { helloReply: HelloReply =>
        log.info(s"Scheduled ${helloRequest.name} say hello response: ${helloReply.message}")
        TextMessage(s"${helloRequest.name} says: ${helloReply.message}")
      }
      Try(Await.result(message, 3 seconds)) match {
        case Success(value) => value
        case Failure(exception) => TextMessage(exception.getMessage)
        case _ => TextMessage("Unknown message")
      }
    }
  val socketFlow: Flow[Message, Message, Any] = Flow
    .fromSinkAndSource(Sink.foreach[Message](println), dynamicSource)

  val routes =
    path("hello" / Segment) { name =>
      get {
        log.info("hello request")
        onComplete(client.sayHello(HelloRequest(name))) {
          case Success(reply) => complete(reply.message)
          case Failure(t) =>
            log.error(t, "Request failed")
            complete(StatusCodes.InternalServerError, t.getMessage)
        }
      }
    } ~ (path("api" / "web" / "socket")) {
      get {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            html
          )
        )
      } ~ {
        complete(StatusCodes.Forbidden)
      }
    } ~ path("api" / "socket") {
      handleWebSocketMessages(socketFlow)
    }

  def main(args: Array[String]): Unit = {

    system.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds)(() => {
      val helloRequest: HelloRequest = socialRequests(Random.nextInt(socialRequests.size))
      log.info(s"Scheduled say hello to ${helloRequest.name}")
      val response: Future[HelloReply] = client.sayHello(helloRequest)
      response.onComplete { r =>
        val helloReply: HelloReply = r.get
        log.info(s"Scheduled say hello response: ${helloReply.message}")
      }
    })

    val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bindFlow(routes)

    bindingFuture.onComplete {
      case Success(sb) =>
        log.info("Bound: {}", sb)
      case Failure(t) =>
        log.error(t, "Failed to bind. Shutting down")
        system.terminate()
    }
  }
}
