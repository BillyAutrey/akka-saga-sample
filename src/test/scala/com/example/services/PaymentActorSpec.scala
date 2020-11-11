package com.example.services

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class PaymentActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike{

  import PaymentActor._

  "PaymentActor" should {
    "respond PaymentSucceeded for a small payment" in {
      val amount = Amount(100,0)
      val paymentActor = testKit.spawn(PaymentActor())
      val probe = testKit.createTestProbe[PaymentResponse]()
      paymentActor ! ProcessPayment(amount, "123", probe.ref)
      probe.expectMessage(PaymentSucceeded(amount,"123"))
    }
    "respond PaymentFailed for a large payment" in {
      val amount = Amount(1000,0)
      val paymentActor = testKit.spawn(PaymentActor())
      val probe = testKit.createTestProbe[PaymentResponse]()
      paymentActor ! ProcessPayment(amount, "123", probe.ref)
      probe.expectMessage(PaymentFailed("123","Payment amount is too high.  Must be < $500"))
    }
  }

}
