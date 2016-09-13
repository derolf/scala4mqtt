import com.dero.home.Module
import com.dero.home.devices.MQTTDeviceBus
import com.dero.home.{Module, EventBus}
import com.dero.home.states.OnOff
import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}
import com.dero.home.items.Items._

import scala.concurrent.duration._

/**
 * Created by daniel on 8/30/15.
 */
class RuleTestModule() extends Module(new EventBus)  {

    val mqtt = new MQTTDeviceBus("tcp://localhost:1883", "home")

    val my= mqtt("home")

    val Logger = Text() autoUpdate true /*in my("Logger")*/
    val LoggerR = Rule() run {
        case Logger.Command(cmd) => println(cmd)
    }

    Rule() run { case Item.Update(who, from, to) if who != Logger => Logger.command(s"Item $who received update from $from to $to") }
    Rule() run { case Item.Command(who, cmd) if who != Logger => Logger.command(s"Item $who received command $cmd") }

    val Lights = Group(Switch.tagged("Lights"))
    val PIRs = Group(Switch.tagged("PIRs"))

    val BathroomPIR = Switch() tagged "PIRs" facade my("BathroomPIR") autoUpdate true
    val Bathroom = Switch() tagged "Lights" facade my("Bathroom") autoUpdate true

    val BathroomPIRR = Rule() run {
        case BathroomPIR.Update(_, OnOff.ON) => Bathroom.command(OnOff.ON) }

    val switches = Switch.all()
    val Switches = Group.Switch() items Switch.all()
    val LightSwitches = Group.Switch() items Switch.tagged("Lights")

    val SwitchesOn = Rule() run { case Switches.items.Update(_, _, OnOff.ON) => }

    val LightsOff = Rule() run { case Lights.items.Update(_, _, OnOff.OFF) => }

    val Rules = Group.Rule() items Rule.all

    val TestTimer = Timer() run (() => {})
}

class RuleTest extends FlatSpec with Matchers with Eventually {
    def tick = {
        var res = System.nanoTime()
        while (System.nanoTime() == res) {}
        res = System.nanoTime()
        while (System.nanoTime() == res) {}
        res
    }

    val mod = new RuleTestModule()

    import mod._

    Group.Switch.check(Switches) should be(true)
    Group.Switch.check(Rules) should be(false)

    Bathroom.tags.toSet should be(Set("Lights"))
    BathroomPIR.tags.toSet should be(Set("PIRs"))

    Lights.items.toSet should be(Set(Bathroom))
    PIRs.items.toSet should be(Set(BathroomPIR))

    var now = tick
    BathroomPIR.command(OnOff.ON)
    Thread.sleep(100)

    Bathroom.state should be(OnOff.ON)
    SwitchesOn.lastTime should be > now
    LightsOff.lastTime should be < now

    now = tick
    BathroomPIR.command(OnOff.OFF)
    Thread.sleep(100)

    Bathroom.state should be(OnOff.ON)
    SwitchesOn.lastTime should be < now
    LightsOff.lastTime should be < now

    now = tick
    Bathroom.command(OnOff.OFF)
    Thread.sleep(100)

    Bathroom.state should be(OnOff.OFF)
    SwitchesOn.lastTime should be < now
    LightsOff.lastTime should be > now

    TestTimer.after(1 second)

    Thread.sleep(200)
    TestTimer.lastTime should be < now
    Thread.sleep(1000)
    TestTimer.lastTime should be > now
}


