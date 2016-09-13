package com.dero.home.items

import java.util.Date

import com.dero.home.AbstractModule
import com.dero.home.devices.{DeviceBus}
import com.dero.home.events.{Event, ReceivedCommandEvent, ReceivedUpdateEvent}

import scala.collection.mutable
import scala.languageFeature.implicitConversions
import scala.reflect.runtime.universe._

/**
 * command(X)
 * -> generate receiveCommand(X) event
 * -> post <device-realm>/set/<device-topic> X
 * -> if autoUpdate, update(X)
 *
 * update(X)
 * -> update state holder, generate receiveUpdate(oldState, X) event
 * -> post <facade-realm>/status/<facade-topic> X
 *
 * <facade-realm>/status/<facade-topic> X
 * -> ONLY ON BOOT, update state holder (don't generate receiveUpdate)
 *
 * <facade-realm>/set/<facade-topic> X
 * -> command(X)
 *
 * <device-realm>/status/<device-topic> X
 * -> update(X)
 *
 */
abstract class Item(val module: AbstractModule) {
    outer =>

    def This = typeOf[Item]
    type State
    type Command

    private var _autoUpdate = false

    private var _state: State = null.asInstanceOf[State]
    private var _stateTime : Date = null

    private var _tags: Set[String] = Set.empty

    private var _device:  DeviceBus#Realm#Topic#Device[Command, State] = null

    private var _facade:  DeviceBus#Realm#Topic#Facade[Command, State] = null

    /**
     * Bind this to the given device.
     *
     * @param device
     * @return
     */
    def device(device: DeviceBus#Realm#Topic#Device[Command, State]) : this.type = {
        synchronized {
            if (_device != null)
                _device.onState.removeSubscription(onDeviceUpdate)
            _device = device
            _device.onState.subscribe(onDeviceUpdate)
        }
        this
    }

    /**
     * Bind this to the given facade
     *
     * @param facade
     * @return
     */
    def facade(facade: DeviceBus#Realm#Topic#Facade[Command, State]) : this.type = {
        synchronized {
            if (_facade != null) {
                _facade.onCommand.removeSubscription(onFacadeCommand)
                _facade.onState.removeSubscription(onFacadeUpdate)
            }
            _facade = facade
            _facade.onCommand.subscribe(onFacadeCommand)
            _facade.onState.subscribe(onFacadeUpdate)
        }
        this
    }

    /**
     * State of item.
     *
     * @return
     */
    def state = _state

    /**
     * Time when last state was updated.
     *
     * @return
     */
    def stateUpdated = _stateTime

    /**
     * Tags of this item.
     * @return
     */
    def tags : Iterable[String] = _tags

    def tagged(firstTag: String, otherTags: String*): this.type = tagged(Seq(firstTag) ++ otherTags)

    def tagged(tags: Iterable[String]): this.type = {
        _tags = tags.toSet
        module.notifyItemsChanged()
        this
    }

    /**
     * If <flag> is true, any command <cmd> received by this item will be translated to an update(cmd).
     *
     * @param flag
     * @return
     */
    def autoUpdate(flag: Boolean) : this.type = {
        _autoUpdate = flag
        this
    }

    /**
     * Send command to this item.
     *
     * -> generate receiveCommand(X) event
     * -> post <device-realm>/set/<device-topic> X*
     * -> if autoUpdate, update(X)
     */
    def command(cmd: Command): this.type = {
        synchronized {
            module.bus.notify(ReceivedCommandEvent[Command](this, cmd))

            if (_device != null)
                _device.command(cmd)

            if (_autoUpdate)
                update(cmd.asInstanceOf[State])
            this
        }
    }

    /**
     * Update state of this item.
     *
     * -> update state holder, generate receiveUpdate(oldState, X) event
     * -> post <facade-realm>/status/<facade-topic> X
     */
    def update(state: State) : this.type = {
        synchronized {
            val prevState = _state
            _state = state
            _stateTime = new Date()

            module.bus.notify(ReceivedUpdateEvent[State](outer, prevState, state))

            if (_facade != null)
                _facade.update(state)
            this
        }
    }

    private object onDeviceUpdate extends mutable.Subscriber[State, scala.collection.mutable.Publisher[State]#Pub] {
        override def notify(pub: mutable.Publisher[State]#Pub, event: State): Unit = {
            outer.update(event)
        }
    }

    private object onFacadeCommand extends mutable.Subscriber[Command, mutable.Publisher[Command]#Pub] {
        override def notify(pub: mutable.Publisher[Command]#Pub, event: Command): Unit = {
            outer.command(event)
        }
    }

    private object onFacadeUpdate extends mutable.Subscriber[State, mutable.Publisher[State]#Pub] {
        override def notify(pub: mutable.Publisher[State]#Pub, event: State): Unit = {
            if (_state == null) {
                _state = state
                _stateTime = new Date()
            }
        }
    }

    /**
     * Case matching extractor for Event to catch a Command.
     * Usage: case item.Command(cmd) => matches if <item> received command <cmd>
     */
    object Command {
        def unapply(ev: Event) : Option[Command] = ev match {
            case ev: ReceivedCommandEvent[_] if ev.item == outer => Some(ev.cmd.asInstanceOf[Command])
            case _ => None
        }
    }

    /**
     * Case matching extractor for Event to catch an Update.
     * Usage: case item.Update(from, to) => matches if <item> received update from <from> to <to>
     */
    object Update {
        def unapply(ev: Event) : Option[(State, State)] = ev match {
            case ev: ReceivedUpdateEvent[_] if ev.item == outer => Some((ev.fromState.asInstanceOf[State], ev.toState.asInstanceOf[State]))
            case _ => None
        }
    }

    override def toString = module.findItemFieldName(this) match {
        case (module, Some(field)) => s"$module#$field"
        case (module, None) => s"$module#<unknown>"
    }

    module.addItem(this)

    /**
     * Destroy this item.
     */
    def destroy() : Unit = {
        device(null)
        facade(null)
        module.removeItem(this)
    }
}
