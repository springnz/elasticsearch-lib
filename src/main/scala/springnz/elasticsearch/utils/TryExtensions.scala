package springnz.elasticsearch.utils

import com.typesafe.scalalogging.Logger

import scala.util.{ Failure, Success, Try }

object TryExtensions {
  implicit class TryPimper[A](t: Try[A]) {
    def withErrorLog(msg: String)(implicit log: Logger): Try[A] =
      t.recoverWith {
        case e ⇒
          log.error(msg, e)
          Failure(e)
      }

    def withFinally[T](block: ⇒ T): Try[A] = {
      block
      t
    }
  }

  implicit class TraversableTryPimper[A](travTry: Traversable[Try[A]]) {
    def sequence: Try[Traversable[A]] = {
      val empty: Try[Traversable[A]] = Success(Traversable.empty)

      travTry.foldRight(empty) {
        case (triedValue, triedResults) ⇒
          for {
            nextResult ← triedValue
            previousResults ← triedResults
          } yield Traversable(nextResult) ++ previousResults
      }
    }
  }
}
