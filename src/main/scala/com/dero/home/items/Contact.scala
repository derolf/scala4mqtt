package com.dero.home.items

import com.dero.home.AbstractModule
import com.dero.home.states.OpenClose

import scala.languageFeature.implicitConversions
import scala.reflect.runtime.universe._

class Contact(module: AbstractModule) extends Item(module) {
    override def This = typeOf[Contact]
    type State = OpenClose.Type
    type Command = State
}
