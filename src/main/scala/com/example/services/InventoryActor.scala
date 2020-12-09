package com.example.services

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.example.{CborSerializable, Order}

import scala.util.Try

object InventoryActor {

  case class State(items: Map[String, Int]) extends CborSerializable
  sealed trait InventoryCommand extends CborSerializable {
    def order: Order
  }
  sealed trait InventoryResponse extends CborSerializable

  case class ReserveItem(order: Order, ref: ActorRef[ReservationResponse]) extends InventoryCommand
  sealed trait ReservationResponse                                         extends InventoryResponse
  case class ReservationMade(order: Order)                                 extends ReservationResponse
  case class ReservationFailed(order: Order, cause: String)                extends ReservationResponse

  case class UnreserveItem(order: Order, ref: ActorRef[UnreserveResponse]) extends InventoryCommand

  sealed trait UnreserveResponse                           extends InventoryResponse
  case class UnreserveSucceeded(orderId: String)           extends UnreserveResponse
  case class UnreserveFailed(order: Order, reason: String) extends UnreserveResponse

  //This is not a good pattern.  For demo purposes, however, we will just create a hard-coded inventory
  val inventory = Map(
    "socks"    -> 10,
    "shoes"    -> 15,
    "pants"    -> 3,
    "shirt"    -> 7,
    "backpack" -> 2
  )

  def apply(): Behavior[InventoryCommand] = Behaviors.setup { context =>
    receive(context, State(inventory))
  }

  def receive(context: ActorContext[InventoryCommand], state: State): Behavior[InventoryCommand] =
    Behaviors.receiveMessage[InventoryCommand] { msg: InventoryCommand =>
      val orderId        = msg.order.orderId
      val itemId         = msg.order.item._1
      val quantity       = msg.order.item._2
      val maybeInventory = Try(state.items(itemId))

      msg match {
        case ReserveItem(order, ref) =>
          if (maybeInventory.isFailure) {
            ref ! ReservationFailed(order, s"ItemId '$itemId' was not found in inventory")
            Behaviors.same
          } else if (maybeInventory.getOrElse(0) >= quantity) {
            ref ! ReservationMade(order)
            receive(context, State(state.items + (itemId -> (maybeInventory.get - quantity))))
          } else {
            ref ! ReservationFailed(order, "Not enough inventory")
            Behaviors.same
          }
        case UnreserveItem(order, ref) =>
          if (maybeInventory.isFailure) {
            ref ! UnreserveFailed(order, s"Cannot restock due to missing item:  $itemId")
            Behaviors.same
          } else {
            ref ! UnreserveSucceeded(orderId)
            receive(context, State(state.items + (itemId -> (maybeInventory.get + quantity))))
          }
      }
    }

}
