package springnz.elasticsearch.client

import java.nio.file.{ Files, Path }

import org.apache.commons.io.FileUtils
import org.elasticsearch.action.search.{ SearchAction, SearchRequestBuilder }
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.{ ShouldMatchers, fixture }
import springnz.elasticsearch.server.{ ESServer, ESServerParams }
import springnz.util.Logging

import scala.concurrent.Await
import scala.concurrent.duration._

trait ESEmbedded extends fixture.WordSpec with Logging {

  override type FixtureParam = ESServer

  val esBinaryPort = 9300
  val esHttpPort = None
  val clusterName = "elasticsearch"
  val extraConfig: Map[String, String] = Map.empty

  // TODO: create a working example of Snapshotting an index
  val snapshotDirPath: Path = Files.createTempDirectory(s"snapshot-")

  val serverParams = ESServerParams(esHttpPort, Seq(snapshotDirPath.toString), extraConfig)

  def newClient() = ESClient.transport(ESClientURI("localhost", esBinaryPort), clusterName)

  override def withFixture(test: OneArgTest) = {
    val server = new ESServer(clusterName, serverParams)
    try {
      server.start()
      withFixture(test.toNoArgTest(server)) // "loan" the fixture to the test
    } finally {
      FileUtils.deleteDirectory(snapshotDirPath.toFile)
      server.stop()
    }
  }
}

class ESClientTest extends ESEmbedded with ShouldMatchers {

  import ClientPimper._

  val timeout = 5.seconds

  "ESClientTest" should {

    "create index" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val result = Await.ready(client.createIndex(indexName), timeout)
      result.value.get.isSuccess shouldBe true
    }

    "create, close, delete index" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      Await.ready(client.createIndex(indexName), timeout)
      Await.ready(client.closeIndex(indexName), timeout)
      Await.result(client.deleteIndex(indexName), timeout)
    }

    "create index, close it, update settings" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val settings =
        """
          |{
          |  "analysis": {
          |    "filter": {
          |      "name_autocomplete_filter": {
          |        "type": "edge_ngram",
          |        "min_gram": 1,
          |        "max_gram": 18
          |      }
          |    }
          |  }
          |}
        """.stripMargin

      Await.result(client.createIndex(indexName), timeout)
      Thread.sleep(1000)

      Await.result(client.closeIndex(indexName), timeout) // must close index before updating settings

      val result = Await.ready(client.updateSettings(indexName, settings), timeout)
      result.value.get.isSuccess shouldBe true
    }

    "insert then search" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val typeName = "docs"
      val source =
        """
          |{
          |  "name": "bob"
          |}
        """.stripMargin

      Await.result(client.createIndex(indexName), timeout)
      Thread.sleep(1000)

      val insertResult = Await.ready(client.insert(indexName, typeName, source), timeout)
      insertResult.value.get.isSuccess shouldBe true
      Thread.sleep(1000)

      val searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
        .setIndices(indexName)
        .setTypes(typeName)
        .setQuery(QueryBuilders.termQuery("name", "bob"))
        .request()

      val searchResponse = Await.result(client.execute(searchRequest), timeout)
      searchResponse.getHits.totalHits shouldBe 1
    }

    "put and get mapping" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val typeName = "docs"
      val source = """{"docs":{"properties":{"somefield":{"type":"string"}}}}"""

      Await.result(client.createIndex(indexName), timeout)
      Thread.sleep(1000)

      val putFuture = Await.ready(client.putMapping(indexName, typeName, source), timeout)
      putFuture.value.get.isSuccess shouldBe true

      val mappingsResponse = Await.result(client.getMapping(indexName, typeName), timeout)
      val mapping = mappingsResponse.getMappings.get(indexName).get(typeName).source()
      mapping.toString shouldBe source
    }

    "get aliases" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val result = Await.ready(client.createIndex(indexName), timeout)

    }
  }
}
