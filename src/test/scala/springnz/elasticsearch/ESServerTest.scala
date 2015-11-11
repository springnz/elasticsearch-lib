package springnz.elasticsearch

import com.ning.http.client.Response
import dispatch.Future
import org.scalatest._
import wabisabi._
import scala.concurrent.Await
import scala.concurrent.duration._

class ESServerTest extends fixture.WordSpec with ShouldMatchers {

  override type FixtureParam = ESServer
  val port  = "9250"

  override def withFixture(test: OneArgTest) = {
    val server = new ESServer("test-cluster", port)

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
  }
}

