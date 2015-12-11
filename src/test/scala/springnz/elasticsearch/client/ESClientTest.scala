package springnz.elasticsearch.client

import java.nio.file.{ Files, Path }

import org.apache.commons.io.FileUtils
import org.elasticsearch.action.search.{ SearchAction, SearchRequestBuilder }
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.{ ShouldMatchers, fixture }
import springnz.elasticsearch.server.{ ESServer, ESServerParams }

import scala.concurrent.Await
import scala.concurrent.duration._

class ESClientTest extends fixture.WordSpec with ShouldMatchers {

  import ClientPimper._
  import ESClient._

  override type FixtureParam = ESServer

  // TODO: create a working example of Snapshotting an index
  private val snapshotDirPath: Path = Files.createTempDirectory(s"snapshot-")
  private val timeout = 5.seconds

  override def withFixture(test: OneArgTest) = {
    val server = new ESServer("elasticsearch", ESServerParams(httpPort = None, repos = Seq(snapshotDirPath.toString)))
    try {
      server.start()
      withFixture(test.toNoArgTest(server)) // "loan" the fixture to the test
    } finally {
      FileUtils.forceDelete(snapshotDirPath.toFile)
      server.stop() // clean up the fixture
    }
  }

  "ESClientTest" should {

    "create index" in { server ⇒
      val client = transport(ESClientURI("localhost", 9300))
      val indexName = "testindex"
      val result = Await.ready(client.createIndex(indexName), timeout)
      result.value.get.isSuccess shouldBe true
    }

    "create index, close it, update settings" in { server ⇒
      val client = transport(ESClientURI("localhost", 9300))
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
      Thread.sleep(1000) // must wait a while for index to be created even after future completes

      Await.result(client.closeIndex(indexName), timeout) // must close index before updating settings

      val result = Await.ready(client.updateSettings(indexName, settings), timeout)
      result.value.get.isSuccess shouldBe true
    }

    "insert then search" in { server ⇒
      val client = transport(ESClientURI("localhost", 9300))
      val indexName = "testindex"
      val typeName = "docs"
      val source =
        """
          |{
          |  "name": "bob"
          |}
        """.stripMargin

      Await.result(client.createIndex(indexName), timeout)
      Thread.sleep(1000) // must wait for index to be created even after future completes

      val insertResult = Await.ready(client.insert(indexName, typeName, source), timeout)
      insertResult.value.get.isSuccess shouldBe true
      Thread.sleep(1000) // must wait for insert to be processed

      val searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
        .setIndices(indexName)
        .setTypes(typeName)
        .setQuery(QueryBuilders.termQuery("name", "bob"))
        .request()

      val searchResponse = Await.result(client.execute(searchRequest), timeout)
      searchResponse.getHits.totalHits shouldBe 1
    }

    "put and get mapping" in { server ⇒
      val client = transport(ESClientURI("localhost", 9300))
      val indexName = "testindex"
      val typeName = "docs"
      val source = """{"docs":{"properties":{"somefield":{"type":"string"}}}}"""

      Await.result(client.createIndex(indexName), timeout)
      Thread.sleep(1000) // must wait for index to be created even after future completes

      val putFuture = Await.ready(client.putMapping(indexName, typeName, source), timeout)
      putFuture.value.get.isSuccess shouldBe true

      val mappingsResponse = Await.result(client.getMapping(indexName, typeName, source), timeout)
      val mapping = mappingsResponse.getMappings.get(indexName).get(typeName).source()
      mapping.toString shouldBe source
    }
  }
}
