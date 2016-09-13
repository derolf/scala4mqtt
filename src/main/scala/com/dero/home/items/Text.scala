package com.dero.home.items

import com.dero.home.AbstractModule

import scala.languageFeature.implicitConversions

import scala.reflect.runtime.universe._

class Text(module: AbstractModule) extends Item(module) {
    override def This = typeOf[Text]
    type State = String
    type Command = State
}

