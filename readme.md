# Akka Saga Sample
This code repository is meant to illustrate a basic saga pattern, using the pure Akka Typed API with behaviors.  There is also an example that uses Akka Event Sourced entities, which mutate behavior in a slightly different way.

## Example Domain
In this example, we want to model a transaction that requires action to take place on two services.  We will emulate the second service as an actor, and have our saga handlers in two classes - `EventSourcedSagaActor` and `TypedSagaActor`.

The activity to model is an online order.  We want to take five actions in order to process our order:

1. Reserve inventory for the ordered items
1. Charge the customer
1. Send order details to the shipping team, to package the order
1. Report to the customer that the transaction was successful
1. Email the customer a receipt

These steps should happen in this order.  We don't want to continue with the transaction if we are unable to reserve inventory.  We don't want to prepare any shipment if we can't charge the customer successfully.  And we don't want to report that the transaction was successful (or email a receipt) if the transaction failed during some step along the way.

For the sake of simplicity, we will emulate the activities here as if they were implemented by other services.  We will use actors to represent these services, but they could be anything.  These messages could be sent to Kafka, or could call gRPC or REST endpoints directly.  The method is not important, but the presence of an external activity (and response) is. 

Services for these activities:
1. `InventoryActor` handles inventory reservation.
1. `PaymentActor` handles payments.
1. `ShippingActor` handles shipment details.
1. `EmailServiceActor` handles emails.