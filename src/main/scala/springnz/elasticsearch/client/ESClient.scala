package springnz.elasticsearch.client

import java.net.InetSocketAddress

import com.typesafe.scalalogging.Logger
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.close.{ CloseIndexRequest, CloseIndexResponse }
import org.elasticsearch.action.admin.indices.create.{ CreateIndexAction, CreateIndexRequestBuilder, CreateIndexResponse }
import org.elasticsearch.action.admin.indices.delete.{ DeleteIndexAction, DeleteIndexRequestBuilder, DeleteIndexResponse }
import org.elasticsearch.action.admin.indices.mapping.get.{ GetMappingsAction, GetMappingsRequestBuilder, GetMappingsResponse }
import org.elasticsearch.action.admin.indices.mapping.put.{ PutMappingAction, PutMappingRequestBuilder, PutMappingResponse }
import org.elasticsearch.action.admin.indices.open.{ OpenIndexRequest, OpenIndexResponse }
import org.elasticsearch.action.admin.indices.settings.put.{ UpdateSettingsAction, UpdateSettingsRequestBuilder, UpdateSettingsResponse }
import org.elasticsearch.action.index.{ IndexAction, IndexRequestBuilder, IndexResponse }
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.node.NodeBuilder

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

object ClientPimper {

  implicit class ClientOps(javaClient: Client) {

    def executeRequest[Request, Response](request: Request)(
      implicit action: ActionMagnet[Request, Response]): Future[Response] =
      action.execute(javaClient, request)

    private def executeAndWaitForYellow[A](clientFuture: Client ⇒ Future[A])(implicit log: Logger): Future[A] =
      for {
        result ← clientFuture(javaClient)
        isYellow ← ESClient.waitForNonRedHealthStatus(javaClient)
      } yield {
        result
      }

    def createIndex(indexName: String)(implicit log: Logger): Future[CreateIndexResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.createIndex(client, indexName))

    def closeIndex(indexName: String)(implicit log: Logger): Future[CloseIndexResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.closeIndex(client, indexName))

    def openIndex(indexName: String)(implicit log: Logger): Future[OpenIndexResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.openIndex(client, indexName))

    def deleteIndex(indexName: String)(implicit log: Logger): Future[DeleteIndexResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.deleteIndex(client, indexName))

    def updateSettings(indexName: String, source: String)(implicit log: Logger): Future[UpdateSettingsResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.updateSettings(client, indexName, source))

    def insert(indexName: String, typeName: String, source: String)(implicit log: Logger): Future[IndexResponse] =
      ESClient.insert(javaClient, indexName, typeName, source)

    def putMapping(indexName: String, typeName: String, source: String)(implicit log: Logger): Future[PutMappingResponse] =
      executeAndWaitForYellow(client ⇒ ESClient.putMapping(client, indexName, typeName, source))

    def getMapping(indexName: String, typeName: String)(implicit log: Logger): Future[GetMappingsResponse] =
      ESClient.getMapping(javaClient, indexName, typeName)

    def waitForYellowHealthStatus()(implicit log: Logger): Future[Boolean] =
      ESClient.waitForNonRedHealthStatus(javaClient)
  }
}

object ESClient {

  import ClientPimper._

  /**
    * Creates an ElasticClient connected to the elasticsearch instance(s) specified by the uri.
    * This method will use default settings.
    *
    * Note: The method name 'transport' refers to the fact that the client will connect to the instance(s)
    * using the transport client rather than becoming a full node itself and joining the cluster.
    * This is what most people think of when they talk about a client, like you would in mongo or mysql for example.
    * To create a local node, use the fromNode method.
    *
    * @param uri the instance(s) to connect to.
    */
  def transport(uri: ESClientURI)(implicit log: Logger): Client = transport(Settings.builder.build, uri)

