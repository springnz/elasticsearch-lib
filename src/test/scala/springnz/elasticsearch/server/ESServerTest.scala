package springnz.elasticsearch.server

import com.ning.http.client.{Request, Response}
import dispatch.{url, Req, Future}
import org.scalatest._
import play.api.libs.json.Json
import springnz.elasticsearch.utils.HttpUtils
import wabisabi._

import scala.concurrent.Await
import scala.concurrent.duration._

class ESServerTest extends fixture.WordSpec with ShouldMatchers {

  override type FixtureParam = ESServer
  val port = 9250

  override def withFixture(test: OneArgTest) = {
    val server = new ESServer("test-cluster", ESServerParams(httpPort = Some(port)))

    try {
      withFixture(test.toNoArgTest(server)) // "loan" the fixture to the test
    } finally
      server.stop() // clean up the fixture
  }

  "ESServerTest" should {

    "start up a test instance with health green" in { server ⇒
      server.start()

      val client = new Client(s"http://127.0.0.1:$port")
      val healthFuture: Future[Response] = client.health()
      val health = Await.result(healthFuture, 10 seconds)

      health.getStatusText shouldBe "OK"

      val responseBody = health.getResponseBody
      responseBody.contains(""","status":"green",""") shouldBe true
    }

    "add a test index" in { server ⇒
      server.start()

      val client = new Client(s"http://127.0.0.1:$port")
      val indexFuture: Future[Response] = client.createIndex("testindex")

      val index = Await.result(indexFuture, 10 seconds)
      index.getResponseBody shouldBe """{"acknowledged":true}"""
    }

    "check we can connect with HttpUtils" in { server =>
      server.start()
      val request: Req = url(s"http://localhost:$port").GET
      import HttpUtils._

      import scala.concurrent.ExecutionContext.Implicits.global
      val responseFuture = request.processHttpRequest(waitForComplete = true)
      val response = Await.result(responseFuture, 10 seconds)
      Json.stringify(response.json) should include ("You Know, for Search")
    }
  }
}

