package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.example.services._

object TypedSagaActor {

  case class TypeState(
                        orderId: String,
                        emailService: ActorRef[EmailServiceActor.SendEmail],
                        inventory: ActorRef[InventoryActor.ReserveItem],
                        payment: ActorRef[PaymentActor.ProcessPayment],
                        inventoryResponseMapper: ActorRef[InventoryActor.ReservationResponse],
                        emailResponseMapper: ActorRef[EmailServiceActor.EmailServiceResponse],
                        paymentResponseWrapper: ActorRef[PaymentActor.PaymentResponse]
                      ) extends CborSerializable

  sealed trait SagaCommand extends CborSerializable

  case class ProcessTransaction(order: Order) extends SagaCommand
  case object SpanishInquisition extends SagaCommand //Nobody expects the Spanish Inquisition!

  // Adapted responses - https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#adapted-response
  private final case class WrappedEmailServiceResponse(response: EmailServiceActor.EmailServiceResponse)
    extends SagaCommand
  private final case class WrappedInventoryResponse(response: InventoryActor.ReservationResponse) extends SagaCommand
  private final case class WrappedPaymentResponse(response: PaymentActor.PaymentResponse) extends SagaCommand

  def apply(
      orderId: String,
      emailService: ActorRef[EmailServiceActor.SendEmail],
      inventory: ActorRef[InventoryActor.ReserveItem],
      payment: ActorRef[PaymentActor.ProcessPayment]
  ): Behavior[SagaCommand] = Behaviors.setup { context =>
    ready(
      TypeState(
        orderId,
        emailService,
        inventory,
        payment,
        context.messageAdapter(rsp => WrappedInventoryResponse(rsp)),
        context.messageAdapter(rsp => WrappedEmailServiceResponse(rsp)),
        context.messageAdapter(rsp => WrappedPaymentResponse(rsp))
      ),
      context
    )
  }

  def ready(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] = Behaviors.receiveMessage {
    case ProcessTransaction(order) =>
      context.log.info(s"Processing order ${order.orderId}")
      state.inventory ! InventoryActor.ReserveItem(order, state.inventoryResponseMapper)
      waitingOnInventoryResponse(state, context)
    case msg =>
      context.log.info(s"Unexpected message $msg in ready state")
      Behaviors.same
  }

  def waitingOnInventoryResponse(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] = Behaviors.receiveMessage{
    case WrappedInventoryResponse(msg) =>
      msg match {
        case InventoryActor.ReservationMade(orderId, itemId, quantity) =>
          context.log.info(s"Received item reservation for $quantity $itemId")
          state.payment ! PaymentActor.ProcessPayment(PaymentActor.Amount(1,99),orderId, state.paymentResponseWrapper)
          Behaviors.same
        case InventoryActor.ReservationFailed(orderId, _, _, cause) =>
          context.log.error(s"Transaction $orderId failed - $cause")
          Behaviors.same
      }
    case msg =>
      context.log.info(s"Unexpected message: $msg")
      context.log.info("Currently awaiting inventory response")
      Behaviors.same
  }

}
