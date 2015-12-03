package springnz.elasticsearch.client

import java.net.InetSocketAddress

import org.elasticsearch.client.Client
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.node.NodeBuilder
import springnz.util.Logging

import scala.language.implicitConversions

object ESClient extends Logging {

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
}

