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

package io.fcomb.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.PathMatcher._
import akka.http.scaladsl.model.Uri
import io.fcomb.models.acl.{MemberKind => MMemberKind}
import io.fcomb.models.common.{Slug => MSlug}

object Path {
  final object Slug extends PathMatcher1[MSlug] {
    def apply(path: Uri.Path) = path match {
      case Uri.Path.Segment(segment, tail) => Matched(tail, Tuple1(MSlug.parse(segment)))
      case _                               => Unmatched
    }
  }

  final object MemberKind extends PathMatcher1[MMemberKind] {
    def apply(path: Uri.Path) = path match {
      case Uri.Path.Segment(segment, tail) =>
        MMemberKind.withNameOption(segment) match {
          case Some(kind) => Matched(tail, Tuple1(kind))
          case _          => Unmatched
        }
      case _ => Unmatched
    }
  }
}
