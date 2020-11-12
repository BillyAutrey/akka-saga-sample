package com.example.services

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.{CborSerializable, Order}

import scala.util.Try

object InventoryActor {

  case class State(items: Map[String, Int]) extends CborSerializable
  case class ReserveItem(order: Order, ref: ActorRef[ReservationResponse]) extends CborSerializable
  sealed trait ReservationResponse extends CborSerializable
  case class ReservationMade(orderId: String, itemId: String, quantity: Int) extends ReservationResponse
  case class ReservationFailed(orderId: String, itemId: String, quantity: Int, cause: String) extends ReservationResponse

  //This is not a good pattern.  For demo purposes, however, we will just create a hard-coded inventory
  val inventory = Map(
    "socks" -> 10,
    "shoes" -> 15,
    "pants" -> 3,
    "shirt" -> 7,
    "backpack" -> 2
  )

  def apply(): Behavior[ReserveItem] = Behaviors.setup{
    context =>
    receive(context, State(inventory))
  }

  def receive(context: ActorContext[ReserveItem], state: State): Behavior[ReserveItem] = Behaviors.receiveMessage{
    msg =>
      val order = msg.order
      val orderId = order.orderId
      val itemId = order.item._1
      val quantity = order.item._2
      val maybeInventory = Try(state.items(itemId))

      if(maybeInventory.isFailure){
        msg.ref ! ReservationFailed(orderId, itemId, quantity, s"ItemId '$itemId' was not found in inventory")
        Behaviors.same
      }else if(maybeInventory.getOrElse(0) >= quantity){
        msg.ref ! ReservationMade(orderId, itemId, quantity)
        receive(context, State(state.items + (itemId -> (maybeInventory.get - quantity))))
      } else {
        msg.ref ! ReservationFailed(orderId, itemId, quantity, "Not enough inventory")
        Behaviors.same
      }
  }

}
