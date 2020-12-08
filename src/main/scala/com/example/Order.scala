package com.example

case class Order(orderId: String, item: (String, Int)) extends CborSerializable
object Order{
  val empty: Order = Order("", ("",0))
}

