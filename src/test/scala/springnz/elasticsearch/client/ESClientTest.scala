package springnz.elasticsearch.client

import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse
import org.elasticsearch.action.search.{ SearchAction, SearchRequestBuilder }
import org.elasticsearch.index.query.QueryBuilders
import org.scalatest.ShouldMatchers
import springnz.elasticsearch.ESEmbedded

import scala.concurrent.Await
import scala.concurrent.duration._

class ESClientTest extends ESEmbedded with ShouldMatchers {

  import ClientExt._

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

      // Settings

      val indexName = "testindex"

      Await.result(client.createIndex(indexName), timeout)
      Await.result(client.closeIndex(indexName), timeout) // must close index before updating settings

      val result = Await.ready(client.updateSettings(indexName, JsonSources.settingsSource), timeout)
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

      val insertResult = Await.ready(client.insert(indexName, typeName, source), timeout)
      insertResult.value.get.isSuccess shouldBe true
      Thread.sleep(1000)

      val searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
        .setIndices(indexName)
        .setTypes(typeName)
        .setQuery(QueryBuilders.termQuery("name", "bob"))
        .request()

      val searchResponse = Await.result(client.executeRequest(searchRequest), timeout)
      searchResponse.getHits.totalHits shouldBe 1
    }

    "put and get mapping" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val typeName = "docs"
      val source = """{"docs":{"properties":{"somefield":{"type":"string"}}}}"""

      Await.result(client.createIndex(indexName), timeout)

      val putMappingFuture = Await.ready(client.putMapping(indexName, typeName, source), timeout)
      putMappingFuture.value.get.isSuccess shouldBe true

      val mapping = Await.result(client.getMapping(indexName, typeName), timeout)
      mapping shouldBe source
    }

    "check presence of index" in { server ⇒
      val client = newClient()
      val indexName = "testindex"
      val result = Await.ready(client.createIndex(indexName), timeout)
      Thread.sleep(2000)
      result.value.get.isSuccess shouldBe true

      import scala.collection.JavaConverters._
      val indicesStats: IndicesStatsResponse = Await.result(client.getIndicesStats(), timeout)
      val indices = indicesStats.getIndices.asScala.toMap
      indices.get(indexName).isDefined shouldBe true

    }

    "create index, close it, update settings, update mapping, open index" in { server ⇒
      val client = newClient()

      val indexName = "testindex"

      // Settings
      Await.result(client.createIndex(indexName), timeout)

      Await.result(client.createIndex("openindex"), timeout) // create another index and leave it open

      Await.result(client.closeIndex(indexName), timeout) // must close index before updating settings

      val result = Await.ready(client.updateSettings(indexName, JsonSources.settingsSource), timeout)
      result.value.get.isSuccess shouldBe true

      // Mapping

      val typeName = "docs"
      val mappingSource = s"""{"$typeName":{"properties":{"somefield":{"type":"string", "analyzer": "autocomplete_word"}}}}"""
      val putMappingFuture = Await.ready(client.putMapping(indexName, typeName, mappingSource), timeout)

      putMappingFuture.value.get.isSuccess shouldBe true

      // reopen the index
      Await.result(client.openIndex(indexName), timeout) // throws an exception if failed
    }
  }

  object JsonSources {
    val settingsSource = """
      |{
      |  "analysis": {
      |    "filter": {
      |      "autocomplete_word_filter": {
      |        "type": "edge_ngram",
      |        "min_gram": 1,
      |        "max_gram": 18
      |      }
      |    },
      |    "analyzer": {
      |      "autocomplete_word": {
      |        "type": "custom",
      |        "tokenizer": "standard",
      |        "filter": [
      |          "lowercase",
      |          "stop",
      |          "autocomplete_word_filter"
      |        ]
      |      }
      |    }
      |  }
      |}
    """.stripMargin
  }
}
