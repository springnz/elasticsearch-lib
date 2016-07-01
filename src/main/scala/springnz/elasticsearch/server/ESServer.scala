package springnz.elasticsearch.server

import java.io.File
import java.nio.file.Files

import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.commons.io.FileUtils
import org.elasticsearch.Version
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.NodeBuilder._
import springnz.elasticsearch.utils.Logging

import scala.util.Try

// adapted from https://orrsella.com/2014/10/28/embedded-elasticsearch-server-for-scala-integration-tests/
case class ESServerConfig(
  clusterName: String,
  httpPort: Option[Int] = Some(9200),
  transportPort: Option[Int] = Some(9200),
  snapshotRepoPath: Option[String] = None,
  repos: Seq[String] = Seq.empty[String],
  extraConfig: Map[String, String] = Map.empty)

class ESServer(serverParams: ESServerConfig, config: Config = ConfigFactory.load()) extends Logging {

  private val clusterName = serverParams.clusterName
  private val dataDirPath = Files.createTempDirectory(s"data-$clusterName-")
  private val dataDir: File = dataDirPath.toFile
  private val esPluginDir = config.getString("elasticsearch-lib.elasticsearch-plugin-dir")

  log.info(s"Logging ESServer for cluster '$clusterName' to '$dataDir'")
  log.info(s"Setting path.plugins to [$esPluginDir]")

  private val settings = {
    val _settings = Settings.settingsBuilder
      .put("path.home", "/tmp/this-directory-must-not-exist")
      .put("path.data", dataDir.toString)
      .put("path.plugins", esPluginDir)
      .put("index.version.created", Version.V_2_2_2_ID.toString)
      .put("cluster.name", clusterName)
      .putArray("path.repo", serverParams.repos: _*)

    serverParams.snapshotRepoPath.foreach { snapshotRepoPath ⇒
      _settings.put("path.repo.0", snapshotRepoPath)
    }

    serverParams.httpPort match {
      case Some(port) ⇒
        _settings.put("http.enabled", "true").put("http.port", port)
      case None ⇒
        _settings.put("http.enabled", "false")
    }
    serverParams.transportPort map (port ⇒ _settings.put("transport.tcp.port", port))

    for ((key, value) ← serverParams.extraConfig) {
      if (key.nonEmpty && value.nonEmpty)
        _settings.put(key, value)
    }

    _settings.build()
  }

  def client: Client = node.client

  def isClosed = node.isClosed

  private lazy val node = {
    // http://stackoverflow.com/questions/33975807/elasticsearch-jar-hell-error
    val originalClassPath = System.getProperty("java.class.path")
    val classPathEntries = originalClassPath.split(":")
    val esClasspath = new StringBuilder()
    log.info(s"Setting up classpath entries for ES embedded")
    classPathEntries.foreach { entry ⇒
      if (!entry.contains("elasticsearch-lib") && (entry.contains("elasticsearch") || entry.contains("lucene"))) {
        log.info(s"Appending classpath entry $entry")
        esClasspath.append(entry)
        esClasspath.append(":")
      }
    }
    System.setProperty("java.class.path", esClasspath.toString)

    val node = nodeBuilder().local(false).settings(settings).node()

    System.setProperty("java.class.path", originalClassPath)
    node
  }

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

