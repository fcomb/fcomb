package io.fcomb.services

import akka.actor._
import akka.persistence._
import akka.cluster.sharding._
import io.fcomb.trie._
import io.fcomb.models.comb._
import io.fcomb.persist.comb.{CombMethod => PCombMethod}
import io.fcomb.validations
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

object CombProcessor {
}

// class CombProcessor(timeout: Duration) extends PersistentActor
//   with AtLeastOnceDelivery with ActorLogging {
//   import context.dispatcher
//   import CombProcessor._

// }
