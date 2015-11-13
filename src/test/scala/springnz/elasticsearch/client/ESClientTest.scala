package springnz.elasticsearch.client

import org.elasticsearch.action.index.{ IndexAction, IndexRequestBuilder }
import org.elasticsearch.action.search.{ SearchAction, SearchRequestBuilder }
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.{ ShouldMatchers, WordSpec }
import springnz.elasticsearch.server.ESServer

import scala.concurrent.Await
import scala.concurrent.duration._

class ESClientTest extends WordSpec with ShouldMatchers {

  "index and search" in {

    import ESClient._

    val server = new ESServer("elasticsearch", None)
    server.start()

    val client = transport(ESClientURI("localhost", 9300))

    val indexName = "megacorp"
    val typeName = "employee"

    val indexRequest = new IndexRequestBuilder(client, IndexAction.INSTANCE)
      .setIndex(indexName).setType(typeName)
      .setSource(
        """
        |{
        |    "first_name" : "John",
        |    "last_name" :  "Smith",
        |    "age" :        25,
        |    "about" :      "I love to go rock climbing",
        |    "interests": [ "sports", "music" ]
        |}
      """.stripMargin)
      .request()

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

    server.stop()
  }

}
