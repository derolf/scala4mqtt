package com.dero.home.states

object OnOff extends Enumeration {
    val OFF = Value(0, "OFF")
    val ON = Value(1, "ON")
    type Type = Value
}

object OpenClose extends Enumeration {
    val OPEN = Value(0, "OPEN")
    val CLOSE = Value(1, "CLOSE")
    type Type = Value
}
