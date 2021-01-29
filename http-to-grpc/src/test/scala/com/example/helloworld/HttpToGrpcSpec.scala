package com.example.helloworld

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest, WSProbe}
import akka.stream.Materializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class HttpToGrpcSpec extends AnyWordSpec
  with Matchers
  with ScalatestRouteTest {

  import HttpToGrpc._

  implicit val mat: Materializer = Materializer(HttpToGrpc.system)
  implicit val timeout: RouteTestTimeout = RouteTestTimeout(2 seconds)

  val wsClient = WSProbe()(system, mat)

  "A HTTP request using akka-Grpc protocol on the backend" should {
    "return StatusCodes.OK[200] for a GET socket open try" in {
      Get("/api/web/socket") ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
    "return StatusCodes.Forbidden[403] for a POST socket open try" in {
      Post("/api/web/socket") ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
    "return StatusCodes for a socket open try" in {
      WS("/api/socket", wsClient.flow) ~> routes ~> check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true
        // manually run a WS conversation

        // wsClient.sendMessage("")
        // wsClient.expectMessage("UNAVAILABLE")

        // wsClient.sendCompletion()
        // wsClient.expectCompletion()
      }
    }
  }
}
