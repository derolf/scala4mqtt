package com.dero.home.items

import com.dero.home.AbstractModule

import scala.reflect.runtime.universe._

class Float(module: AbstractModule) extends Item(module) {
    override def This = typeOf[Float]
    type State = Double
    type Command = State
}
