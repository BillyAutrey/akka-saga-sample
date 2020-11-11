package com.example.services

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class EmailServiceActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike{

  "EmailServiceActor" should {
    "respond with EmailSucceeded to a valid SendEmail message" in {
      val emailActor = testKit.spawn(EmailServiceActor(), "emailActor")
      val probe = testKit.createTestProbe[EmailServiceActor.EmailServiceResponse]()
      emailActor ! EmailServiceActor.SendEmail("someaddress@gmail.com", "123", probe.ref)
      probe.expectMessage(EmailServiceActor.EmailSucceeded("123"))
    }
  }

}
