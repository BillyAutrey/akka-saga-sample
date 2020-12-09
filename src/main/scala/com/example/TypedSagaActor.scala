package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.example.services._

object TypedSagaActor {

  sealed trait SagaCommand extends CborSerializable

  case class TypeState(
                        orderId: String,
                        order: Option[Order],
                        emailService: ActorRef[EmailServiceActor.EmailCommand],
                        inventory: ActorRef[InventoryActor.InventoryCommand],
                        payment: ActorRef[PaymentActor.PaymentCommand],
                        inventoryResponseMapper: ActorRef[InventoryActor.InventoryResponse],
                        emailResponseMapper: ActorRef[EmailServiceActor.EmailServiceResponse],
                        paymentResponseWrapper: ActorRef[PaymentActor.PaymentResponse]
                      ) extends CborSerializable

  case class ProcessTransaction(order: Order) extends SagaCommand
  case object SpanishInquisition              extends SagaCommand //Nobody expects the Spanish Inquisition!

  // Adapted responses - https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#adapted-response
  private final case class WrappedEmailResponse(response: EmailServiceActor.EmailServiceResponse) extends SagaCommand
  private final case class WrappedInventoryResponse(response: InventoryActor.InventoryResponse)   extends SagaCommand
  private final case class WrappedPaymentResponse(response: PaymentActor.PaymentResponse)         extends SagaCommand

  def apply(
             orderId: String,
             emailService: ActorRef[EmailServiceActor.EmailCommand],
             inventory: ActorRef[InventoryActor.InventoryCommand],
             payment: ActorRef[PaymentActor.PaymentCommand]
           ): Behavior[SagaCommand] =
    Behaviors.setup { context =>
      ready(
        TypeState(
          orderId,
          None,
          emailService,
          inventory,
          payment,
          context.messageAdapter(rsp => WrappedInventoryResponse(rsp)),
          context.messageAdapter(rsp => WrappedEmailResponse(rsp)),
          context.messageAdapter(rsp => WrappedPaymentResponse(rsp))
        ),
        context
      )
    }

  def ready(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] = Behaviors.receiveMessage {
    case ProcessTransaction(order) =>
      context.log.info(s"Processing order ${order.orderId}")
      state.inventory ! InventoryActor.ReserveItem(order, state.inventoryResponseMapper)
      waitingOnInventoryResponse(state.copy(order = Some(order)), context)
    case msg =>
      context.log.info(s"Unexpected message $msg in ready state")
      Behaviors.same
  }

  def waitingOnInventoryResponse(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] =
    Behaviors.receiveMessage {
      case WrappedInventoryResponse(msg) =>
        msg match {
          case InventoryActor.ReservationMade(order) =>
            context.log.info(s"Received item reservation for ${order.item._2} ${order.item._1}")
            state.payment ! PaymentActor.ProcessPayment(
              PaymentActor.Amount(1, 99),
              order.orderId,
              state.paymentResponseWrapper
            )
            waitingOnPaymentResponse(state, context)
          case InventoryActor.ReservationFailed(order, cause) =>
            context.log.error(s"Transaction ${order.orderId} failed, rolling back transaction.  Cause: $cause")
            // take compensating action, and un-reserve item
            state.inventory ! InventoryActor.UnreserveItem(order, state.inventoryResponseMapper)
            waitingOnInventoryCompensatingAction(state, context)
        }
      case msg =>
        context.log.warn(s"State: WaitingOnInventoryResponse.  Unexpected message: $msg")
        Behaviors.same
    }

  def waitingOnPaymentResponse(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] =
    Behaviors.receiveMessage {
      case WrappedPaymentResponse(msg) =>
        msg match {
          case PaymentActor.PaymentSucceeded(amount, orderId) =>
            context.log.info(s"Payment for $orderId succeeded:  $amount")
            state.emailService ! EmailServiceActor.SendEmail("example@gmail.com", orderId, state.emailResponseMapper)
            ready(state, context)
          case PaymentActor.PaymentFailed(orderId, amount, reason) =>
            context.log.error(s"Payment for $orderId failed:  $reason")
            //compensating action
            state.payment ! PaymentActor.RefundPayment(amount, orderId, state.paymentResponseWrapper)
            waitingOnPaymentCompensatingAction(state, context)
        }
      case msg =>
        context.log.warn(s"State: WaitingOnPaymentResponse.  Unexpected message:  $msg")
        Behaviors.same
    }

  def done(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] = Behaviors.receiveMessage { msg =>
    context.log.info(s"Received message $msg, but we don't care.  We're done.")
    Behaviors.same
  }

  //compensating action behaviors
  def waitingOnInventoryCompensatingAction(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] =
    Behaviors.receiveMessage {
      case WrappedInventoryResponse(response) =>
        response match {
          case InventoryActor.UnreserveFailed(order, reason) =>
            context.log.error(s"Failure un-reserving ${order.orderId}, retrying.  Cause: $reason")
            state.inventory ! InventoryActor.UnreserveItem(order, state.inventoryResponseMapper)
            Behaviors.same
          case InventoryActor.UnreserveSucceeded(orderId) =>
            context.log.info("Rolled back inventory reservation, going to ready state")
            ready(state, context)
          case msg =>
            context.log.warn(s"State: WaitingOnInventoryCompensatingAction.  Unexpected message: $msg")
            Behaviors.same
        }
      case msg =>
        context.log.warn(s"State: WaitingOnInventoryCompensatingAction.  Unexpected message: $msg")
        Behaviors.same
    }

  def waitingOnPaymentCompensatingAction(state: TypeState, context: ActorContext[_]): Behavior[SagaCommand] =
    Behaviors.receiveMessage {
      case WrappedPaymentResponse(response) =>
        response match {
          case PaymentActor.RefundFailed(amount, orderId, cause) =>
            Behaviors.same
          case PaymentActor.RefundSucceeded(amount, orderId) =>
            state.inventory ! InventoryActor.UnreserveItem(
              state.order.getOrElse(Order.empty.copy(orderId = orderId)),
              state.inventoryResponseMapper
            )
            waitingOnInventoryCompensatingAction(state, context)
          case paymentMsg =>
            context.log.warn(s"State: WaitingOnPaymentCompensatingAction.  Unexpected message: $paymentMsg")
            Behaviors.same
        }
      case msg =>
        context.log.warn(s"State: WaitingOnPaymentCompensatingAction.  Unexpected message: $msg")
        Behaviors.same
    }
}