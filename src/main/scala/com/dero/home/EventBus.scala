package com.dero.home

import com.dero.home.events.Event
import com.dero.home.items.Rule

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by daniel on 9/5/15.
 */
class EventBus {
    val _modules = scala.collection.concurrent.TrieMap.empty[AbstractModule, Any]

    def findRules(ev: Event) : Iterable[Rule] = {
        _modules.keys.flatMap(_.items.filter(_.isInstanceOf[Rule]).map(_.asInstanceOf[Rule]).filter(rule => rule._then != null && rule._then.isDefinedAt(ev)))
    }

    def notify(ev: Event) = {
        findRules(ev).foreach(rule => {
            Future {
                rule._then(ev)
                rule._lastTime = System.nanoTime()
            }
        })
    }
}
