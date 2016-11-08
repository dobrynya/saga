package ru.dimitrius.saga

import shapeless._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a part of a Saga.
  * @param action action to be completed
  * @param rollback compensation in case of a failure in other branches
  * @tparam T result type
  */
class SagaPart[T](action: => T, val rollback: T => Unit) {
  def run: T = action
}

/**
  * Contains errors in case of failed saga.
  * @param msg description message
  * @param reasons contains errors
  */
case class SagaFailed(msg: String, reasons: List[Throwable]) extends Exception(msg)

/**
  * Builds saga and run it.
  * @tparam S result type
  */
class SagaBuilder[S <: HList] {
  private var parts = List.empty[SagaPart[_]]

  type ResultAndRollback = (Any, Any => Unit)

  /**
    * Adds a part or a branch to a saga
    * @param action action to be completed
    * @param rollback compensation in case of a failure in other branches
    * @tparam T action result type
    * @return current saga
    */
  def part[T](action: => T, rollback: T => Unit): SagaBuilder[T :: S] = {
    parts ::= new SagaPart[T](action, rollback)
    this.asInstanceOf[SagaBuilder[T :: S]]
  }

  /**
    * Runs a saga.
    * @param ec specifies execution context
    * @return result of saga execution as a future
    */
  def run(implicit ec: ExecutionContext): Future[S] = {
    val actions = Future.sequence(parts map makeFuture)

    actions.flatMap {
      case results if results.forall(_.isRight) =>
        val list = results.collect {
          case Right((result, _)) => result
        }
        Future.successful(makeResult(list))

      case results => // rollback successful saga parts
        val rollingBack = results.collect {
          case Right((result, rollback)) =>
            Future(rollback(result)).recover {
              case _ =>
            }
        }
        val errors = results.collect {
          case Left(th) => th
        }

        Future.sequence(rollingBack).flatMap { _ =>
          Future.failed(new SagaFailed("Saga has been rolled back!", errors))
        } recoverWith {
          case failed: SagaFailed => Future.failed(failed)
          case th => Future.failed(new SagaFailed("Saga could not be rolled back!", List(th)))
        }
    }
  }

  private[saga] def makeFuture(part: SagaPart[_])(implicit ec: ExecutionContext): Future[Throwable Either ResultAndRollback] = {
    val sp = part.asInstanceOf[SagaPart[Any]]
    Future(Right(sp.run -> sp.rollback)) recover {
      case th: Throwable => Left(th)
    }
  }

  private[saga] def makeResult(results: List[_]): S =
    results.foldRight[HList](HNil) {
      (part, list) => part :: list
    }.asInstanceOf[S]
}

/**
  * Creates saga.
  */
object SagaBuilder {
  def saga: SagaBuilder[HNil] = new SagaBuilder[HNil]
}


