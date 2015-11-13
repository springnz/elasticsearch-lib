package springnz.elasticsearch

import org.elasticsearch.client.Client

import scala.concurrent.Future
import scala.language.implicitConversions

package object client {
  implicit class ElasticsearchClient(val javaClient: Client) extends AnyVal {
    def execute[Request, Response](request: Request)(
      implicit action: ActionMagnet[Request, Response]): Future[Response] =
      action.execute(javaClient, request)
  }
}
