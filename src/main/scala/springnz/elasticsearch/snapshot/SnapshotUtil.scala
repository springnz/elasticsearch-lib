package springnz.elasticsearch.snapshot

import java.nio.file.Files

import better.files._
import dispatch.url
import org.apache.commons.io.FileUtils
import org.json4s._
import org.json4s.jackson.JsonMethods._
import springnz.elasticsearch.snapshot.SnapshotUtil.SnapShot
import springnz.elasticsearch.utils.TryExtensions._
import springnz.elasticsearch.utils.{ HttpUtils, Logging, Zip4jUtil }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }
import scala.util.{ Failure, Success, Try }

class SnapshotUtil(port: Int, timeout: FiniteDuration = 1.minute) extends Logging {
  import HttpUtils._

  private val serverUrl = url(s"http://localhost:$port")

  def createSnapshot(
    destinationPath: String,
    repoName: String,
    snapshotName: String,
    indices: Seq[String]): Try[ResponseResult] = {

    val snapshotPath = destinationPath / repoName

    log.info(s"Creating snapshot at path [$snapshotPath] ...")

    def executeRegisterSnapshot(): ResponseResult = {
      val body =
        s"""
           |{
           |  "type": "fs",
           |  "settings": {
           |    "location": "${snapshotPath.pathAsString}",
           |    "compress": true
           |  }
           |}""".stripMargin
      val request = (serverUrl / "_snapshot" / repoName).setBody(body).PUT
      val resultFuture = request.processHttpRequest(waitForComplete = true)
      val result = Await.result(resultFuture, timeout)
      log.info(s"Result of registering snapshot repository: ${result.json}")
      result
    }

    def executeCreateSnapshot(indices: Seq[String]): ResponseResult = {
      val request = if (indices.nonEmpty) {
        // snapshot specified indices
        val body =
          s"""
             |{
             |  "indices": "${indices.mkString(",")}",
             |  "ignore_unavailable": "true",
             |  "include_global_state": false
             |}
             """.stripMargin
        (serverUrl / "_snapshot" / repoName / snapshotName).setBody(body).PUT
      } else {
        // snapshot everything in the cluster
        (serverUrl / "_snapshot" / repoName / snapshotName).PUT
      }
      val response = request.processHttpRequest(waitForComplete = true)
      val result = Await.result(response, timeout)
      log.info(s"Result of creating snapshot repository: ${result.json}")
      result
    }

    Try { FileUtils.forceDelete(snapshotPath.toJava) }
    lazy val registerSnapshotResult = executeRegisterSnapshot()
    lazy val createSnapshotResult = executeCreateSnapshot(indices)
    if (registerSnapshotResult.response.getStatusCode == 200 && createSnapshotResult.response.getStatusCode == 200) {
      Success(createSnapshotResult)
    } else {
      Failure(new RuntimeException(s"Error creating snapshot. Response: ${registerSnapshotResult.response.getResponseBody}"))
    }.withErrorLog("Error creating snapshot")
  }

  def createZippedSnapshot(
    destinationPath: String,
    repoName: String,
    snapshotName: String,
    indices: Seq[String])(implicit ec: ExecutionContext): Try[Unit] = {

    val snapshotPath = destinationPath / repoName
    val snapshotZip = destinationPath / s"$repoName.zip"

    val snapshotResult = createSnapshot(destinationPath, repoName, snapshotName, indices)
    snapshotResult.flatMap { _ ⇒
      Try {
        Zip4jUtil.zip(snapshotPath.pathAsString, snapshotZip.pathAsString)
        FileUtils.forceDelete(snapshotPath.toJava)
      }
    }.withErrorLog(s"Error creating zipped snapshot $snapshotZip")
  }

  private def addRepo(repoPath: String, repoName: String): ResponseResult = {
    val repoSettings =
      s"""
         |{
         |  "type": "fs",
         |  "settings": {
         |    "location": "$repoPath/$repoName",
         |    "compress": true
         |  }
         |}""".stripMargin

    log.info(s"Adding snapshot repo '$repoPath/$repoName' ... ")
    val responseFuture = (serverUrl / "_snapshot" / repoName)
      .setBody(repoSettings)
      .PUT
      .processHttpRequest(waitForComplete = true)
    val response = Await.result(responseFuture, timeout)
    log.info(s"Added snapshot repo '$repoName': $response")
    response
  }

  private def getSnapshotList(repoName: String): List[String] = {
    log.info(s"Getting list of snapshots for repo '$repoName' ... ")
    val responseFuture = (serverUrl / "_snapshot" / repoName / "_all")
      .GET
      .processHttpRequest(waitForComplete = true)
    val response = Await.result(responseFuture, timeout)
    implicit val formats = DefaultFormats
    val jValue = parse(response.json) \ "snapshots"
    val snapshots = jValue.extract[List[SnapShot]].map(_.snapshot)
    log.info(s"Found snapshots: $snapshots")
    snapshots
  }

  private def restoreSnapshot(repoName: String, snapshotName: String): ResponseResult = {
    log.info(s"Restoring snapshot '$repoName/$snapshotName' ... ")
    val responseFuture = (serverUrl / "_snapshot" / repoName / snapshotName / "_restore")
      .POST
      .processHttpRequest()
    val response = Await.result(responseFuture, timeout)
    log.info(s"Restored snapshot '$repoName': $response")
    Thread.sleep(1000) // TODO: check status and continue when ready
    response
  }

  def restoreRepo(repoSourcePath: String, repoName: String): Try[Seq[ResponseResult]] =
    Try {
      log.info(s"Restoring repo '$repoName'")
      addRepo(repoSourcePath, repoName)
      val snapshotList = getSnapshotList(repoName)
      val result = snapshotList.map(restoreSnapshot(repoName, _))
      Thread.sleep(5000) // TODO: check status and continue when ready
      result
    }.withErrorLog(s"Error restoring repo $repoSourcePath/$repoName")

  def restoreZippedRepo(repoSourcePath: String, repoName: String, repoRestorePath: String): Try[Seq[ResponseResult]] = {
    val filename = s"$repoSourcePath/$repoName.zip"
    Try {
      Zip4jUtil.unzip(filename, repoRestorePath)
    }.flatMap { _ ⇒
      restoreRepo(repoRestorePath, repoName)
    }.withErrorLog(s"Error restoring zipped repo $filename")
  }
}

object SnapshotUtil {
  case class SnapShot(snapshot: String) extends AnyVal
  def getSharedRepoPath: File = FileUtils.getTempDirectory.getPath / "es_repos_shared"
  def createTmpRepoPath: File = Files.createTempDirectory("es_repos_unzipped")
}
