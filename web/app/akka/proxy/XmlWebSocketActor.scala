package akka.proxy


import scala.concurrent.duration._
import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, Identify, Props, ReceiveTimeout, Terminated}

/**
  * Created by zodiake on 17-4-17.
  */
class XmlWebSocketActor(out: ActorRef, path: String) extends Actor with ActorLogging {
  val deployer = context.actorOf(Props(new RemoteLookupProxy("akka.tcp://application@0.0.0.0:2550")))

  context.setReceiveTimeout(3 seconds)
  sendIdentifyRequest()

  def sendIdentifyRequest(): Unit = {
    val selection = context.actorSelection(path)
    selection ! Identify(path)
  }

  def receive = identify

  def identify: Receive = {
    case ActorIdentity(`path`, Some(actor)) =>
      context.setReceiveTimeout(Duration.Undefined)
      log.info("switching to active state")
      context.become(active(actor))
      context.watch(actor)

    case ActorIdentity(`path`, None) =>
      log.debug(path)
      log.error(s"Remote actor with path $path is not available.")

    case ReceiveTimeout =>
      sendIdentifyRequest()

    case msg: Any =>
      log.error(s"Ignoring message $msg, remote actor is not ready yet.")
  }

  def active(actor: ActorRef): Receive = {
    case Terminated(actorRef) =>
      log.info("Actor $actorRef terminated.")
      log.info("switching to identify state")
      context.become(identify)
      context.setReceiveTimeout(3 seconds)
      sendIdentifyRequest()
    case msg: Any => actor ! msg
  }
}
