package com.example.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.example.CborSerializable

object PaymentActor {

  case class Amount(dollars: Int, cents: Int) extends CborSerializable
  sealed trait PaymentCommand                 extends CborSerializable

  case class ProcessPayment(amount: Amount, orderId: String, ref: ActorRef[PaymentResponse]) extends PaymentCommand
  case class RefundPayment(amount: Amount, orderId: String, ref: ActorRef[RefundResponse])   extends PaymentCommand

  sealed trait PaymentResponse extends CborSerializable

  sealed trait ProcessResponse                                              extends PaymentResponse
  case class PaymentSucceeded(amount: Amount, orderId: String)              extends PaymentResponse
  case class PaymentFailed(orderId: String, amount: Amount, reason: String) extends PaymentResponse

  sealed trait RefundResponse                                             extends PaymentResponse
  case class RefundSucceeded(amount: Amount, orderId: String)             extends RefundResponse
  case class RefundFailed(amount: Amount, orderId: String, cause: String) extends RefundResponse

  def apply(): Behavior[PaymentCommand] = Behaviors.receiveMessage[PaymentCommand] {
    case ProcessPayment(amount, orderId, ref) =>
      amount match {
        case Amount(dollars, cents) if dollars > 500 =>
          ref ! PaymentFailed(orderId, amount, "Payment amount is too high.  Must be < $500")
        case amount =>
          ref ! PaymentSucceeded(amount, orderId)
      }
      Behaviors.same
    case RefundPayment(amount, orderId, ref) =>
      ref ! RefundSucceeded(amount, orderId)
      Behaviors.same
  }

}
