package com.dero.home.items

import com.dero.home.AbstractModule
import com.dero.home.states.OnOff

import scala.languageFeature.implicitConversions
import scala.reflect.runtime.universe._

class Switch(module: AbstractModule) extends Item(module) {
    override def This = typeOf[Switch]
    type State = OnOff.Type
    type Command = State
}
