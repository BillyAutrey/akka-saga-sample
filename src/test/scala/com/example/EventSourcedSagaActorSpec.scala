package com.example

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit

class EventSourcedSagaActorSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config) {

}
