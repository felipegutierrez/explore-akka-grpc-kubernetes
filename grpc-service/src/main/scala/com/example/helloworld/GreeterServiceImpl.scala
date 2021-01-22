package com.example.helloworld

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}

import scala.concurrent.Future

class GreeterServiceImpl(materializer: Materializer, log: LoggingAdapter) extends GreeterService {

  val mapHelloReply = Map(
    "Simone" -> "I have found a job to work with Scala =)",
    "Martin" -> "Scala 3 has been announced",
    "Daniel" -> "A new Rock the JVM course is open",
    "John" -> "I killed Java",
    "Christopher" -> "I have created a sample project with akka-grpc to run on top of akka-http and akka-stream with K8S",
    "Felipe" -> "I have enhanced the akka-grpc, akka-http, akka-stream by creating a Docker image and run it on minikube =)",
    "Bob" -> "I am streaming data from akka-grpc server to client and exposing it on the browser in a stream fashion =)"
  )

  private implicit val mat: Materializer = materializer

  val (inboundHub: Sink[HelloRequest, NotUsed], outboundHub: Source[HelloReply, NotUsed]) =
    MergeHub.source[HelloRequest]
      .map(request => HelloReply(s"Hello ${request.name} -> ${mapHelloReply.getOrElse(request.name, "this person does not exist =(")}"))
      .toMat(BroadcastHub.sink[HelloReply])(Keep.both)
      .run()

  override def sayHello(request: HelloRequest): Future[HelloReply] = {
    log.info("sayHello {}", request)
    Future.successful(HelloReply(s"Hello, ${request.name} -> ${mapHelloReply.getOrElse(request.name, "this person does not exist =(")}"))
  }

  override def sayHelloToAll(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    log.info("sayHelloToAll")
    in.runWith(inboundHub)
    outboundHub
  }
}
