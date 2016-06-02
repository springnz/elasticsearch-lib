package springnz.elasticsearch.utils

import com.typesafe.scalalogging.{Logger, LazyLogging}


private[elasticsearch] trait Logging extends LazyLogging {
  implicit lazy val log: Logger = logger
}
