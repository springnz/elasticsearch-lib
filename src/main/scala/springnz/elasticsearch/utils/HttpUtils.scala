package springnz.elasticsearch.utils

import com.ning.http.client.Response
import dispatch.{Http, Req}
import org.json4s.{JValue, _}
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Await
import scala.concurrent.duration._

object HttpUtils {

  case class ResponseResult(response: Response, json: JValue)

  implicit class ReqOps(request: Req) {

    def processHttpRequest(duration: Duration = 10 seconds): ResponseResult = {
      val requestAdded = request.addQueryParameter("wait_for_completion", "true")

      import scala.concurrent.ExecutionContext.Implicits.global
      val response: Response = Await.result(Http(requestAdded), duration)
      val responseBody = response.getResponseBody
      val responseJson: JValue = parse(responseBody)
      ResponseResult(response = response, json = responseJson)
    }
  }
}
