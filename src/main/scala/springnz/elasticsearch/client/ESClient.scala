package springnz.elasticsearch.client

import java.net.InetSocketAddress

import org.elasticsearch.action.admin.indices.close.{ CloseIndexRequest, CloseIndexResponse }
import org.elasticsearch.action.admin.indices.create.{ CreateIndexAction, CreateIndexRequestBuilder, CreateIndexResponse }
import org.elasticsearch.action.admin.indices.mapping.get.{ GetMappingsAction, GetMappingsRequestBuilder, GetMappingsResponse }
import org.elasticsearch.action.admin.indices.mapping.put.{ PutMappingAction, PutMappingRequestBuilder, PutMappingResponse }
import org.elasticsearch.action.admin.indices.settings.put.{ UpdateSettingsAction, UpdateSettingsRequestBuilder, UpdateSettingsResponse }
import org.elasticsearch.action.index.{ IndexAction, IndexRequestBuilder, IndexResponse }
import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.node.NodeBuilder
import springnz.util.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

object ClientPimper {

  implicit class ClientOps(javaClient: Client) {
    def execute[Request, Response](request: Request)(
      implicit action: ActionMagnet[Request, Response]): Future[Response] =
      action.execute(javaClient, request)

    def createIndex(indexName: String): Future[CreateIndexResponse] =
      ESClient.createIndex(javaClient, indexName)

    def closeIndex(indexName: String): Future[CloseIndexResponse] =
      ESClient.closeIndex(javaClient, indexName)

    def updateSettings(indexName: String, source: String): Future[UpdateSettingsResponse] =
      ESClient.updateSettings(javaClient, indexName, source)

    def insert(indexName: String, typeName: String, source: String): Future[IndexResponse] =
      ESClient.insert(javaClient, indexName, typeName, source)

    def putMapping(indexName: String, typeName: String, source: String): Future[PutMappingResponse] =
      ESClient.putMapping(javaClient, indexName, typeName, source)

    def getMapping(indexName: String, typeName: String, source: String): Future[GetMappingsResponse] =
      ESClient.getMapping(javaClient, indexName, typeName, source)
  }
}

object ESClient extends Logging {

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
  def transport(uri: ESClientURI): Client = transport(Settings.builder.build, uri)

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
  def transport(settings: Settings, uri: ESClientURI): Client = {
    val client = TransportClient.builder.settings(settings).build()
    log.info(s"Creating an Elasticsearch client with settings: $settings")
    for ((host, port) ← uri.hosts) {
      client.addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress(host, port)))
    }
    client
  }

  /**
    * Creates a local data node. This is useful for embedded usage, or for unit tests.
    * Default settings will be applied.
    */
  def local: Client = local(Settings.settingsBuilder().build())

  /**
    * Creates a local data node. This is useful for embedded usage, or for unit tests.
    * @param settings the settings object to set on the node
    */
  def local(settings: Settings): Client = {
    val node = NodeBuilder.nodeBuilder().local(true).data(true).settings(settings).node()
    node.client
  }

  def createIndex(client: Client, indexName: String): Future[CreateIndexResponse] = {
    val request = new CreateIndexRequestBuilder(client, CreateIndexAction.INSTANCE).setIndex(indexName).request()
    client.execute(request)
  }

  def closeIndex(client: Client, indexName: String): Future[CloseIndexResponse] = {
    val javaFuture = client.admin().indices().close(new CloseIndexRequest(indexName))
    Future {
      javaFuture.get()
    }
  }

  def updateSettings(client: Client, indexName: String, source: String): Future[UpdateSettingsResponse] = {
    val request = new UpdateSettingsRequestBuilder(client, UpdateSettingsAction.INSTANCE).setSettings(source).request()
    client.execute(request)
  }

  def insert(client: Client, indexName: String, typeName: String, source: String): Future[IndexResponse] = {
    val request = new IndexRequestBuilder(client, IndexAction.INSTANCE)
      .setIndex(indexName).setType(typeName).setSource(source)
      .request()
    client.execute(request)
  }

  def putMapping(client: Client, indexName: String, typeName: String, source: String): Future[PutMappingResponse] = {
    val request = new PutMappingRequestBuilder(client, PutMappingAction.INSTANCE)
      .setIndices(indexName).setType(typeName).setSource(source)
      .request()
    client.execute(request)
  }

  def getMapping(client: Client, indexName: String, typeName: String, source: String): Future[GetMappingsResponse] = {
    val request = new GetMappingsRequestBuilder(client, GetMappingsAction.INSTANCE)
      .setIndices(indexName).setTypes(typeName)
      .request()
    client.execute(request)
  }
}

