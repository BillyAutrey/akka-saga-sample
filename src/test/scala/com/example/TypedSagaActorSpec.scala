package com.example

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.TypedSagaActor.{WrappedEmailServiceResponse, WrappedInventoryResponse, WrappedPaymentResponse}
import com.example.services.{EmailServiceActor, InventoryActor, PaymentActor}
import org.scalatest.wordspec.AnyWordSpecLike


class TypedSagaActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike{

  "TypedSagaActor" should {

    "given ready state, when receiving a ProcessTransaction, send ReserveItem to InventoryActor" in {
      val orderId = "123"
      val itemId = "socks"
      val quantity = 1
      val order = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.SendEmail]("emailServiceActor")
      val inventoryActor = testKit.createTestProbe[InventoryActor.ReserveItem]("inventoryActor")
      val paymentActor = testKit.createTestProbe[PaymentActor.ProcessPayment]("paymentActor")

      val typedSagaActor = testKit.spawn(TypedSagaActor(orderId,emailServiceActor.ref,inventoryActor.ref, paymentActor.ref))
      typedSagaActor ! TypedSagaActor.ProcessTransaction(order)

      val msg = inventoryActor.expectMessageType[InventoryActor.ReserveItem]
      msg.order.orderId shouldBe "123"
      msg.order.item._2 shouldBe 1
    }

    "given ready state, when receiving an unexpected message, log an unexpected message" in {
      val orderId = "123"
      val itemId = "socks"
      val quantity = 1
      val order = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.SendEmail]("emailServiceActor")
      val inventoryActor = testKit.createTestProbe[InventoryActor.ReserveItem]("inventoryActor")
      val paymentActor = testKit.createTestProbe[PaymentActor.ProcessPayment]("paymentActor")

      val typedSagaActor = testKit.spawn(TypedSagaActor(orderId, emailServiceActor.ref, inventoryActor.ref, paymentActor.ref))
      typedSagaActor ! TypedSagaActor.SpanishInquisition

      //expect the unexpected
      LoggingTestKit.warn("unexpected")
    }

    "given awaitingInventoryResponse, when receiving an inventory response, send a message to process payment" in {
      val orderId = "123"
      val itemId = "socks"
      val quantity = 1
      val order = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.SendEmail]("emailServiceActor")
      val inventoryActor = testKit.createTestProbe[InventoryActor.ReserveItem]("inventoryActor")
      val paymentActor = testKit.createTestProbe[PaymentActor.ProcessPayment]("paymentActor")

      //create actor, and send message to get it into the right state
      val typedSagaActor = testKit.spawn(TypedSagaActor(orderId,emailServiceActor.ref,inventoryActor.ref, paymentActor.ref))
      typedSagaActor ! TypedSagaActor.ProcessTransaction(order)

      //typedSagaActor ! InventoryActor.ReservationMade(orderId, itemId, quantity)
    }

    "given awaitingInventoryResponse, when receiving an unexpected command, log an unexpected message warning" in {
      val orderId = "123"
      val itemId = "socks"
      val quantity = 1
      val order = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.SendEmail]("emailServiceActor")
      val inventoryActor = testKit.createTestProbe[InventoryActor.ReserveItem]("inventoryActor")
      val paymentActor = testKit.createTestProbe[PaymentActor.ProcessPayment]("paymentActor")

      val typedSagaActor = testKit.spawn(TypedSagaActor(orderId,emailServiceActor.ref,inventoryActor.ref, paymentActor.ref))
      typedSagaActor ! TypedSagaActor.ProcessTransaction(order)

      // Just tracking that we should have switched states
      val msg = inventoryActor.expectMessageType[InventoryActor.ReserveItem]

      //send the message
      typedSagaActor ! TypedSagaActor.SpanishInquisition

      //expect the unexpected
      LoggingTestKit.warn("unexpected")
    }

  }

}
