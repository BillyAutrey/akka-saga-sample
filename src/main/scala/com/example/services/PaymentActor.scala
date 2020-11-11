package com.example.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.CborSerializable

object PaymentActor {

  case class Amount(dollars: Int, cents: Int) extends CborSerializable
  case class ProcessPayment(amount: Amount, orderId: String, ref: ActorRef[PaymentResponse]) extends CborSerializable

  sealed trait PaymentResponse extends CborSerializable
  case class PaymentSucceeded(amount: Amount, orderId: String) extends PaymentResponse
  case class PaymentFailed(orderId: String, reason: String) extends PaymentResponse

  def apply(): Behavior[ProcessPayment] = Behaviors.receiveMessage{
    msg =>
      msg.amount match {
        case Amount(dollars, cents) if dollars > 500 =>
          msg.ref ! PaymentFailed(msg.orderId, "Payment amount is too high.  Must be < $500")
        case amount =>
          msg.ref ! PaymentSucceeded(amount, msg.orderId)
      }
      Behaviors.same
  }

}
