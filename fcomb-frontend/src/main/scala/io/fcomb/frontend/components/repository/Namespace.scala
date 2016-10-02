/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.frontend.components.repository

import cats.Eq
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.models.{Owner, OwnerKind}

sealed trait Namespace

sealed trait OwnerNamespace extends Namespace {
  val id: Option[Int]
  val slug: String

  def groupTitle: String
  def toOwner: Option[Owner]
}

object Namespace {
  final case object All extends Namespace

  final case class User(slug: String, id: Option[Int] = None) extends OwnerNamespace {
    def isCurrentUser = AppCircuit.currentUser.exists(_.username == slug)
    def toOwner       = id.map(Owner(_, OwnerKind.User))
    def groupTitle    = "Users"
  }

  final case class Organization(slug: String, id: Option[Int] = None) extends OwnerNamespace {
    def toOwner    = id.map(Owner(_, OwnerKind.Organization))
    def groupTitle = "Organizations"
  }

  implicit val valueEq: Eq[Namespace] = Eq.fromUniversalEquals
}
