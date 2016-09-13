package com.dero.home

import java.util.concurrent.atomic.AtomicLong

import com.dero.home.items.{Item, Items}

import scala.languageFeature.implicitConversions
import scala.reflect.runtime.universe._


/**
 * Created by daniel on 8/30/15.
 */
class AbstractModule(val bus: EventBus) {
    module =>

    val updateLock = new Object

    private val _items = scala.collection.concurrent.TrieMap.empty[Item, Unit]

    def items = _items.keys

    private val _itemsChanged = new AtomicLong(0)

    def itemsChanged = _itemsChanged.get()

    def notifyItemsChanged(): Unit = _itemsChanged.incrementAndGet()

    def addItem(item: Item): Unit = _items.put(item, Unit);
    notifyItemsChanged()

    def removeItem(item: Item): Unit = _items.remove(item);
    notifyItemsChanged()

    def itemsOfType[T <: Item : TypeTag]: Items[T] = new Items[T](items.view.flatMap(item => item match {
            case item if item.This <:< typeOf[T] => Some(item.asInstanceOf[T])
            case _ => None
        }), itemsChanged)

    bus._modules.put(this, this)

    def findItemFieldName(item: Item): (String, Option[String]) = {
        val im = scala.reflect.runtime.universe.runtimeMirror(this.getClass.getClassLoader).reflect(this)
        (im.symbol.name.toString, im.symbol.typeSignature.members.filter(_.typeSignature <:< typeOf[Item]).find(f => im.reflectField(f.asInstanceOf[TermSymbol]).get == item).map(_.name.toString.trim))
        /*this.getClass.getDeclaredFields.find(f =>
            try {
                f.setAccessible(true)
                f.get(this) == item
            } catch {
                case _ => false
            }
        ).map(_.getName)*/
    }
}


object AbstractModule {

    /*object undefined {}

    object any {
        override def equals(other: Any) = true
    }

    /** For chaining events */
    object && {
        def unapply(a: Event) = Some((a, a))
    }*/

}
