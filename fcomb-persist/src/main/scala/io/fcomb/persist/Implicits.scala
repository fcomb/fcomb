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

package io.fcomb.persist

import java.sql.JDBCType
import java.util.UUID
import slick.jdbc.SetParameter

object Implicits {
  final implicit val setUuid: SetParameter[UUID] = {
    SetParameter { (u, pp) =>
      pp.setObject(u, JDBCType.BINARY.getVendorTypeNumber)
    }
  }

  final implicit val seqUuid: SetParameter[Seq[UUID]] = setParameterSeq[UUID]

  private def setParameterSeq[T](implicit pconv: SetParameter[T]): SetParameter[Seq[T]] =
    SetParameter { (s, pp) =>
      s.map(pconv.apply(_, pp))
      ()
    }
}
