package com.example

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.example.services.{EmailServiceActor, InventoryActor, PaymentActor}

object EventSourcedSagaActor {

  sealed trait State extends CborSerializable {
    def id: String
    def refs: Refs
  }

  sealed trait Command extends CborSerializable
  sealed trait Event   extends CborSerializable

  // Case class to contain injected ActorRefs, to make it easier to pass this state around
  case class Refs(
                   inventoryActor: ActorRef[InventoryActor.InventoryCommand],
                   emailServiceActor: ActorRef[EmailServiceActor.EmailCommand],
                   paymentActor: ActorRef[PaymentActor.PaymentCommand],
                   inventoryResponseMapper: Option[ActorRef[InventoryActor.InventoryResponse]],
                   emailResponseMapper: Option[ActorRef[EmailServiceActor.EmailServiceResponse]],
                   paymentResponseWrapper: Option[ActorRef[PaymentActor.PaymentResponse]]
                 ) extends CborSerializable

  // Multiple state classes, as described in "Akka EventSourced behaviors as finite state machines".
  // See https://doc.akka.io/docs/akka/current/typed/persistence-fsm.html for more details.
  final case class ReadyState(id: String, refs: Refs)              extends State
  final case class WaitingOnInventoryState(id: String, refs: Refs) extends State
  final case class WaitingOnPaymentState(id: String, refs: Refs)   extends State

  // Compensation state
  final case class WaitingOnInventoryCompensation(id: String, refs: Refs) extends State
  final case class WaitingOnPaymentCompensation(id: String, refs: Refs)   extends State

  final case class ProcessTransaction(order: Order) extends Command
  final case object SpanishInquisition              extends Command //Nobody expects the Spanish Inquisition!

  final case class ProcessTransactionReceived(order: Order) extends Event
  final case class ReservationMadeReceived(order: Order)    extends Event

  // Failure receipt events
  final case class TransactionFailureReceived(order: Order) extends Event
  final case class ReservationFailureReceived(order: Order) extends Event

  // Compensating action events
  final case class InventoryCompensationReceived(orderId: String) extends Event
  final case class InventoryCompensationFailed(orderId: String)   extends Event

  // Adapted responses - https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#adapted-response
  private final case class WrappedEmailServiceResponse(response: EmailServiceActor.EmailServiceResponse) extends Command
  private final case class WrappedInventoryResponse(response: InventoryActor.InventoryResponse)          extends Command
  private final case class WrappedPaymentResponse(response: PaymentActor.PaymentResponse)                extends Command

  def apply(
      entityId: String,
      inventoryActor: ActorRef[InventoryActor.InventoryCommand],
      emailServiceActor: ActorRef[EmailServiceActor.EmailCommand],
      paymentActor: ActorRef[PaymentActor.PaymentCommand]
  ): Behavior[Command] = Behaviors.setup { context =>
    val refs = refFactory(inventoryActor, emailServiceActor, paymentActor, context)

    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId("EventSourcedSaga", entityId),
      emptyState = ReadyState(entityId, refs),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }

  // For testing
  def apply(partialState: State): Behavior[Command] = Behaviors.setup { context =>
    val refs = partialState.refs match {
      case Refs(i, e, p, None, None, None) =>
        refFactory(i, e, p, context)
      case valid =>
        valid
    }

    val state = partialState match {
      case r: ReadyState              => r.copy(refs = refs)
      case i: WaitingOnInventoryState => i.copy(refs = refs)
      case p: WaitingOnPaymentState   => p.copy(refs = refs)
    }

    context.log.info(s"Starting with ${state.getClass}")

    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId("EventSourcedSaga", state.id),
      emptyState = state,
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }

  private def refFactory(
      inventoryActor: ActorRef[InventoryActor.InventoryCommand],
      emailServiceActor: ActorRef[EmailServiceActor.EmailCommand],
      paymentActor: ActorRef[PaymentActor.PaymentCommand],
      context: ActorContext[Command]
  ): Refs =
    Refs(
      inventoryActor,
      emailServiceActor,
      paymentActor,
      Some(context.messageAdapter(rsp => WrappedInventoryResponse(rsp))),
      Some(context.messageAdapter(rsp => WrappedEmailServiceResponse(rsp))),
      Some(context.messageAdapter(rsp => WrappedPaymentResponse(rsp)))
    )

  def commandHandler(context: ActorContext[Command])(state: State, command: Command): Effect[Event, State] = {
    state match {
      case WaitingOnInventoryState(_, _) =>
        waitingOnInventoryCommandHandler(context)(state, command)
      case WaitingOnPaymentState(_, _) =>
        waitingOnPaymentCommandHandler(state, command)
      case ready: ReadyState =>
        readyCommandHandler(context)(ready, command)
      case WaitingOnInventoryCompensation(_, _) =>
        waitingOnInventoryCompensatingAction(context)(state, command)
    }
  }

