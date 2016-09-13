/**
 * Created by daniel on 9/3/15.
 */

package com.dero.home.items

import com.dero.home.events.{Event, ReceivedCommandEvent, ReceivedUpdateEvent}

import scala.reflect.runtime.universe._
import scala.languageFeature.implicitConversions
import scala.util.matching.Regex

trait ModificationTagAware {
    def tag : Long
}

/**
 * Expose extractors on a traversable of items.
 *
 * @tparam T
 */
class Items[+T <: Item : TypeTag](_items: => Traversable[T], itemsTag: => Long) {
    outer =>

    private var _cached : Seq[Item] = null
    private var _tag : Long = -1

    var filters = List.empty[(Item) => Boolean]

    def reset() : this.type = {
        filters = List.empty[(Item) => Boolean]
        _tag = -1
        this
    }

    def filter(filter: (T) => Boolean) : this.type = {
        filters ::= filter.asInstanceOf[Item => Boolean]
        _tag = -1
        this
    }

    def items = new Traversable[T] {
        def foreach[U](f: T => U): Unit = {
            if (_tag < 0 || _tag != itemsTag) {
                val newTag = itemsTag
                _cached = filters.foldLeft(_items.view)((prev, filter) => prev.filter(filter)).toSeq
                _tag = newTag
            }
            _cached.asInstanceOf[Seq[T]].foreach(f)
        }
    }

    def tagged(tags: Items.Filter*) : Items[T] = tagged(Seq() ++ tags)

    def tagged(tags: Iterable[Items.Filter]) : Items[T] = filter((item: T) => tags.exists(filter => item.tags.exists(filter(_))))
}

object Items {
    type Filter = (String) => Boolean

    implicit def stringFilter(tag: String) : Filter = _ == tag

    implicit def regexFilter(tag: Regex) : Filter = tag.pattern.matcher(_).matches()

    /**
     * Expose extractors on a traversable of items.
     *
     * @tparam T
     */

    implicit class RichItems[+T <: Item : TypeTag](items: Traversable[Item]) {
    /**
     * Case matching extractor for Event to catch a Command.
     * Usage: case grouped.Command(item, cmd) => matches if <item> in <grouped> received command <cmd>
     */
    object Command {
        def unapply(ev: Event) : Option[(T, T#State)] = ev match {
            case ReceivedCommandEvent(item, cmd) if items.exists(item == _) => Some((item.asInstanceOf[T], cmd.asInstanceOf[T#State]))
            case _ => None
        }
    }

    /**
     * Case matching extractor for Event to catch an Update.
     * Usage: case grouped.Update(item, from, to) => matches if <item> in <group> received update from <from> to <to>
     */
    object Update {
        def unapply(ev: Event) : Option[(T, T#State, T#State)] = ev match {
            case ReceivedUpdateEvent(item, from, to) if items.exists(item == _) => Some((item.asInstanceOf[T], from.asInstanceOf[T#State], to.asInstanceOf[T#State]))
            case _ => None
        }
    }
    }
}
