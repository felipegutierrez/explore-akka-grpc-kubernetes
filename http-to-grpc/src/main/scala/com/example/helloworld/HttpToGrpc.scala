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
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class SocialPost(owner: String, content: String)

object HttpToGrpc {

  val socialFeed = Source(
    List(
      SocialPost("Martin", " Scala 3 has been announced"),
      SocialPost("Daniel", "A new Rock the JVM course is open"),
      SocialPost("Martin", "I killed Java"),
      SocialPost("Felipe", "I have found a job to work with Scala =)")
    )
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

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("HttpToGrpc")
    implicit val mat: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher
    val log: LoggingAdapter = system.log

    val settings = GrpcClientSettings.fromConfig("helloworld.GreeterService")
    val client = GreeterServiceClient(settings)

    system.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds)(() => {
      log.info("Scheduled say hello to chris")
      val response: Future[HelloReply] = client.sayHello(HelloRequest("Christopher"))
      response.onComplete { r =>
        log.info("Scheduled say hello response {}", r)
      }
    })

    // dynamic web socket flow to update html without refreshing the page
    val socialMessages = socialFeed.throttle(1, 5 seconds).initialDelay(2 seconds)
      .map { socialPost: SocialPost => TextMessage(s"${socialPost.owner} said: ${socialPost.content}") }
    val socketFlow: Flow[Message, Message, Any] = Flow
      .fromSinkAndSource(Sink.foreach[Message](println), socialMessages)

    val route =
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
      } ~ (path("api" / "web" / "socket") & get) {
        complete(
          HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            html
          )
        )
      } ~ path("api" / "socket") {
        handleWebSocketMessages(socketFlow)
      }

    val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bindFlow(route)

    bindingFuture.onComplete {
      case Success(sb) =>
        log.info("Bound: {}", sb)
      case Failure(t) =>
        log.error(t, "Failed to bind. Shutting down")
        system.terminate()
    }
  }
}
