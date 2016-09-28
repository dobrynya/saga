package ru.dimitrius.saga

import org.scalatest._

class SagaPartSpec extends FlatSpec with Matchers {
  behavior of "SagaPart"

  it should "rollback" in {
    var rolledBack = false
    val sp = new SagaPart[String](null, _ => rolledBack = true)
    sp.rollback(null)
    rolledBack should be(true)
  }

  it should "run" in {
    val sp = new SagaPart[String]("success", _ => ())
    sp.run should be("success")
  }
}
