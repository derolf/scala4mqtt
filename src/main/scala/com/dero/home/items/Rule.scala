package com.dero.home.items

import com.dero.home.AbstractModule
import com.dero.home.events.Event
import com.dero.home.states.OnOff

import scala.reflect.runtime.universe._

class Rule(module: AbstractModule) extends Item(module) {
    override def This = typeOf[Rule]
    type State = OnOff.Type

    type Then = PartialFunction[Event, Unit]

    var _lastTime : Long = 0
    var _then : Then = null

    def run(func: Then) : this.type = {
        this._then = func
        this
    }

    def lastTime = _lastTime
}

