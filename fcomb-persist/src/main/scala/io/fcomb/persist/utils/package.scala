package io.fcomb.persist

import slick.jdbc.{SetParameter, SQLActionBuilder}

package object utils {
  def sql[T](query: String)(p: T)(implicit f: SetParameter[T]) = {
    SQLActionBuilder(Seq(query), f.applied(p))
  }
}
