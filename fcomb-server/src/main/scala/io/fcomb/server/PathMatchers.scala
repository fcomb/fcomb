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
import akka.http.scaladsl.model.Uri.Path
import io.fcomb.models.acl.MemberKind
import io.fcomb.models.common.{Enum, EnumItem, Slug}

abstract class PathMatcher[E <: EnumItem](enum: Enum[E]) extends PathMatcher1[E] {
  final def apply(path: Path) = path match {
    case Path.Segment(segment, tail) => Matched(tail, Tuple1(enum.withName(segment)))
    case _                           => Unmatched
  }
}

object PathMatchers {
  final object SlugPath extends PathMatcher1[Slug] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Slug.parse(segment)))
      case _                           => Unmatched
    }
  }

  final object MemberKindPath extends PathMatcher(MemberKind)
}
