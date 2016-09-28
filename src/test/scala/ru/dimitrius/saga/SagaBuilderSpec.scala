package ru.dimitrius.saga

import shapeless._
import org.scalatest._
import scala.util.Failure
import scala.concurrent.Await
import org.scalatest.concurrent.Futures
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class SagaBuilderSpec extends FlatSpec with Futures with Matchers {
  behavior of "SagaBuilder"

  it should "build and run a successful saga" in {
    val saga = SagaBuilder.saga
      .part[Int](1, _ => ())
      .part[String]("success", _ => ())
      .part[Boolean](true, _ => ())

    val result = saga.run.map {
      case bool :: str :: int :: HNil =>
        (bool, str, int)
    }

    Await.result(result, Duration.Inf) should matchPattern {
      case (true, "success", 1) =>
    }
  }

  it should "roll back all successfully completed actions in case of a failed action" in {
    val error = new IllegalStateException
    var rolledBack1 = false
    var rolledBack2 = false

    val sagaResult =
      SagaBuilder.saga
        .part[Int](throw error, _ => ())
        .part[String]("success", _ => rolledBack1 = true)
        .part[Boolean](true, _ => rolledBack2 = true)
        .run
    Await.ready(sagaResult, Duration.Inf)

    sagaResult.value should matchPattern {
      case Some(Failure(SagaFailed(_, List(`error`)))) if rolledBack1 && rolledBack2 =>
    }
  }
}
