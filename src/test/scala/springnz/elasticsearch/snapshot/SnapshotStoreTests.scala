package springnz.elasticsearch.snapshot

import java.nio.file.Files

import org.apache.commons.io.FileUtils
import org.elasticsearch.action.search.{ SearchAction, SearchRequestBuilder }
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.{ BeforeAndAfterAll, ShouldMatchers }
import springnz.elasticsearch.ESEmbedded
import springnz.elasticsearch.client.ClientExt
import springnz.elasticsearch.server.ESServerConfig

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SnapshotStoreTests extends ESEmbedded with ShouldMatchers with BeforeAndAfterAll {
  import ClientExt._

  val timeout = 5.seconds
  val snapshotRepoDir = Files.createTempDirectory("snapshot-test-")

  override def afterAll(): Unit = {
    FileUtils.forceDelete(snapshotRepoDir.toFile)
  }

  override val serverConfig = ESServerConfig(clusterName)
    .withHttpPort(httpPort)
    .withTransportPort(transportPort)
    .withSnapshotRepoDir(snapshotRepoDir.toFile.toString)

  "SnapshotStore" should {
    "create zipped repo, delete index, restore zipped repo" in { server ⇒

      val indexName = "testindex"
      val repoName = "test_repo"

      // create an index with one document
      val client = newClient()
      val typeName = "docs"
      val source =
        """
          |{
          |  "name": "bob"
          |}
        """.stripMargin
      Await.result(client.createIndex(indexName), timeout)
      val insertResult = Await.ready(client.insert(indexName, typeName, source), timeout)
      insertResult.value.get.isSuccess shouldBe true
      Thread.sleep(1000)

      // create zipped repo
      val snapshotStore = new SnapshotStore(httpPort)
      snapshotStore.createZippedSnapshot(snapshotRepoDir.toFile.toString, repoName, "test_snapshot", Seq(indexName))

      // delete index
      Await.result(client.closeIndex(indexName), timeout)
      Await.result(client.deleteIndex(indexName), timeout)

      // restore zipped repo
      snapshotStore.restoreZippedRepo(snapshotRepoDir.toFile.toString, repoName, snapshotRepoDir.toFile.toString)

      // search restored repo
      val searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
        .setIndices(indexName)
        .setTypes(typeName)
        .setQuery(QueryBuilders.termQuery("name", "bob"))
        .request()
      val searchResponse = Await.result(client.executeRequest(searchRequest), timeout)
      searchResponse.getHits.totalHits shouldBe 1
    }
  }
}
