/**
 * Author: Fredrik Sommar
 */
package io.github.fsommar.chameneos

import akka.actor.{ActorSystem, Props}
import akka.event.Logging

import lacasa.akka.actor.{Actor, ActorRef}
import lacasa.Safe


object Chameneos {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("Chameneos")

    val mallActor: ActorRef = system.actorOf(Props(
      new ChameneosMallActor(
        /* ChameneosConfig.numMeetings */ 2000,
        /* ChameneosConfig.numChameneos */ 10)))
    Thread.sleep(2000)
    system.terminate()
  }

  object Message {
    implicit val MessageIsSafe = new Safe[Message] {}
    implicit val MeetMsgIsSafe = new Safe[MeetMsg] {}
    implicit val ChangeMsgIsSafe = new Safe[ChangeMsg] {}
    implicit val MeetingCountMsgIsSafe = new Safe[MeetingCountMsg] {}
    implicit val ExitMsgIsSafe = new Safe[ExitMsg] {}
  }

  sealed trait Message
  case class MeetMsg(color: Color, sender: ActorRef) extends Message
  case class ChangeMsg(color: Color, sender: ActorRef) extends Message
  case class MeetingCountMsg(count: Int, sender: ActorRef) extends Message
  case class ExitMsg(sender: ActorRef) extends Message

  private class ChameneosMallActor(numMeetings: Int, numChameneos: Int) extends Actor {
    val log = Logging(context.system, this)

    var numMeetingsLeft: Int = numMeetings
    var sumMeetings: Int = 0
    var numFaded: Int = 0
    var waitingChameneo: Option[ActorRef] = None

    val colors = List(YELLOW, BLUE, RED)
    1 to numChameneos foreach { i =>
      val color = colors(i % 3)
      val chameneoActor: ActorRef = context.system.actorOf(Props(
        new ChameneoActor(self, i, color)))
    }

    override def receive: Receive = {
      case message: MeetingCountMsg =>
        numFaded += 1
        sumMeetings += message.count
        if (numFaded == numChameneos) {
          log.info("stopping")
          context.stop(self)
        }
      case message: MeetMsg =>
        if (numMeetingsLeft > 0) {
          if (waitingChameneo == None) {
            waitingChameneo = Some(message.sender)
          } else {
            numMeetingsLeft -= 1
            waitingChameneo.get ! message
            waitingChameneo = None
          }
        } else {
          message.sender ! new ExitMsg(self)
        }
      case _ => ???
    }

  }

  private class ChameneoActor(mall: ActorRef, id: Int, var color: Color) extends Actor {
    val log = Logging(context.system, this)
    private var meetings: Int = 0

    mall ! new MeetMsg(color, self)

    override def receive: Receive = {
      case message: MeetMsg =>
        color = color.complement(message.color)
        meetings += 1
        message.sender ! new ChangeMsg(color, self)
        mall ! new MeetMsg(color, self)
      case message: ChangeMsg =>
        color = message.color
        meetings += 1
        mall ! new MeetMsg(color, self)
      case message: ExitMsg =>
        color = FADED
        log.info(s"Chameneo #${id} is now a faded color.")
        message.sender ! new MeetingCountMsg(meetings, self)
        context.stop(self)
      case _ => ???
    }
  }

}

sealed trait Color {

  def complement(otherColor: Color): Color = {
    this match {
      case RED =>
        otherColor match {
          case RED => RED
          case YELLOW => BLUE
          case BLUE => YELLOW
          case FADED => FADED
        }
      case YELLOW =>
        otherColor match {
          case RED => BLUE
          case YELLOW => YELLOW
          case BLUE => RED
          case FADED => FADED
        }
      case BLUE =>
        otherColor match {
          case RED => YELLOW
          case YELLOW => RED
          case BLUE => BLUE
          case FADED => FADED
        }
      case FADED => FADED
    }
  }
}

case object RED extends Color
case object YELLOW extends Color
case object BLUE extends Color
case object FADED extends Color
