/**
 * Author: Philipp Haller
 */
package io.github.fsommar.threadring

import akka.actor.{ActorSystem, Props}
import akka.event.Logging

import lacasa.akka.actor.{Actor, ActorRef, SafeReceive}
import lacasa.{Box, CanAccess, Safe, Utils}
import Box._


object Message {
  implicit val messageIsSafe = new Safe[Message] {}
  implicit val pingMessageIsSafe = new Safe[PingMessage] {}
  implicit val exitMessageIsSafe = new Safe[ExitMessage] {}
  implicit val dataMessageIsSafe = new Safe[DataMessage] {}
}

sealed trait Message

class PingMessage(val pingsLeft: Int) extends Message {
  def hasNext: Boolean =
    pingsLeft > 0

  def next(): PingMessage =
    new PingMessage(pingsLeft - 1)
}

class ExitMessage(val exitsLeft: Int) extends Message {
  def hasNext: Boolean =
    exitsLeft > 0

  def next(): ExitMessage =
    new ExitMessage(exitsLeft - 1)
}

class DataMessage(val data: ActorRef) extends Message

/* Steps when converting an existing Akka program to LaCasa:
 *
 * 1. Use trait `Actor` instead of `Actor`; `Actor` provides a `receive`
 *    method with a different signature.
 * 2. Send and receive boxes as messages.
 *    This requires changes such as opening each received box.
 * 3. Creating boxes for messages: here, sent message classes may need to be
 *    changed to provide a no-arg constructor.
 */
private class ThreadRingActor(id: Int, numActorsInRing: Int) extends Actor with SafeReceive {
  val log = Logging(context.system, this)

  private var nextActor: ActorRef = _

  def safeReceive: Receive = {
    case pm: PingMessage =>
      log.info(s"received PingMessage: pings left == ${pm.pingsLeft}")
      if (pm.hasNext) {
        nextActor ! pm.next()
      } else {
        nextActor ! new ExitMessage(numActorsInRing)
      }

    case em: ExitMessage =>
      if (em.hasNext) {
        nextActor ! em.next()
        log.info(s"stopping ${self.path}")
        context.stop(self)
      } else {
        log.info(s"stopping ${self.path}")
        context.stop(self)
      }

    case dm: DataMessage =>
      log.info(s"received DataMessage: ${dm.data}")
      nextActor = dm.data
  }

}

object ThreadRing {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ThreadRing")

    val numActorsInRing = /* ThreadRingConfig.N */ 2
    val ringActors = Array.tabulate[ActorRef](numActorsInRing)(i => {
      system.actorOf(Props(new ThreadRingActor(i, numActorsInRing)))
    })

    for ((loopActor, i) <- ringActors.view.zipWithIndex) {
      val nextActor = ringActors((i + 1) % numActorsInRing)
      loopActor ! new DataMessage(nextActor)
    }
    ringActors(0) ! new PingMessage(10)

    Thread.sleep(2000)
    system.terminate()
  }

}
