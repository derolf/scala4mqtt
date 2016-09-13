package com.dero.home.items

import com.dero.home.AbstractModule

import scala.reflect.runtime.universe._

class Custom[StateType: TypeTag, CommandType : TypeTag](module: AbstractModule)(implicit tt : TypeTag[Custom[StateType, CommandType]]) extends Item(module) {
    type State = StateType
    type Command = CommandType
    override def This = tt.tpe // typeOf[Custom[T]] <- didn't work when T comes from a different bundle
}
