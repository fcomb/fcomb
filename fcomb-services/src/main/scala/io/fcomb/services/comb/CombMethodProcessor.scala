// package io.fcomb.services

// import akka.actor._
// import akka.persistence._
// import akka.cluster.sharding._
// import io.fcomb.trie._
// import io.fcomb.models.comb._
// import io.fcomb.persist.comb.{CombMethod => PCombMethod}
// import io.fcomb.validations
// import scala.concurrent.Future
// import scala.language.implicitConversions
// import scala.collection.mutable.ListBuffer
// import scala.concurrent.duration.Duration
// import scala.util.{Try, Success, Failure}

// object CombMethodProcessor {
//   val extractEntityId: ShardRegion.ExtractEntityId = {
//     case EntityEnvelope(combId, payload) ⇒ (combId.toString, payload)
//   }

//   val numberOfShards = 1 // TODO: move into config

//   val extractShardId: ShardRegion.ExtractShardId = {
//     case EntityEnvelope(combId, _) ⇒ (combId % numberOfShards).toString
//   }

//   def props(timeout: Duration) =
//     Props(classOf[CombMethodProcessor], timeout)

//   case class CombMethodProcessorRegion(ref: ActorRef)

//   val shardName = "CombMethodShard"

//   def lookup()(implicit sys: ActorSystem) =
//     sys.dispatchers.lookup(shardName)

//   def startRegion(timeout: Duration)(implicit sys: ActorSystem) =
//     CombMethodProcessorRegion(ClusterSharding(sys).start(
//       typeName = shardName,
//       entityProps = props(timeout),
//       settings = ClusterShardingSettings(sys),
//       extractEntityId = extractEntityId,
//       extractShardId = extractShardId
//     ))

//   sealed trait CombMethodCommand

//   @SerialVersionUID(1L)
//   case class AddMethod(method: CombMethod) extends CombMethodCommand

//   @SerialVersionUID(1L)
//   case class UpdateMethod(method: CombMethod) extends CombMethodCommand

//   @SerialVersionUID(1L)
//   case class RemoveMethod(method: CombMethod) extends CombMethodCommand

//   @SerialVersionUID(1L)
//   case class DestroyAll() extends CombMethodCommand

//   @SerialVersionUID(1L)
//   case object GetRouteTrie

//   case class EntityEnvelope(combId: Long, payload: Any)
// }

// class CombMethodProcessor(timeout: Duration) extends PersistentActor
//   with AtLeastOnceDelivery with ActorLogging {
//   import context.dispatcher
//   import CombMethodProcessor._
//   import ShardRegion.Passivate

//   context.setReceiveTimeout(timeout)

//   val combId = self.path.name.toLong

//   override def persistenceId = s"comb-method-processor-$combId"

//   @SerialVersionUID(1L)
//   case class State(trie: RouteNode[CombMethod] = RouteTrie.empty)

//   var state = State()

//   def updateState(f: RouteNode[CombMethod] => RouteNode[CombMethod]) = {
//     state = state.copy(trie = f(state.trie))
//     state
//   }

//   def snapshotState(f: RouteNode[CombMethod] => RouteNode[CombMethod]) =
//     saveSnapshot(updateState(f))

//   val messages = new ListBuffer[(ActorRef, CombMethodCommand)]

//   def handleValidation[T](
//     v: validations.ValidationResult[T],
//     replyTo: ActorRef
//   )(
//     f: T => Unit
//   ) = {
//     v match {
//       case r @ scalaz.Success(res) =>
//         f(res)
//         replyTo ! r
//       case e @ scalaz.Failure(_) =>
//         replyTo ! e
//     }
//     backToWork()
//   }

//   def handleFutureValidation[T](
//     fv: Future[validations.ValidationResult[T]],
//     replyTo: ActorRef
//   )(
//     f: T => Unit
//   ) =
//     fv.onComplete {
//       case Success(res) =>
//         handleValidation(res, replyTo)(f)
//       case Failure(e) =>
//         replyTo ! PCombMethod.validationError("_", e.getMessage)
//         backToWork()
//     }

//   def tryRoute(
//     routeF: RouteNode[CombMethod] => RouteNode[CombMethod]
//   )(
//     f: ActorRef => Unit
//   ) = {
//     Try(routeF(state.trie)) match {
//       case Success(_) =>
//         f(sender)
//       case Failure(e) =>
//         sender() ! PCombMethod.validationError("uri", e.getMessage())
//     }
//   }

//   def idle: Receive = {
//     case AddMethod(m) =>
//       tryRoute(_ + (m.uri, m.routeKind, m)) { replyTo =>
//         state.trie.getMethodValue(m.routeKind, m.uri) match {
//           case Some(v) =>
//             if (v.copy(id = None) == m) replyTo ! validations.successResult(v)
//             else replyTo ! PCombMethod.validationError("id", "already exists")
//           case _ =>
//             context.become(busy, true)
//             handleFutureValidation(PCombMethod.create(m), replyTo) { res =>
//               snapshotState(_ + (res.uri, res.routeKind, res))
//             }
//         }
//       }
//     case DestroyAll =>
//       val replyTo = sender
//       context.become(terminate(replyTo), true)
//       PCombMethod.destroyByCombId(combId).onComplete {
//         case Success(_) =>
//           deleteSnapshots(new SnapshotSelectionCriteria)
//         case Failure(e) =>
//           replyTo ! PCombMethod.validationError("_", e.getMessage)
//           context.become(idle)
//       }
//     case ReceiveTimeout ⇒
//       context.parent ! Passivate(stopMessage = PoisonPill)
//     case GetRouteTrie =>
//       sender ! state.trie
//   }

//   def busy: Receive = {
//     case msg: CombMethodCommand =>
//       messages += ((sender, msg)) // TODO: limit
//     case GetRouteTrie =>
//       sender ! state.trie
//   }

//   def terminate(replyTo: ActorRef): Receive = {
//     case _: DeleteSnapshotsSuccess =>
//       replyTo ! validations.successResult(())
//       context.parent ! Passivate(stopMessage = PoisonPill)
//     case _: DeleteSnapshotsFailure =>
//       deleteSnapshots(new SnapshotSelectionCriteria)
//     case _ =>
//   }

//   def backToWork() = {
//     context.become(idle, true)
//     messages.map { case (s, m) => self.tell(m, s) }
//     messages.clear()
//   }

//   def receiveCommand = idle

//   def receiveRecover = {
//     case RecoveryCompleted =>
//     case SnapshotOffer(_, snapshot: State) =>
//       state = snapshot
//   }
// }