  def readyCommandHandler(context: ActorContext[Command])(state: ReadyState, command: Command): Effect[Event, State] = {
    command match {
      case ProcessTransaction(order) =>
        Effect
          .persist(ProcessTransactionReceived(order))
          .thenRun(_ =>
            state.refs.inventoryResponseMapper match {
              case Some(ref) =>
                state.refs.inventoryActor ! InventoryActor.ReserveItem (order,ref)
              case None =>
                context.log.error(s"No inventory mapper available for ReserveItem")
            }
          )
      case _ =>
        context.log.warn(s"Unexpected message in 'Ready' state:  $command")
        Effect.noReply
    }
  }

  def waitingOnInventoryCommandHandler(
      context: ActorContext[Command]
  )(state: State, command: Command): Effect[Event, State] = {
    command match {
      case WrappedInventoryResponse(response) =>
        response match {
          case InventoryActor.ReservationMade(order) =>
            Effect
              .persist(ReservationMadeReceived(order))
              .thenRun(_ =>
                state.refs.paymentResponseWrapper match {
                  case Some(ref) =>
                    state.refs.paymentActor ! PaymentActor
                      .ProcessPayment (PaymentActor.Amount (1, 99), order.orderId, ref)
                  case None =>
                    context.log.error(s"No payment mapper available for ProcessPayment")
                }
              )
          case InventoryActor.ReservationFailed(order, cause) =>
            context.log.error(s"Unable to process ${order.orderId}: $cause")
            Effect
              .persist(ReservationFailureReceived(order))
              .thenRun( _ =>
                state.refs.inventoryResponseMapper match {
                  case Some(ref) =>
                    state.refs.inventoryActor ! InventoryActor.UnreserveItem (order, ref)
                  case None =>
                    context.log.error("No inventory mapper available for UnreserveItem")
                }
              )
        }
      case _ =>
        context.log.warn(s"Unexpected message in 'WaitingOnInventory' state:  $command")
        Effect.noReply
    }
  }

  // TODO
  def waitingOnPaymentCommandHandler(state: State, command: Command): Effect[Event, State] = Effect.noReply

  // Compensating behaviors
  def waitingOnInventoryCompensatingAction(
      context: ActorContext[Command]
  )(state: State, command: Command): Effect[Event, State] = command match {
    case WrappedInventoryResponse(response) =>
      response match {
        case InventoryActor.UnreserveSucceeded(orderId) =>
          Effect
            .persist(InventoryCompensationReceived(orderId))
        case InventoryActor.UnreserveFailed(order, reason) =>
          context.log.error(s"Retrying failed compensating action for ${order.orderId}: $reason")
          Effect
            .none
            .thenRun( _ =>
              state.refs.inventoryResponseMapper match {
                case Some(ref) =>
                  state.refs.inventoryActor ! InventoryActor.UnreserveItem(order, ref)
                case None =>
                  context.log.error("No inventory mapper available for UnreserveItem")
              }
            )
      }
  }

  def waitingOnPaymentCompensatingAction(
      context: ActorContext[Command]
  )(state: State, command: Command): Effect[Event, State] = Effect.noReply

  // Event Handlers
  def eventHandler(context: ActorContext[Command])(state: State, event: Event): State = {
    state match {
      case WaitingOnInventoryState(id, refs) =>
        waitingOnInventoryEventHandler(state, event)
      case WaitingOnPaymentState(_, _) =>
        waitingOnPaymentEventHandler(state, event)
      case WaitingOnInventoryCompensation(_, _) =>
        waitingOnInventoryCompensationEventHandler(state, event)
      case ReadyState(id, refs) =>
        readyEventHandler(state, event)
    }
  }

  def readyEventHandler(state: State, event: Event): State = event match {
    case ProcessTransactionReceived(order) =>
      WaitingOnInventoryState(state.id, state.refs)
    case _ =>
      state
  }

  def waitingOnInventoryEventHandler(state: State, event: Event): State = event match {
    case ReservationMadeReceived(order) =>
      WaitingOnPaymentState(state.id, state.refs)
    case ReservationFailureReceived(order) =>
      WaitingOnInventoryCompensation(state.id, state.refs)
    case _ =>
      state
  }

  def waitingOnInventoryCompensationEventHandler(state: State, event: Event): State = event match {
    case InventoryCompensationReceived(orderId) =>
      ReadyState(orderId, state.refs)
  }

  // TODO
  def waitingOnPaymentEventHandler(state: State, event: Event): State = state

}
