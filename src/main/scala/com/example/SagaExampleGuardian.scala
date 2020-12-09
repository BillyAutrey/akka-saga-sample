package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.example.services._

object SagaExampleGuardian {

  def apply(): Behavior[ProcessOrder] = {
    Behaviors.setup { context =>
      val emailServiceActor = context.spawn(EmailServiceActor(), "emailServiceActor")
      val inventoryActor    = context.spawn(InventoryActor(), "inventoryActor")
      val paymentActor      = context.spawn(PaymentActor(), "paymentActor")

      listen(emailServiceActor, inventoryActor, paymentActor, context)
    }
  }

  def listen(
      emailServiceActor: ActorRef[EmailServiceActor.EmailCommand],
      inventoryActor: ActorRef[InventoryActor.InventoryCommand],
      paymentActor: ActorRef[PaymentActor.PaymentCommand],
      context: ActorContext[_]
  ): Behavior[ProcessOrder] = Behaviors.receiveMessage { msg =>
    val sagaActor = context.spawn(
      TypedSagaActor(msg.order.orderId, emailServiceActor, inventoryActor, paymentActor),
      s"order-${msg.order.orderId}"
    )
    sagaActor ! TypedSagaActor.ProcessTransaction(msg.order)
    Behaviors.same
  }

  case class ProcessOrder(order: Order) extends CborSerializable

}
