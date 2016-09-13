package com.dero.home.items

import com.dero.home.AbstractModule

import scala.reflect.runtime.universe._

class Group[+T <: Item : TypeTag](module: AbstractModule) extends Item(module) {
    override def This = typeOf[Group[T]]
    type State = Any
    type Command = Any

    def validItemType[SubType: TypeTag] = typeOf[SubType] <:< typeOf[T]

    var _items : Items[Item] = module.itemsOfType[T]

    def items(items: Items[Item]) : this.type = {
        items.asInstanceOf[Items[T]] // check type
        _items = items
        this
    }

    def items = _items.asInstanceOf[Items[T]].items
}
