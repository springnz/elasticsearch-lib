package springnz.elasticsearch.utils

import com.ning.http.client.Response
import dispatch.{Http, Req}
import org.json4s.jackson.JsonMethods._
import org.json4s.{JValue, _}

import scala.concurrent.{ExecutionContext, Future}

object HttpUtils {

  case class ResponseResult(response: Response, json: JValue)

  implicit class ReqOps(request: Req) {

    def processHttpRequest(implicit ec: ExecutionContext): Future[ResponseResult] = {
      val requestAdded = request.addQueryParameter("wait_for_completion", "true")
      Http(requestAdded).map { response =>
        val responseBody = response.getResponseBody
        val responseJson: JValue = parse(responseBody)
        ResponseResult(response = response, json = responseJson)
      }
    }
  }
}
