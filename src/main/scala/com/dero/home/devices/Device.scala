package com.dero.home.devices

import java.nio.charset.StandardCharsets

import com.dero.home.states.{OpenClose, OnOff}
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.eclipse.paho.client.mqttv3._

import scala.collection._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.languageFeature.implicitConversions


/**
 * Created by daniel on 9/21/15.
 */
case class DeviceMapper[Command, State](commandOut: (Command) => String, stateIn: (String) => (State))

case class FacadeMapper[Command, State](commandIn: (String) => Command, stateIn: (String) => (State), stateOut: (State) => (String))

object DeviceBus {
    //implicit def stringDevice(topic: DeviceBus#Realm#Topic) = topic(DeviceMapper(((v: String) => v.toString, (v: String) =>), (v: String) => v.toString))
    implicit def stringFacade(topic: DeviceBus#Realm#Topic) = topic[String, String](FacadeMapper[String, String](n=>n, n=>n, n=>n))
    implicit def stringDevice(topic: DeviceBus#Realm#Topic) = topic[String, String](DeviceMapper[String, String](n=>n, n=>n))

    implicit def onOffFacade(topic: DeviceBus#Realm#Topic) = topic(FacadeMapper[OnOff.Type, OnOff.Type](OnOff.withName(_), OnOff.withName(_), _.toString))
    implicit def onOffDevice(topic: DeviceBus#Realm#Topic) = topic(DeviceMapper[OnOff.Type, OnOff.Type](_.toString, OnOff.withName(_)))

    implicit def openCloseFacade(topic: DeviceBus#Realm#Topic) = topic(FacadeMapper[OpenClose.Type, OpenClose.Type](OpenClose.withName(_), OpenClose.withName(_), _.toString))
    implicit def openCloseDevice(topic: DeviceBus#Realm#Topic) = topic(DeviceMapper[OpenClose.Type, OpenClose.Type](_.toString, OpenClose.withName(_)))
}

abstract class DeviceBus {
    bus =>

    type Callback = (String, String) => Unit

    def publish(topic: String, payload: String, qos: Int, retained: Boolean)
    def subscribe(topic: String, callback: Callback)
    def unsubscribe(topic: String, callback: Callback)

    def apply(realm: String) = Realm(realm)

    case class Realm(realm: String) {

        case class Topic(topic: String) {

            case class Device[Command, State](mapper: DeviceMapper[Command, State]) {
                def command(command: Command) : Unit = {
                    bus.publish(realm + "/set/" + topic, mapper.commandOut(command), 1, false)
                }

                object onState extends scala.collection.mutable.Publisher[State] {
                    def route(topic: String, state: String) = publish(mapper.stateIn(state))
                }

                bus.subscribe(realm + "/status/" + topic, onState.route)
            }

            case class Facade[Command, State](mapper: FacadeMapper[Command, State]) {
                def update(state: State) : Unit = {
                    bus.publish(realm + "/status/" + topic, mapper.stateOut(state), 0, true)
                }

                object onCommand extends mutable.Publisher[Command] {
                    def route(topic: String, command: String) = publish(mapper.commandIn(command))
                }

                object onState extends mutable.Publisher[State] {
                    def route(topic: String, state: String) = publish(mapper.stateIn(state))
                }

                bus.subscribe(realm + "/set/" + topic, onCommand.route)
                bus.subscribe(realm + "/status/" + topic, onState.route)

            }

            def apply[Command, State](mapper: FacadeMapper[Command, State]) = Facade(mapper)
            def apply[Command, State](mapper: DeviceMapper[Command, State]) = Device(mapper)
        }

        def apply(topic: String) = Topic(topic)
    }

}

class MQTTDeviceBus(serverURI: String, clientId: String) extends DeviceBus with StrictLogging {
    val mqtt = new MqttAsyncClient(serverURI, clientId)

    def connect(): Unit = {
        while (true) {
            try {
                synchronized {
                    if (mqtt.isConnected)
                        return
                    logger.info(s"Connecting to $serverURI as $clientId")
                    mqtt.connect().waitForCompletion()
                    logger.info(s"DONE. Connecting to $serverURI as $clientId")

                    // subscribe to all topics

                    topicMap.keys.foreach(topic => {
                        logger.debug(s"Subscribing $topic")
                        mqtt.subscribe(topic, 0).waitForCompletion()
                        logger.debug(s"DONE. Subscribing $topic")
                    })

                }
            } catch {
                case e : MqttException => logger.warn(s"Exception at #connect: $e. Retrying!")
            }
        }
    }

    var topicMap = new mutable.HashMap[String, mutable.Set[Callback]] with mutable.MultiMap[String, Callback]

    mqtt.setCallback(new MqttCallback {
        override def messageArrived(topic: String, msg: MqttMessage): Unit = onMessageArrived(topic, msg)

        override def connectionLost(throwable: Throwable): Unit = connect()

        override def deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken): Unit = {}
    })

    override def publish(topic: String, payload: String, qos: Int, retained: Boolean): Unit = {
        while (true) {
            try {
                logger.debug(s"Publishing $topic -> $payload")
                mqtt.publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained).waitForCompletion()
                logger.debug(s"DONE. Publishing $topic -> $payload")
                return
            } catch {
                case e : MqttException => logger.warn(s"Exception at #publish: $e. Retrying!")
            }
        }
    }

    override def subscribe(topic: String, callback: Callback): Unit = {
        synchronized {
            val sub = !topicMap.contains(topic)
            topicMap.addBinding(topic, callback)
            if (sub) {
                try {
                    logger.debug(s"Subscribing $topic")
                    Some(mqtt.subscribe(topic, 0))
                } catch {
                    case e : MqttException => logger.warn(s"Exception at #subscribe: $e"); None
                }
            } else None
        } foreach(token => {
            token.waitForCompletion()
            logger.debug(s"DONE. Subscribing $topic")
        })
    }

    override def unsubscribe(topic: String, callback: Callback): Unit = {
        synchronized {
            topicMap.removeBinding(topic, callback)
            if (!topicMap.contains(topic)) {
                try {
                    logger.debug(s"Unsubscribing $topic")
                    Some(mqtt.unsubscribe(topic))
                } catch {
                    case e : MqttException => logger.warn(s"Exception at #unsubscribe: $e"); None
                }
            } else None
        } foreach(token => {
            token.waitForCompletion()
            logger.debug(s"DONE. Subscribing $topic")
        })
    }

    val queue = new java.util.concurrent.LinkedBlockingQueue[(String, String)]

    Future {
        while (true) {
            val (topic, payload) = queue.take()
            logger.debug(s"Message processing $topic/$payload")
            val sub = synchronized { topicMap.get(topic) }
            try {
                sub.foreach(_.foreach(_(topic, payload)))
            } catch {
                case e : Throwable => logger.error(s"Exception while precessing $topic -> $payload: $e")
            }
            logger.debug(s"DONE. Message processing $topic -> $payload")
        }
    }

    private def onMessageArrived(topic: String, msg: MqttMessage): Unit = {
        val payload = new String(msg.getPayload, StandardCharsets.UTF_8)
        logger.debug(s"Message arriving $topic: $payload")
        queue.add((topic, payload))
        logger.debug(s"DONE. Message arriving $topic -> $payload")
    }

    connect()
}

