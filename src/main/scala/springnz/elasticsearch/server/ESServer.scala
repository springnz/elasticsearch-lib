package springnz.elasticsearch.server

import java.nio.file.Files

import better.files._
import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.commons.io.FileUtils
import org.elasticsearch.Version
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.NodeBuilder._
import springnz.elasticsearch.utils.Logging

import scala.util.Try

case class ESServerConfig(
    clusterName: String,
    transportPort: Int = 9300,
    httpPort: Option[Int] = Some(9200),
    dataWriteDir: Option[String] = None,
    snapshotRepoDir: Option[String] = None,
    snapshotRepoNames: Seq[String] = Seq.empty[String],
    extraConfig: Map[String, String] = Map.empty) {

  def withTransportPort(port: Int) = this.copy(transportPort = port)
  def withHttpPort(port: Int) = this.copy(httpPort = Some(port))
  def withDataWriteDir(dir: String) = this.copy(dataWriteDir = Some(dir))
  def withSnapshotRepoDir(dir: String) = this.copy(snapshotRepoDir = Some(dir))
  def withSnapshotRepoNames(repoNames: Seq[String]) = this.copy(snapshotRepoNames = repoNames)
  def withExtraConfig(extraConfig: Map[String, String]) = this.copy(extraConfig = extraConfig)
}

object ESServerConfig {
  def apply(clusterName: String) = new ESServerConfig(clusterName)
}

class ESServer(serverConfig: ESServerConfig, config: Config = ConfigFactory.load()) extends Logging {

  private val dataDir = serverConfig.dataWriteDir.getOrElse {
    Files.createTempDirectory(s"data-${serverConfig.clusterName}-").toFile.toString
  }

  private val esPluginDir = config.getString("elasticsearch-lib.elasticsearch-plugin-dir")

  log.info(s"Logging ESServer for cluster '${serverConfig.clusterName}' to '$dataDir'")
  log.info(s"Setting path.plugins to [$esPluginDir]")

  private val settings = {
    val _settings = Settings.settingsBuilder
      .put("path.home", "/tmp/this-directory-must-not-exist")
      .put("path.data", dataDir.toString)
      .put("path.plugins", esPluginDir)
      .put("index.version.created", Version.V_2_2_2_ID.toString)
      .put("cluster.name", serverConfig.clusterName)
      .put("transport.tcp.port", serverConfig.transportPort)
      .putArray("path.repo", serverConfig.snapshotRepoNames: _*)
    serverConfig.snapshotRepoDir.foreach { snapshotRepoPath ⇒
      _settings.put("path.repo.0", snapshotRepoPath)
    }
    serverConfig.httpPort match {
      case Some(port) ⇒
        _settings.put("http.enabled", "true").put("http.port", port)
      case None ⇒
        _settings.put("http.enabled", "false")
    }
    for ((key, value) ← serverConfig.extraConfig) {
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
    log.info(s"Starting embedded server node for cluster '${serverConfig.clusterName}'")
    node.start()
  }

  def stop(): Unit = {
    log.info(s"Stopping embedded server node for cluster '${serverConfig.clusterName}'")
    node.close()
    Try {
      log.info(s"Deleting directory '$dataDir'")
      FileUtils.forceDelete(dataDir.toFile.toJava)
    }
  }
}

