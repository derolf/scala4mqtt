package com.dero.home

import com.dero.home.events.{Event, ReceivedCommandEvent, ReceivedUpdateEvent}
import com.dero.home.items.{Item, Items}
import com.dero.home.{items => $items}

import scala.reflect.runtime.universe._

/**
 * Created by daniel on 8/30/15.
 */
class Module(bus: EventBus) extends AbstractModule(bus) {
    module =>

    class ItemAccessor[S <: $items.Item : TypeTag](_create: => S) {
        /**
         * Get item by name and create if not existing.
         *
         * @param name
         * @tparam T
         * @return
         */
        def apply() : S = cast(create())

        /**
         * Check whether item is of right type.
         *
         * @param item
         * @tparam T
         * @return
         */
        def check(item: $items.Item) : Boolean = item.This <:< typeOf[S]

        /**
         * Create new item. Replaces any existing item with same name of any type.
         *
         * @param name
         * @tparam T
         * @return
         */
        def create() : S = {
            val o = _create
            val b = typeOf[S]
            assert (o.This =:= typeOf[S])
            o
        }

        /**
         * Cast item to right type, MatchError if not compatible.
         *
         * @param item
         * @tparam T
         * @return
         */
        def cast(item: $items.Item) : S = item match { case s if item.This <:< typeOf[S] => s.asInstanceOf[S] }

        /**
         * Group by all items of right type and having at least one of the given tags
         *
         * @param tags
         * @tparam T
         * @return
         */
        def tagged(tags: Items.Filter*) = module.itemsOfType[S].tagged(tags)

        /**
         * Group by all items of right type.
         *
         * @return
         */
        def all() = module.itemsOfType[S]

        /**
         * Case matching extract for Event to catch a Command.
         * Usage: case module.Switch.Command(item, cmd) => matches if <item> in <module> received command <cmd>
         * Note: better use item.Command(cmd) to match a certain item
         */
        object Command {
            def unapply(ev: Event) : Option[(S, S#Command)] = ev match {
                case ReceivedCommandEvent(item, cmd) if (module == item.module && item.This <:< typeOf[S]) => Some((item.asInstanceOf[S], cmd.asInstanceOf[S#Command]))
                case _ => None
            }
        }

        /**
         * Case matching extract for Event to catch an Update.
         * Usage: case module.Switch.Update(item, from, to) => matches if <item> in <module> received update from <from> to <to>
         * Note: better use item.Update(from, to) to match a certain item
         */
        object Update {
            def unapply(ev: Event) : Option[(S, S#State, S#State)] = ev match {
                case ReceivedUpdateEvent(item, from, to) if (module == item.module && item.This <:< typeOf[S])=> Some((item.asInstanceOf[S], from.asInstanceOf[S#State], to.asInstanceOf[S#State]))
                case _ => None
            }
        }
    }

    def defineItemType[Item <: $items.Item : TypeTag](create : => Item) = new ItemAccessor[Item](create)

    /**
     * Accessor for Item.
     */
    val Item = defineItemType[$items.Item](throw new IllegalAccessError(s"Raw Item cannot be created"))

    /**
     * Accessor for Switches.
     */
    val Switch = defineItemType(new $items.Switch(module))

    /**
     * Accessor for Contacts.
     */
    val Contact = defineItemType(new $items.Contact(module))


    /**
     * Accessor for Switches.
     */
    val Text = defineItemType(new $items.Text(module))

    /**
     * Accessor for Numbers.
     */
    val Number = defineItemType(new $items.Float(module))

    /**
     * Accessor for Rules.
     */
    val Rule = defineItemType(new $items.Rule(module))

    /**
     * Accessor for Timers.
     */
    val Timer = defineItemType(new $items.Timer(module))

    /**
     * Accessor for Groups.
     */
    val Group = {
        object Group
        {
            val Item = defineItemType(new $items.Group[$items.Item](module))
            val Switch = defineItemType(new $items.Group[$items.Switch](module))
            val Contact = defineItemType(new $items.Group[$items.Contact](module))
            val Text = defineItemType(new $items.Group[$items.Text](module))
            val Number = defineItemType(new $items.Group[$items.Float](module))
            val Rule = defineItemType(new $items.Group[$items.Rule](module))
            val Timer = defineItemType(new $items.Group[$items.Timer](module))
            def apply[T <: Item : TypeTag](items: Items[T]) = (new $items.Group[T](module)).items(items)
        }
        Group
    }

}
object Module {

}
