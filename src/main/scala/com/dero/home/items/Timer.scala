package com.dero.home.items

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import com.dero.home.Module
import com.dero.home.items.Timer.TimerProxy
import com.dero.home.states.OnOff
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.quartz._
import org.quartz.impl.StdSchedulerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

class Timer(module: Module) extends Item(module) {
    override def This = typeOf[Timer]
    type State = OnOff.Type

    type Then = () => Unit

    var _lastTime : Long = 0
    var _then : Then = null
    var _jobKeys = List.empty[JobKey]

    def run(func: Then) : this.type = {
        this._then = func
        this
    }

    def reset() : this.type = {
        val oldKeys = synchronized {
            val oldKeys = _jobKeys
            _jobKeys = List.empty
            oldKeys
        }

        oldKeys.foreach(Timer.map.remove(_))

        Timer.sch.deleteJobs(oldKeys.asJava)

        this
    }

    def cron(cronExpression: String) : this.type = {
        val ce = cronExpression match {
            case "NOON" => "0 0 12 * * ?"
            case "MIDNIGHT" => "0 0 0 * * ?"
            case s => s
        }

        schedule(trigger = org.quartz.TriggerBuilder.newTrigger
            .withIdentity(Timer.counter.incrementAndGet.toString)
            .withSchedule(CronScheduleBuilder.cronSchedule(ce))
            .build)
    }

    def after(duration: Duration) : this.type = {
        schedule(org.quartz.TriggerBuilder.newTrigger
            .withIdentity(Timer.counter.incrementAndGet.toString)
            .startAt(new Date(System.currentTimeMillis + duration.toMillis))
            .build)

        this
    }

    private def schedule(trigger: Trigger) : this.type = {
        val key = new JobKey(Timer.counter.incrementAndGet.toString)

        Timer.map += (key -> this)

        Timer.sch.scheduleJob(JobBuilder.newJob(classOf[TimerProxy])
            .withIdentity(key)
            .build, trigger)

        synchronized {
            _jobKeys ::= key
        }

        this
    }

    private def fire(context : JobExecutionContext) = {
        val trigger = context.getTrigger

        if (trigger.getNextFireTime == null) {
            val key = context.getJobDetail.getKey

            Timer.map.remove(key)
            synchronized {
                _jobKeys = _jobKeys.filterNot(_ == key)
            }
        }

        if (_then != null ) {
            _then()
            _lastTime = System.nanoTime()
        }
    }

    def lastTime = _lastTime

    val job = org.quartz.JobBuilder.newJob(classOf[TimerProxy]).build()

    override def finalize(): Unit = {
        reset()
        super.finalize()
    }
}

object Timer extends StrictLogging {
    val sf = new StdSchedulerFactory()
    val sch = sf.getScheduler()
    sch.start()
    val map = scala.collection.concurrent.TrieMap.empty[JobKey, Timer]

    val counter = new AtomicInteger()

    class TimerProxy extends Job {
        def execute(context : JobExecutionContext) : Unit = {
            try {
                map.get(context.getJobDetail.getKey).foreach(_.fire(context))
            } catch {
                case e : Throwable => logger.error(s"Exception while executing timer: $e")
            }
        }
    }
}
