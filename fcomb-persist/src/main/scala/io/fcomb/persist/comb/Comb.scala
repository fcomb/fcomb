// package io.fcomb.persist.comb

// import io.fcomb.Db._
// import io.fcomb.RichPostgresDriver.api._
// import io.fcomb.models
// import io.fcomb.models.comb
// import io.fcomb.persist._
// import io.fcomb.validations._
// import java.time.LocalDateTime
// import java.util.UUID
// import scala.concurrent.{ ExecutionContext, Future }
// import scalaz._
// import scalaz.Scalaz._

// class CombTable(tag: Tag) extends Table[comb.Comb](tag, "combs") with PersistTableWithAutoLongPk {
//   def userId = column[UUID]("user_id")
//   def name = column[String]("name")
//   def slug = column[String]("slug")
//   def createdAt = column[LocalDateTime]("created_at")
//   def updatedAt = column[LocalDateTime]("updated_at")

//   def * =
//     (id, userId, name, slug, createdAt, updatedAt) <>
//       ((comb.Comb.apply _).tupled, comb.Comb.unapply)
// }

// object Comb extends PersistModelWithAutoLongPk[comb.Comb, CombTable] {
//   val table = TableQuery[CombTable]

//   def create(
//     userId: UUID,
//     name:   String,
//     slug:   Option[String]
//   )(implicit ec: ExecutionContext): Future[ValidationModel] = {
//     val timeAt = LocalDateTime.now()
//     create(comb.Comb(
//       userId = userId,
//       name = name,
//       slug = makeSlug(name, slug),
//       createdAt = timeAt,
//       updatedAt = timeAt
//     ))
//   }

//   // TODO: add validation that slug doesn't have an UUID format
//   // TODO: name to slug with some random digits by default
//   private def makeSlug(name: String, slug: Option[String]) =
//     slug match {
//       case Some(s) => s.toLowerCase
//       case None    => id.toString
//     }

//   def updateByRequest(id: Long)(
//     name: String,
//     slug: Option[String]
//   )(implicit ec: ExecutionContext): Future[ValidationModel] =
//     update(id) { comb =>
//       comb.copy(
//         name = name,
//         slug = makeSlug(name, slug),
//         updatedAt = LocalDateTime.now()
//       )
//     }

//   private val findBySlugCompiled = Compiled { slug: Rep[String] =>
//     table.filter(_.slug === slug.toLowerCase).take(1)
//   }

//   private val findBySlugWithinUserCompiled = Compiled {
//     (userId: Rep[UUID], slug: Rep[String]) =>
//       table.filter { f =>
//         f.userId === userId && f.slug === slug.toLowerCase
//       }.take(1)
//   }

//   def findBySlugWithinUser(userId: UUID, slug: String) = db.run {
//     findBySlugWithinUserCompiled(userId, slug).result.headOption
//   }

//   private val uniqueNameCompiled = Compiled {
//     (userId: Rep[UUID], name: Rep[String]) =>
//       table.filter { f =>
//         f.userId === userId && f.name === name
//       }.exists
//   }

//   private val uniqueSlugCompiled = Compiled {
//     (userId: Rep[UUID], slug: Rep[String]) =>
//       table.filter { f =>
//         f.userId === userId && f.slug === slug
//       }.exists
//   }

//   import Validations._

//   override def validate(c: comb.Comb)(implicit ec: ExecutionContext): ValidationDBIOResult = {
//     val plainValidations = validatePlain(
//       "name" -> List(lengthRange(c.name, 1, 255)),
//       "slug" -> List(lengthRange(c.slug, 1, 42))
//     )
//     val dbioValidations = validateDBIO(
//       "name" -> List(unique(uniqueNameCompiled(c.userId, c.name.toLowerCase))),
//       "slug" -> List(unique(uniqueSlugCompiled(c.userId, c.slug.toLowerCase)))
//     )
//     validate(plainValidations, dbioValidations)
//   }
// }
