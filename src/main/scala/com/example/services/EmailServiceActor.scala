package com.example.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.example.CborSerializable

object EmailServiceActor {

  case class SendEmail(address: String, orderId: String, ref: ActorRef[EmailServiceResponse]) extends CborSerializable
  sealed trait EmailServiceResponse extends CborSerializable
  case class EmailSucceeded(orderId: String) extends EmailServiceResponse
  case class EmailFailed(orderId: String, cause: String) extends EmailServiceResponse

  def apply(): Behavior[SendEmail] = {
    Behaviors.receiveMessage[SendEmail]{
      msg =>
        msg.ref ! EmailSucceeded(msg.orderId)
        Behaviors.same
    }
  }
}
