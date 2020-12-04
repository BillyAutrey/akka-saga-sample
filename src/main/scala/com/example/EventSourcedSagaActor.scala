package com.example

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object EventSourcedSagaActor {

  sealed trait State extends CborSerializable
  object State{
    val empty: State = ReadyState("")
  }

  case class ReadyState(id: String) extends State
  case class WaitingOnInventoryState(id: String) extends State
  case class WaitingOnPaymentState(id: String) extends State

  sealed trait Command extends CborSerializable

  sealed trait Event extends CborSerializable

  def apply(entityId: String): Behavior[Command] = Behaviors.setup {
    context =>
      EventSourcedBehavior[Command,Event, State](
        persistenceId = PersistenceId("EventSourcedSaga",entityId),
        emptyState = State.empty,
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context)
      )
  }

  def commandHandler(context: ActorContext[_])(state: State, command: Command): Effect[Event, State] = {
    state match {
      case WaitingOnInventoryState(id) =>
        waitingOnInventoryCommandHandler(state, command)
      case WaitingOnPaymentState(id) =>
        waitingOnPaymentCommandHandler(state, command)
    }
  }

  def waitingOnInventoryCommandHandler(state: State, command: Command): Effect[Event, State] = Effect.noReply
  def waitingOnPaymentCommandHandler(state: State, command: Command): Effect[Event, State] = Effect.noReply

  def eventHandler(context: ActorContext[_])(state: State, event: Event): State = State.empty

}
