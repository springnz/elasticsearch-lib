package springnz.elasticsearch.utils

import java.io.File

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants

object Zip4jUtil {

  @throws(classOf[ZipException])
  def zip(sourcePath: String, destinationFilePath: String, password: Option[String] = None): Unit = {

    val parameters = new ZipParameters
    parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE)
    parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL)
    password.foreach { pass ⇒
      parameters.setEncryptFiles(true)
      parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES)
      parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256)
      parameters.setPassword(pass)
    }

    val zipFile = new ZipFile(destinationFilePath)
    val targetFile = new File(sourcePath)

    if (targetFile.isFile) {
      zipFile.addFile(targetFile, parameters)
    } else {
      zipFile.addFolder(targetFile, parameters)
    }
  }

  @throws(classOf[ZipException])
  def unzip(sourceFilePath: String, destinationDirectoryPath: String, password: Option[String] = None): Unit = {
    val zipFile = new ZipFile(sourceFilePath)
    if (zipFile.isEncrypted) {
      password match {
        case Some(pass) ⇒
          zipFile.setPassword(pass)
        case None ⇒
          throw new ZipException(s"Need a password to unzip $sourceFilePath")
      }
    }
    zipFile.extractAll(destinationDirectoryPath)
  }
}
