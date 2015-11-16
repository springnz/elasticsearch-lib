package springnz.elasticsearch.client

import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import org.elasticsearch.action.index.{IndexAction, IndexRequest, IndexRequestBuilder}
import org.elasticsearch.action.search.{SearchAction, SearchRequestBuilder}
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.{ShouldMatchers, fixture}
import springnz.elasticsearch.server.{ESServer, ESServerParams}

import scala.concurrent.Await
import scala.concurrent.duration._

class ESClientTest extends fixture.WordSpec with ShouldMatchers {

  override type FixtureParam = ESServer
  private val snapshotDirPath: Path = Files.createTempDirectory(s"snapshot-")

  override def withFixture(test: OneArgTest) = {
 //   val server = new ESServer("elasticsearch", ESServerParams(httpPort = None, Map("path.repo" -> s"${snapshotDirPath.toString}")))
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
    import ESClient._

    val indexName = "megacorp"
    val typeName = "employee"

    def createIndexRequest(client: Client): IndexRequest = {
      val indexData = """
                        |{
                        |    "first_name" : "John",
                        |    "last_name" :  "Smith",
                        |    "age" :        25,
                        |    "about" :      "I love to go rock climbing",
                        |    "interests": [ "sports", "music" ]
                        |}
                      """.stripMargin
      val indexRequest = new IndexRequestBuilder(client, IndexAction.INSTANCE)
        .setIndex(indexName).setType(typeName)
        .setSource(indexData)
        .request()
      indexRequest
    }

    "index and search" in { server ⇒

      val client = transport(ESClientURI("localhost", 9300))
      val indexRequest: IndexRequest = createIndexRequest(client)
      val indexResponse = Await.result(client.execute(indexRequest), 5 seconds)
      println(s"indexResponse = $indexResponse")

      Thread.sleep(1000)

      val searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
        .setIndices(indexName)
        .setTypes(typeName)
        .setQuery(QueryBuilders.termQuery("age", "25"))
        .request()

      val searchResponse = Await.result(client.execute(searchRequest), 5 seconds)
      println(s"searchResponse = $searchResponse")

      searchResponse.getHits.getTotalHits shouldBe 1
    }

    "create a snapshot" ignore { server ⇒
      val client = transport(ESClientURI("localhost", 9300))
      val indexRequest: IndexRequest = createIndexRequest(client)
      Await.result(client.execute(indexRequest), 5 seconds)
//
//
//
//      val snapshotRequest = new CreateSnapshotRequestBuilder(client, CreateSnapshotAction.INSTANCE)
//        .setIndices(indexName)
//        .setRepository(snapshotDirPath.toString)
//        .setSnapshot("testbackup")
//        .request()
//
//      val snapshotResponse = Await.result(client.execute(snapshotRequest), 5 seconds)
//      println(s"snapshotResponse = $snapshotResponse")
//
    }
  }

}
