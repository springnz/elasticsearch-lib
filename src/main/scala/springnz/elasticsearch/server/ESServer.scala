package springnz.elasticsearch.server

import java.nio.file.Files

import org.apache.commons.io.FileUtils
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.NodeBuilder._
import springnz.util.Logging

import scala.util.Try

// adapted from https://orrsella.com/2014/10/28/embedded-elasticsearch-server-for-scala-integration-tests/
case class ESServerParams(httpPort: Option[Int] = Some(9200), extraConfig: Map[String, String] = Map.empty)

class ESServer(clusterName: String, serverParams: ESServerParams = ESServerParams()) extends Logging {

  //  private val clusterName = "neon-search"
  private val dataDirPath = Files.createTempDirectory(s"data-$clusterName-")
  private val dataDir = dataDirPath.toFile

  log.info(s"Logging ESServer for cluster '$clusterName' to '$dataDir'")

  private val settings = {
    val _settings = Settings.settingsBuilder
      .put("path.home", "/usr/local/elasticsearch-2.0.0/bin")
      .put("path.data", dataDir.toString)
      .put("cluster.name", clusterName)

    serverParams.httpPort match {
      case Some(port) ⇒
        _settings.put("http.enabled", "true").put("http.port", port)
      case None ⇒
        _settings.put("http.enabled", "false")
    }

    for ((key,value) <- serverParams.extraConfig) {
      if (key.nonEmpty && value.nonEmpty)
        _settings.put(key, value)
    }

    _settings.build()
  }

  private lazy val node = nodeBuilder().local(false).settings(settings).build

  def client: Client = node.client

  def isClosed = node.isClosed

  def start(): Unit = {
    log.info(s"Starting embedded server node for cluster '$clusterName'")
    node.start()
  }

  def stop(): Unit = {
    log.info(s"Stopping embedded server node for cluster '$clusterName'")
    node.close()
    Try {
      log.info(s"Deleting folder '$dataDir'")
      FileUtils.forceDelete(dataDir)
    }
  }
}

