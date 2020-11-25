package com.example.services

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.Order
import org.scalatest.wordspec.AnyWordSpecLike

class InventoryActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "InventoryActor" should {
    "respond ReservationMade for a valid item and quantity" in {
      val order = Order("123", ("socks", 1))
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(order, probe.ref)
      probe.expectMessage(InventoryActor.ReservationMade(order))
    }

    "respond ReservationFailed for an invalid item" in {
      val order = Order("123", ("bacon", 1))
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(order, probe.ref)
      probe.expectMessage(InventoryActor.ReservationFailed(order, "ItemId 'bacon' was not found in inventory"))
    }

    "respond ReservationFailed for an invalid quantity" in {
      val order = Order("123", ("socks", 100))
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(order, probe.ref)
      probe.expectMessage(InventoryActor.ReservationFailed(order, "Not enough inventory"))
    }
  }

}