  /**
    * Connects to elasticsearch instance(s) specified by the uri and setting the
    * given settings object on the client.
    *
    * Note: The method name 'transport' refers to the fact that the client will connect to the instance(s)
    * using the transport client rather than becoming a full node itself and joining the cluster.
    * This is what most people think of when they talk about a client, like you would in mongo or mysql for example.
    * To create a local node, use the fromNode method.
    *
    * @param settings the settings as applicable to the client.
    * @param uri the instance(s) to connect to.
    */
  def transport(settings: Settings, uri: ESClientURI)(implicit log: Logger): Client = {
    val client = TransportClient.builder.settings(settings).build()
    log.info(s"Creating an Elasticsearch client with settings [${settings.getAsMap}]")
    for ((host, port) ← uri.hosts) {
      log.debug(s"Elasticsearch client setting transport address $host:$port")
      client.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(host, port)))
    }
    client
  }

  def transport(uri: ESClientURI, clusterName: String)(implicit log: Logger): Client = {
    val settings = Settings.settingsBuilder().put("cluster.name", clusterName).build()
    transport(settings, uri)
  }

  /**
    * Creates a local data node. This is useful for embedded usage, or for unit tests.
    * Default settings will be applied.
    */
  def local()(implicit log: Logger): Client = local(Settings.settingsBuilder().build())

  /**
    * Creates a local data node. This is useful for embedded usage, or for unit tests.
    *
    * @param settings the settings object to set on the node
    */
  def local(settings: Settings)(implicit log: Logger): Client = {
    log.info(s"Creating an Elasticsearch client with settings [${settings.getAsMap}]")
    val node = NodeBuilder.nodeBuilder().local(true).data(true).settings(settings).node()
    node.client
  }

  def createIndex(client: Client, indexName: String)(implicit log: Logger): Future[CreateIndexResponse] = {
    log.info(s"Creating index [$indexName]")
    val request = new CreateIndexRequestBuilder(client, CreateIndexAction.INSTANCE).setIndex(indexName).request()
    client.executeRequest(request)
  }

  def closeIndex(client: Client, indexName: String)(implicit log: Logger): Future[CloseIndexResponse] = {
    log.info(s"Closing index [$indexName]")
    val javaFuture = client.admin().indices().close(new CloseIndexRequest(indexName))
    Future {
      javaFuture.get()
    }
  }

  def openIndex(client: Client, indexName: String)(implicit log: Logger): Future[OpenIndexResponse] = {
    log.info(s"Opening index [$indexName]")
    val javaFuture = client.admin().indices().open(new OpenIndexRequest(indexName))
    Future {
      javaFuture.get()
    }
  }

  def deleteIndex(client: Client, indexName: String)(implicit log: Logger): Future[DeleteIndexResponse] = {
    log.info(s"Deleting index [$indexName]")
    val request = new DeleteIndexRequestBuilder(client, DeleteIndexAction.INSTANCE).request().indices(indexName)
    val javaFuture = client.admin().indices().delete(request)
    Future {
      javaFuture.get()
    }
  }

  // unused / untested
  def getAliases(client: Client)(implicit log: Logger) = {
    log.info(s"Getting aliases")
    val javaFuture = client.admin().indices().getAliases(new GetAliasesRequest())
    Future {
      javaFuture.get().getAliases.asScala.map {
        case objectObjectCursor ⇒
          val key = objectObjectCursor.key
          val value = objectObjectCursor.value
          val aliases = value.iterator.asScala.toList.map(_.alias)
          key -> aliases
      }.toList
    }
  }

  def updateSettings(client: Client, indexName: String, source: String)(implicit log: Logger): Future[UpdateSettingsResponse] = {
    log.info(s"Updating settings for index [$indexName]")
    val request = new UpdateSettingsRequestBuilder(client, UpdateSettingsAction.INSTANCE)
      .setIndices(indexName)
      .setSettings(source)
      .request()
    client.executeRequest(request)
  }

  def insert(client: Client, indexName: String, typeName: String, source: String)(implicit log: Logger): Future[IndexResponse] = {
    log.info(s"Inserting document to index [$indexName]")
    val request = new IndexRequestBuilder(client, IndexAction.INSTANCE)
      .setIndex(indexName).setType(typeName).setSource(source)
      .request()
    client.executeRequest(request)
  }

  def putMapping(client: Client, indexName: String, typeName: String, source: String)(implicit log: Logger): Future[PutMappingResponse] = {
    log.info(s"Defining mapping for index [$indexName]")
    val request = new PutMappingRequestBuilder(client, PutMappingAction.INSTANCE)
      .setIndices(indexName).setType(typeName).setSource(source)
      .request()
    client.executeRequest(request)
  }

  def getMapping(client: Client, indexName: String, typeName: String)(implicit log: Logger): Future[GetMappingsResponse] = {
    log.info(s"Getting mapping for index [$indexName]")
    val request = new GetMappingsRequestBuilder(client, GetMappingsAction.INSTANCE)
      .setIndices(indexName).setTypes(typeName)
      .request()
    client.executeRequest(request)
  }

  def waitForNonRedHealthStatus(client: Client)(implicit log: Logger): Future[Boolean] = {
    log.info(s"Waiting for green or yellow health status...")
    val javaFuture = client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute()
    Future {
      val name = javaFuture.get().getStatus.name()
      val isRed = name == "RED"
      if (isRed) {
        log.error(s"cluster health changed to RED")
      }
      !isRed
    }
  }
}

