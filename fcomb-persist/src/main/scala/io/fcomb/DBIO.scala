package io.fcomb

import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.language.implicitConversions
import scalikejdbc._
import scalaz._
import java.sql.Connection

object DBIO {
  sealed trait DBException extends Throwable
  case object RecordNotFound extends DBException

  object TransactionLevel extends Enumeration {
    import Connection._

    type TransactionLevel = Value

    val None = Value(TRANSACTION_NONE)
    val ReadCommitted = Value(TRANSACTION_READ_COMMITTED)
    val ReadUncommitted = Value(TRANSACTION_READ_UNCOMMITTED)
    val RepeatableRead = Value(TRANSACTION_REPEATABLE_READ)
    val Serializable = Value(TRANSACTION_SERIALIZABLE)
  }

  sealed trait DBIOAction[+R] {
    def flatMap[R2](f: R => DBIOAction[R2]): DBIOAction[R2] =
      FlatMapAction[R2, R](this, f)

    def map[R2](f: R => R2) =
      flatMap(r => successful(f(r)))

    def transactionally() =
      SynchronousDatabaseAction.transactionally(this, None)

    def transactionally(level: TransactionLevel.TransactionLevel) =
      SynchronousDatabaseAction.transactionally(this, Some(level))
  }

  sealed trait SynchronousDatabaseAction[+R] extends DBIOAction[R] {
    def run()(implicit session: DBSession): R
  }

  sealed trait SynchronousDatabaseUnitAction[+R] extends DBIOAction[R] {
    def run()(implicit session: DBSession): DBIOAction[R]
  }

  case class SuccessAction[+R](value: R) extends SynchronousDatabaseAction[R] {
    def run()(implicit session: DBSession) = value
  }

  case class FailureAction(t: DBException) extends SynchronousDatabaseAction[Nothing] {
    def run()(implicit session: DBSession) = throw t
  }

  case class FutureAction[+R](f: Future[R]) extends DBIOAction[R]

  case class FlatMapAction[+R, P](base: DBIOAction[P], f: P => DBIOAction[R]) extends DBIOAction[R]

  object SynchronousDatabaseAction {
    private[fcomb] sealed trait RawSqlAction[+R] extends SynchronousDatabaseAction[R]
    private[fcomb] sealed trait TransactionAction[+R] extends SynchronousDatabaseUnitAction[R]

    private def setTransactionIsolation(level: TransactionLevel.TransactionLevel)(implicit session: DBSession) =
      session.connection.setTransactionIsolation(level.id)

    def transactionally[R](base: DBIOAction[R], level: Option[TransactionLevel.TransactionLevel]): DBIOAction[R] =
      new TransactionAction[R] {
        def run()(implicit session: DBSession) = {
          level.map(setTransactionIsolation)
          base
        }
      }

    def action[R](q: SQLToOption[R, HasExtractor]): DBIOAction[Option[R]] =
      new RawSqlAction[Option[R]] {
        def run()(implicit session: DBSession) = q.apply
      }

    def action[R](q: SQLToList[R, HasExtractor]): DBIOAction[List[R]] =
      new RawSqlAction[List[R]] {
        def run()(implicit session: DBSession) = q.apply
      }

    def apply[R](q: SQL[R, HasExtractor]) = q match {
      case l: SQLToList[R, HasExtractor]   => action(l)
      case o: SQLToOption[R, HasExtractor] => action(o)
    }
  }

  def fold[R](q: Any, default: R)(f: (R, R) => Unit): DBIOAction[R] = {
    ???
  }

  def successful[R](res: R): DBIOAction[R] = SuccessAction(res)

  def failure[R](e: DBException): DBIOAction[Nothing] = FailureAction(e)

  private[fcomb] object sameThreadExecutionContext extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(t: Throwable): Unit = throw t
  }

  private def runInContext[R](q: => DBIOAction[R])(implicit session: DBSession, ec: ExecutionContext): Future[DBException \/ R] =
    q match {
      case SuccessAction(res) =>
        Future.successful(\/-(res))
      case FutureAction(f) =>
        f.map(\/-(_))
      case a: SynchronousDatabaseAction.RawSqlAction[R] =>
        Future {
          blocking {
            \/-(a.run())
          }
        }
      case t: SynchronousDatabaseAction.TransactionAction[DBIOAction[R]] =>
        if (ec == sameThreadExecutionContext) runInContext(t.run())
        else DB.futureLocalTx { newSession =>
          Future.successful(()).flatMap { _ =>
            runInContext(t.run()(newSession))(newSession, sameThreadExecutionContext)
          }
        }
      case fm: FlatMapAction[_, _] =>
        runInContext(fm.base).flatMap {
          case \/-(r) =>
            runInContext(fm.f(r))
          case e @ -\/(_) =>
            Future.successful(e)
        }
      case k =>
        throw new Exception(s"Unsupported this kind of action: ${k.getClass.getSimpleName}")
    }

  def run[R](q: => DBIOAction[R])(implicit session: DBSession, ec: ExecutionContext): Future[DBException \/ R] =
    runInContext(q)

  implicit class SqlWrapper[R](val sql: SQL[R, HasExtractor]) extends AnyVal {
    def dbio() = SynchronousDatabaseAction(sql)
  }
}
