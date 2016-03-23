package springnz.elasticsearch.client

import scala.language.implicitConversions

object ESClientURI {

  private val PREFIX = "elasticsearch://"

  implicit def stringtoUri(str: String): ESClientURI = ESClientURI(str)

  def apply(host: String, port: Int): ESClientURI = apply(s"$PREFIX$host:$port")

  def apply(str: String): ESClientURI = {
    require(str != null && str.trim.nonEmpty, s"Invalid uri, must be in format ${PREFIX}host:port,host:port,...")
    val withoutPrefix = str.replace(PREFIX, "")
    val hosts = withoutPrefix.split(',').map { host ⇒
      val parts = host.split(':')
      if (parts.length == 2) {
        parts(0) -> parts(1).toInt
      } else {
        throw new IllegalArgumentException(s"Invalid uri, must be in format ${PREFIX}host:port,host:port,...")
      }
    }
    ESClientURI(str, hosts.toList)
  }
}

case class ESClientURI(uri: String, hosts: List[(String, Int)])

