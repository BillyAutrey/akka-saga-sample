package com.example.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.example.CborSerializable

object EmailServiceActor {

  sealed trait EmailCommand extends CborSerializable

  case class SendEmail(address: String, orderId: String, ref: ActorRef[EmailServiceResponse]) extends EmailCommand
  sealed trait EmailServiceResponse extends CborSerializable
  case class EmailSucceeded(orderId: String) extends EmailServiceResponse
  case class EmailFailed(orderId: String, cause: String) extends EmailServiceResponse

  def apply(): Behavior[EmailCommand] = {
    Behaviors.receiveMessage[EmailCommand]{
      case SendEmail(address, orderId, ref) =>
        ref ! EmailSucceeded(orderId)
        Behaviors.same
    }
  }
}
