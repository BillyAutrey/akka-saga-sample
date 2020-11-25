package com.example

case class Order(orderId: String, item: (String, Int))
object Order{
  val empty: Order = Order("", ("",0))
}

