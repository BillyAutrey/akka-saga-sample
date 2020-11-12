package com.example.services

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.example.Order
import org.scalatest.wordspec.AnyWordSpecLike

class InventoryActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "InventoryActor" should {
    "respond ReservationMade for a valid item and quantity" in {
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(Order("123", ("socks", 1)), probe.ref)
      probe.expectMessage(InventoryActor.ReservationMade("123","socks", 1))
    }

    "respond ReservationFailed for an invalid item" in {
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(Order("123", ("bacon", 1)), probe.ref)
      probe.expectMessage(InventoryActor.ReservationFailed("123","bacon", 1, "ItemId 'bacon' was not found in inventory"))
    }

    "respond ReservationFailed for an invalid quantity" in {
      val inventoryActor = testKit.spawn(InventoryActor())
      val probe = testKit.createTestProbe[InventoryActor.ReservationResponse]()
      inventoryActor ! InventoryActor.ReserveItem(Order("123", ("socks", 100)), probe.ref)
      probe.expectMessage(InventoryActor.ReservationFailed("123","socks", 100, "Not enough inventory"))
    }
  }

}
