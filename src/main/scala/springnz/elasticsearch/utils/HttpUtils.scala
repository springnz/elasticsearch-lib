package springnz.elasticsearch.utils

import com.ning.http.client.Response
import dispatch.{Http, Req}
import play.api.libs.json.{Json, JsValue}

import scala.concurrent.{ExecutionContext, Future}

object HttpUtils {

  case class ResponseResult(response: Response, json: JsValue)

  implicit class ReqOps(request: Req) {

    def processHttpRequest(waitForComplete: Boolean = false)(implicit ec: ExecutionContext): Future[ResponseResult] = {
      val requestAdded = if (waitForComplete) request.addQueryParameter("wait_for_completion", "true") else request
      Http(requestAdded).map { response =>
        val responseBody = response.getResponseBody
        val responseJson: JsValue = Json.parse(responseBody)
        ResponseResult(response = response, json = responseJson)
      }
    }
  }
}
