package com.dero.home.events

import com.dero.home.items.Item

case class ReceivedUpdateEvent[S](item: Item, fromState : S, toState: S) extends Event {
    def from(state: S) = new ReceivedUpdateEvent[S](item, state, toState)

    def to(state: S) = new ReceivedUpdateEvent[S](item, fromState, state)
}
