//#full-example
package com.example

import akka.actor.typed.ActorSystem
import com.example.SagaExampleGuardian.ProcessOrder

/**
 * Main entry point for the application.
 */
object AkkaSagaSample extends App {
  val system = ActorSystem(SagaExampleGuardian(), "SagaExampleGuardian")

  system ! ProcessOrder(Order("123", ("socks",1)))
}

