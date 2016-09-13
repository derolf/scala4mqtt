package com.dero.home.events

import com.dero.home.items.Item

case class ReceivedCommandEvent[S](item: Item, cmd: S) extends Event {
    def to(cmd: S) = new ReceivedCommandEvent[S](item, cmd)
}
