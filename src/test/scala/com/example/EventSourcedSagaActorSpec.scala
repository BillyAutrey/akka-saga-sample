package com.example

import akka.actor.testkit.typed.scaladsl.{LogCapturing, LoggingTestKit, ScalaTestWithActorTestKit, TestProbe}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.example.services.PaymentActor.Amount
import com.example.services.{EmailServiceActor, InventoryActor, PaymentActor}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

class EventSourcedSagaActorSpec
    extends ScalaTestWithActorTestKit(
      ConfigFactory
        .parseString("""
        |akka.actor.serialization-bindings {
        |  "com.example.CborSerializable" = jackson-cbor
        |}
        |""".stripMargin)
        .withFallback(EventSourcedBehaviorTestKit.config)
    )
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with LogCapturing {
  import EventSourcedSagaActor._

  val orderId = "123"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
  }

  private def eventSourcedTestKit(state: State) = EventSourcedBehaviorTestKit[Command, Event, State](
    system,
    EventSourcedSagaActor(state)
  )

  private def testRefsFactory(
      emailServiceActor: TestProbe[EmailServiceActor.EmailCommand],
      inventoryActor: TestProbe[InventoryActor.InventoryCommand],
      paymentActor: TestProbe[PaymentActor.PaymentCommand]
  ): Refs = {
    Refs(inventoryActor.ref, emailServiceActor.ref, paymentActor.ref, None, None, None)
  }

  class EventSourcedSagaActor(id: String)

  "EventSourcedSagaActor" should {
    "given ready state, when receiving a ProcessTransaction, send ReserveItem to InventoryActor" in {
      val itemId            = "socks"
      val quantity          = 1
      val order             = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.EmailCommand]("emailServiceActor")
      val inventoryActor    = testKit.createTestProbe[InventoryActor.InventoryCommand]("inventoryActor")
      val paymentActor      = testKit.createTestProbe[PaymentActor.PaymentCommand]("paymentActor")
      val refs              = testRefsFactory(emailServiceActor, inventoryActor, paymentActor)

      // Create the actor, and process a transaction
      val eventSourcedSagaActor = eventSourcedTestKit(ReadyState(itemId, refs))
      val result                = eventSourcedSagaActor.runCommand(ProcessTransaction(order))

      // Inventory actor should have received a reservation
      val msg = inventoryActor.expectMessageType[InventoryActor.ReserveItem]
      msg.order.orderId shouldBe "123"
      msg.order.item._2 shouldBe 1

      // EventSourcedSagaActor should have transitioned state, and triggered the proper event
      eventSourcedSagaActor.getState() shouldBe a[WaitingOnInventoryState]
      result.event === ProcessTransaction(order)

      //cleanup
      eventSourcedSagaActor.clear()
    }

    "given ready state, when receiving an unexpected message, log an unexpected message" in {
      val itemId            = "socks"
      val quantity          = 1
      val order             = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.EmailCommand]("emailServiceActor")
      val inventoryActor    = testKit.createTestProbe[InventoryActor.InventoryCommand]("inventoryActor")
      val paymentActor      = testKit.createTestProbe[PaymentActor.PaymentCommand]("paymentActor")
      val refs              = testRefsFactory(emailServiceActor, inventoryActor, paymentActor)

      // Create the actor, and process a transaction
      val eventSourcedSagaActor = eventSourcedTestKit(ReadyState(itemId, refs))

      //expect the unexpected
      LoggingTestKit.warn("SpanishInquisition").expect {
        val result = eventSourcedSagaActor.runCommand(SpanishInquisition)

        result.hasNoEvents shouldBe true
      }

      // Inventory actor should NOT have received a reservation
      inventoryActor.expectNoMessage()

      // EventSourcedSagaActor should NOT have transitioned state
      eventSourcedSagaActor.getState() shouldBe a[ReadyState]

      //cleanup
      eventSourcedSagaActor.clear()
    }

    "given WaitingOnInventory state, when receiving an inventory response, send a message to process payment" in {
      val itemId            = "socks"
      val quantity          = 1
      val order             = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.EmailCommand]("emailServiceActor")
      val inventoryActor    = testKit.createTestProbe[InventoryActor.InventoryCommand]("inventoryActor")
      val paymentActor      = testKit.createTestProbe[PaymentActor.PaymentCommand]("paymentActor")
      val refs              = testRefsFactory(emailServiceActor, inventoryActor, paymentActor)

      // Create the actor, and process a transaction
      val eventSourcedSagaActor   = eventSourcedTestKit(WaitingOnInventoryState(itemId, refs))
      val inventoryResponseMapper = eventSourcedSagaActor.getState().refs.inventoryResponseMapper.get //gross
      inventoryResponseMapper ! InventoryActor.ReservationMade(order) // This acts like a mapper for the behavior I care about
      //val result = eventSourcedSagaActor.runCommand(WrappedInventoryResponse(InventoryActor.ReservationMade(order))) //wish I could do this, to get events

      // Inventory actor should have received a reservation
      val msg = paymentActor.expectMessageType[PaymentActor.ProcessPayment]
      msg.amount shouldBe Amount(1, 99)

      // EventSourcedSagaActor should have transitioned state, and triggered the proper event
      eventSourcedSagaActor.getState() shouldBe a[WaitingOnPaymentState]
      //result.event === ProcessTransaction(order) //I can't get at the events now, to assert on them

      //cleanup
      eventSourcedSagaActor.clear()
    }

    "given awaitingInventoryResponse, when receiving an unexpected command, log an unexpected message warning" in {
      val itemId            = "socks"
      val quantity          = 1
      val order             = Order(orderId, (itemId, quantity))
      val emailServiceActor = testKit.createTestProbe[EmailServiceActor.EmailCommand]("emailServiceActor")
      val inventoryActor    = testKit.createTestProbe[InventoryActor.InventoryCommand]("inventoryActor")
      val paymentActor      = testKit.createTestProbe[PaymentActor.PaymentCommand]("paymentActor")
      val refs              = testRefsFactory(emailServiceActor, inventoryActor, paymentActor)

      // Create the actor, and process a transaction
      val eventSourcedSagaActor = eventSourcedTestKit(WaitingOnInventoryState(itemId, refs))

      //expect the unexpected
      LoggingTestKit.warn("SpanishInquisition").expect {
        val result = eventSourcedSagaActor.runCommand(SpanishInquisition)

        result.hasNoEvents shouldBe true
      }

      // Inventory actor should NOT have received a reservation
      paymentActor.expectNoMessage()

      // EventSourcedSagaActor should NOT have transitioned state
      eventSourcedSagaActor.getState() shouldBe a[WaitingOnInventoryState]

      //cleanup
      eventSourcedSagaActor.clear()
    }

  }
}
