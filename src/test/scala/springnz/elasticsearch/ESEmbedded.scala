package springnz.elasticsearch

import org.scalatest.fixture
import springnz.elasticsearch.client.{ ESClient, ESClientURI }
import springnz.elasticsearch.server.{ ESServer, ESServerConfig }
import springnz.elasticsearch.utils.Logging

trait ESEmbedded extends fixture.WordSpec with Logging {

  override type FixtureParam = ESServer

  val clusterName = "elasticsearch"
  val httpPort = 9200
  val transportPort = 9300

  val serverConfig = ESServerConfig(clusterName)
    .withHttpPort(httpPort)
    .withTransportPort(transportPort)

  def newClient() = ESClient.transport(ESClientURI("localhost", transportPort), clusterName)

  override def withFixture(test: OneArgTest) = {
    val server = new ESServer(serverConfig)
    try {
      server.start()
      withFixture(test.toNoArgTest(server)) // "loan" the fixture to the test
    } finally {
      server.stop()
    }
  }
}
