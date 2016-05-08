package io.fcomb.docker.distribution.server.api

import akka.actor.PoisonPill
import akka.http.scaladsl._, model._
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.{Image ⇒ PImage, ImageBlob ⇒ PImageBlob}
import io.fcomb.tests._
import io.fcomb.tests.fixtures.Fixtures
import io.fcomb.utils.StringUtils
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.Await
import scala.concurrent.duration._

class ImageServiceSpec extends WordSpec with Matchers with ScalatestRouteTest with SpecHelpers with ScalaFutures with PersistSpec with ActorClusterSpec {
  val route = Routes()
  val imageName = "library/test-image_2016"
  val bs = ByteString(getFixture("docker/distribution/blob"))
  val bsDigest = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(bs.toArray)
    StringUtils.hexify(md.digest)
  }
  // val digest = "300f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f"
  val credentials = BasicHttpCredentials(Fixtures.User.username, Fixtures.User.password)
  val clusterRef = ImageBlobPushProcessor.startRegion(30.seconds)

  override def afterAll(): Unit = {
    super.afterAll()
    clusterRef ! PoisonPill
  }

  "The image service" should {
    "" in {
      import scala.io.Source
      import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

      def golangPrinter(indent: String) = Printer(
        preserveOrder = true,
        dropNullKeys = false,
        indent = indent,
        lbraceRight = "\n",
        rbraceLeft = "\n",
        lbracketRight = "\n",
        rbracketLeft = "\n",
        lrbracketsEmpty = "\n",
        arrayCommaRight = "\n",
        objectCommaRight = "\n",
        colonLeft = "",
        colonRight = " "
      )

      val rawJson = Source.fromFile("/tmp/request2.json").mkString
      val json = parse(rawJson).toOption.get

      val decoder = implicitly[Decoder[ManifestV1]]
      val manifest = decoder.decodeJson(json).toOption.get
      println(manifest)

      val m = json.asObject.get
      val indent = rawJson.dropWhile(_ != ' ').takeWhile(_ == ' ')
      println(s"indent: ${indent.length}")
      val res = golangPrinter(indent).pretty(m.remove("signatures").asJson)

      val signature = manifest.signatures.get.head

      import org.jose4j.base64url.Base64Url
      import org.jose4j.jwk._
      import scala.collection.JavaConverters._
      val jwk = JsonWebKey.Factory.newJwk(signature.header.jwk.toMap[String, Object].asJava).asInstanceOf[EllipticCurveJsonWebKey]


      val base64url = new Base64Url()

      val payload = s"${signature.`protected`}.${base64url.base64UrlEncode(res.getBytes("utf-8"))}"

      import org.jose4j.jws._
      import org.jose4j.jca.ProviderContext

      val ctx = new ProviderContext()

      val alg = new EcdsaUsingShaAlgorithm.EcdsaP256UsingSha256()
      val signatureBytes = base64url.base64UrlDecode(signature.signature)
      assert(alg.verifySignature(signatureBytes, jwk.getPublicKey(), payload.getBytes("utf-8"), ctx))
    }

    // "return a uuid for POST request to the start upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(s"/v2/$imageName/blobs/uploads/") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.Accepted
    //     responseEntity shouldEqual HttpEntity.Empty
    //     val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get
    //     header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/uploads/$uuid")
    //     header[RangeCustom].get shouldEqual RangeCustom(0L, 0L)
    //   }
    // }

    // "return successful response for POST request to initiate monolithic blob upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(
    //     s"/v2/$imageName/blobs/uploads/?digest=sha256:$bsDigest",
    //     HttpEntity(`application/octet-stream`, bs)
    //   ) ~> addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.Created
    //       responseEntity shouldEqual HttpEntity.Empty
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    //       header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
    //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    //       val blob = Await.result(PImageBlob.findByPk(uuid), 5.seconds).get
    //       blob.length shouldEqual bs.length
    //       blob.state shouldEqual ImageBlobState.Uploaded
    //       blob.sha256Digest shouldEqual Some(bsDigest)

    //       val file = BlobFile.blobFile(blob.sha256Digest.get)
    //       file.length shouldEqual bs.length
    //       val fis = new FileInputStream(file)
    //       val fileDigest = DigestUtils.sha256Hex(fis)
    //       fileDigest shouldEqual bsDigest
    //     }
    // }

    // "return failed response for POST request to initiate monolithic blob upload path" in {
    //   Fixtures.await(Fixtures.User.create())

    //   Post(
    //     s"/v2/$imageName/blobs/uploads/?digest=sha256:333f96719cd9297b942f67578f7e7fe0a4472f9c68c30aff78db728316279e6f",
    //     HttpEntity(`application/octet-stream`, bs)
    //   ) ~> addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.BadRequest
    //       val resp = responseAs[DistributionErrorResponse]
    //       resp shouldEqual DistributionErrorResponse(Seq(DistributionError.DigestInvalid()))
    //     }
    // }

    // "return successful response for PUT request to mount blob upload path" in {
    //   val user = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.docker.distribution.ImageBlob.createAs(
    //       user.getId, imageName, bs, ImageBlobState.Uploaded
    //     )
    //   } yield user)
    //   val newImageName = "newimage/name"

    //   Post(
    //     s"/v2/$newImageName/blobs/uploads/?mount=sha256:$bsDigest&from=$imageName",
    //     HttpEntity(`application/octet-stream`, bs)
    //   ) ~> addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.Created
    //       responseEntity shouldEqual HttpEntity.Empty
    //       header[Location].get shouldEqual Location(s"/v2/$newImageName/blobs/sha256:$bsDigest")
    //       header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)

    //       val newBlob = Await.result({
    //         for {
    //           Some(image) ← PImage.findByImageAndUserId(newImageName, user.getId)
    //           Some(blob) ← PImageBlob.findByImageIdAndDigest(image.getId, bsDigest)
    //         } yield blob
    //       }, 5.seconds)
    //       newBlob.length shouldEqual bs.length
    //       newBlob.state shouldEqual ImageBlobState.Uploaded
    //       newBlob.sha256Digest shouldEqual Some(bsDigest)
    //     }
    // }

    // // "return an info without content for HEAD request to the exist layer path" in {
    // //   Fixtures.await(for {
    // //     user ← Fixtures.User.create()
    // //     _ ← Fixtures.docker.distribution.ImageBlob.createWithImage(
    // //       user.getId, imageName, digest, bs, bs.length
    // //     )
    // //   } yield ())

    // //   Head(s"/v2/$imageName/blobs/sha256:$digest") ~> route ~> check {
    // //     status shouldEqual StatusCodes.OK
    // //     responseAs[String] shouldEqual ""
    // //     header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", digest)
    // //     // header[`Content-Length`].get shouldEqual bs.length
    // //   }
    // // }

    // "return a blob for GET request to the blob download path" in {
    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     _ ← Fixtures.docker.distribution.ImageBlob.createAs(
    //       user.getId, imageName, bs, ImageBlobState.Uploaded
    //     )
    //   } yield ())

    //   Get(s"/v2/$imageName/blobs/sha256:$bsDigest") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     responseAs[ByteString] shouldEqual bs
    //     header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
    //     // header[`Content-Length`].get shouldEqual bs.length
    //   }
    // }

    // "return successful response for PUT request to monolithic blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.docker.distribution.ImageBlob.create(user.getId, imageName)
    //   } yield blob)

    //   Put(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
    //     HttpEntity(`application/octet-stream`, bs)
    //   ) ~> addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.Created
    //       responseEntity shouldEqual HttpEntity.Empty
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    //       header[`Docker-Content-Digest`].get shouldEqual `Docker-Content-Digest`("sha256", bsDigest)
    //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    //       val blob = Await.result(PImageBlob.findByPk(uuid), 5.seconds).get
    //       blob.length shouldEqual bs.length
    //       blob.state shouldEqual ImageBlobState.Uploaded
    //       blob.sha256Digest shouldEqual Some(bsDigest)

    //       val file = BlobFile.uploadFile(blob.getId)
    //       file.length shouldEqual bs.length
    //       val fis = new FileInputStream(file)
    //       val fileDigest = DigestUtils.sha256Hex(fis)
    //       fileDigest shouldEqual bsDigest
    //     }
    // }

    // "return successful response for PATCH requests to blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.docker.distribution.ImageBlob.create(user.getId, imageName)
    //   } yield blob)

    //   val blobPart1 = bs.take(bs.length / 2)
    //   val blobPart1Digest = DigestUtils.sha256Hex(blobPart1.toArray)

    //   Patch(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    //     HttpEntity(`application/octet-stream`, blobPart1)
    //   ) ~> `Content-Range`(ContentRange(0L, blobPart1.length - 1L)) ~>
    //     addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.Accepted
    //       responseEntity shouldEqual HttpEntity.Empty
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
    //       header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
    //       header[RangeCustom].get shouldEqual RangeCustom(0L, blobPart1.length - 1L)

    //       val updatedBlob = Await.result(PImageBlob.findByPk(blob.getId), 5.seconds).get
    //       updatedBlob.length shouldEqual blobPart1.length
    //       updatedBlob.state shouldEqual ImageBlobState.Uploading
    //       updatedBlob.sha256Digest shouldEqual Some(blobPart1Digest)

    //       val file = BlobFile.uploadFile(blob.getId)
    //       file.length shouldEqual blobPart1.length
    //       val fis = new FileInputStream(file)
    //       val fileDigest = DigestUtils.sha256Hex(fis)
    //       fileDigest shouldEqual blobPart1Digest
    //     }

    //   val blobPart2 = bs.drop(blobPart1.length)

    //   Patch(
    //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    //     HttpEntity(`application/octet-stream`, blobPart2)
    //   ) ~> `Content-Range`(ContentRange(blobPart1.length.toLong, blobPart2.length - 1L)) ~>
    //     addCredentials(credentials) ~> route ~> check {
    //       status shouldEqual StatusCodes.Accepted
    //       responseEntity shouldEqual HttpEntity.Empty
    //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
    //       header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
    //       header[RangeCustom].get shouldEqual RangeCustom(0L, bs.length - 1L)

    //       val updatedBlob = Await.result(PImageBlob.findByPk(blob.getId), 5.seconds).get
    //       updatedBlob.length shouldEqual bs.length
    //       updatedBlob.state shouldEqual ImageBlobState.Uploading
    //       updatedBlob.sha256Digest shouldEqual Some(bsDigest)

    //       val file = BlobFile.uploadFile(blob.getId)
    //       file.length shouldEqual bs.length
    //       val fis = new FileInputStream(file)
    //       val fileDigest = DigestUtils.sha256Hex(fis)
    //       fileDigest shouldEqual bsDigest
    //     }
    // }

    // // // TODO
    // // // "return successful response for first PATCH request to blob upload path" in {
    // // //   val blob = Fixtures.await(for {
    // // //     user ← Fixtures.User.create()
    // // //     blob ← Fixtures.DockerDistributionBlob.create(user.getId, imageName)
    // // //   } yield blob)
    // // //   val p = Promise[ByteString]()
    // // //   val bsSource = Source.single(bs).concat(Source.fromFuture(p.future))

    // // //   Patch(
    // // //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    // // //     HttpEntity(ContentTypes.`application/octet-stream`, bsSource)
    // // //   ) ~> `Content-Range`(ContentRange(0L, bs.length - 1L)) ~> route ~> check {
    // // //       status shouldEqual StatusCodes.Accepted
    // // //       responseAs[String] shouldEqual ""
    // // //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/${blob.getId}")
    // // //       header[`Docker-Upload-Uuid`].get shouldEqual `Docker-Upload-Uuid`(blob.getId)
    // // //       header[RangeCustom].get shouldEqual RangeCustom(0L, bs.length - 1)

    // // //       val updatedBlob = Await.result(PBlob.findByPk(blob.getId), 5.seconds).get
    // // //       updatedBlob.length shouldEqual bs.length
    // // //       updatedBlob.state shouldEqual BlobState.Uploading
    // // //       updatedBlob.sha256Digest shouldEqual Some(bsDigest)

    // // //       val file = BlobFile.uploadFile(blob.getId)
    // // //       file.length shouldEqual bs.length
    // // //       val fis = new FileInputStream(file)
    // // //       val fileDigest = DigestUtils.sha256Hex(fis)
    // // //       fileDigest shouldEqual bsDigest
    // // //     }

    // // //   val blobRandom = ByteString(Random.random.alphanumeric.take(1024).mkString)

    // // //   Patch(
    // // //     s"/v2/$imageName/blobs/uploads/${blob.getId}",
    // // //     HttpEntity(ContentTypes.`application/octet-stream`, blobRandom)
    // // //   ) ~> `Content-Range`(ContentRange(0L, blobRandom.length - 1L)) ~> route ~> check {
    // // //       p.complete(Try(ByteString.empty))
    // // //       status shouldEqual StatusCodes.BadRequest // TODO
    // // //       // responseAs[String] shouldEqual ""
    // // //     }
    // // // }

    // // "return successful response for PUT request without final chunk to complete blob upload path" in {
    // //   val blob = Fixtures.await(for {
    // //     user ← Fixtures.User.create()
    // //     blob ← Fixtures.docker.distribution.ImageBlob.createAs(
    // //       user.getId, imageName, bs, ImageBlobState.Uploading
    // //     )
    // //   } yield blob)

    // //   Put(
    // //     s"/v2/$imageName/blobs/uploads/${blob.getId}?digest=sha256:$bsDigest",
    // //     HttpEntity(`application/octet-stream`, bs)
    // //   ) ~> addCredentials(credentials) ~> route ~> check {
    // //       status shouldEqual StatusCodes.Accepted
    // //       responseAs[String] shouldEqual ""
    // //       header[Location].get shouldEqual Location(s"/v2/$imageName/blobs/sha256:$bsDigest")
    // //       val uuid = header[`Docker-Upload-Uuid`].map(h ⇒ UUID.fromString(h.value)).get

    // //       val blob = Await.result(PImageBlob.findByPk(uuid), 5.seconds).get
    // //       blob.length shouldEqual bs.length
    // //       blob.state shouldEqual ImageBlobState.Uploaded
    // //       blob.sha256Digest shouldEqual Some(bsDigest)
    // //     }
    // // }

    // "return successful response for DELETE request to the blob upload path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.docker.distribution.ImageBlob.createAs(
    //       user.getId, imageName, bs, ImageBlobState.Uploading
    //     )
    //   } yield blob)

    //   Delete(s"/v2/$imageName/blobs/uploads/${blob.getId}") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.NoContent
    //     responseEntity shouldEqual HttpEntity.Empty

    //     val file = BlobFile.uploadFile(blob.getId)
    //     file.exists() should be(false)
    //   }
    // }

    // "return successful response for DELETE request to the blob path" in {
    //   val blob = Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob ← Fixtures.docker.distribution.ImageBlob.createAs(
    //       user.getId, imageName, bs, ImageBlobState.Uploaded
    //     )
    //   } yield blob)

    //   Delete(s"/v2/$imageName/blobs/sha256:${blob.sha256Digest.get}") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.NoContent
    //     responseEntity shouldEqual HttpEntity.Empty

    //     val file = BlobFile.uploadFile(blob.getId)
    //     file.exists() should be(false)
    //   }
    // }

    // "return list of repositories for GET request to the catalog path" in {
    //   import ImageService.DistributionImageCatalog

    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "first/test1")
    //     _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "second/test2")
    //     _ ← Fixtures.docker.distribution.ImageBlob.create(user.getId, "third/test3")
    //   } yield ())

    //   Get(s"/v2/_catalog") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     val resp = responseAs[DistributionImageCatalog]
    //     resp shouldEqual DistributionImageCatalog(Seq("first/test1", "second/test2", "third/test3"))
    //   }

    //   Get(s"/v2/_catalog?n=2") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     val uri = Uri(s"/v2/_catalog?n=2&last=second/test2")
    //     header[Link].get shouldEqual Link(uri, LinkParams.next)
    //     val resp = responseAs[DistributionImageCatalog]
    //     resp shouldEqual DistributionImageCatalog(Seq("first/test1", "second/test2"))
    //   }

    //   Get(s"/v2/_catalog?n=2&last=second/test2") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     header[Link] shouldBe empty
    //     val resp = responseAs[DistributionImageCatalog]
    //     resp shouldEqual DistributionImageCatalog(Seq("third/test3"))
    //   }
    // }

    // "return list of tags for GET request to the tags path" in {
    //   import ImageService.ImageTagsResponse

    //   Fixtures.await(for {
    //     user ← Fixtures.User.create()
    //     blob1 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs, ImageBlobState.Uploaded)
    //     _ ← Fixtures.docker.distribution.ImageManifest.create(user.getId, imageName, blob1, List("1.0", "1.1"))
    //     blob2 ← Fixtures.docker.distribution.ImageBlob.createAs(user.getId, imageName, bs ++ bs, ImageBlobState.Uploaded)
    //     _ ← Fixtures.docker.distribution.ImageManifest.create(user.getId, imageName, blob2, List("2.0", "2.1"))
    //   } yield ())

    //   Get(s"/v2/$imageName/tags/list") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     val resp = responseAs[ImageTagsResponse]
    //     resp shouldEqual ImageTagsResponse(imageName, Vector("1.0", "1.1", "2.0", "2.1"))
    //   }

    //   Get(s"/v2/$imageName/tags/list?n=2") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     val uri = Uri(s"/v2/$imageName/tags/list?n=2&last=1.1")
    //     header[Link].get shouldEqual Link(uri, LinkParams.next)
    //     val resp = responseAs[ImageTagsResponse]
    //     resp shouldEqual ImageTagsResponse(imageName, Vector("1.0", "1.1"))
    //   }

    //   Get(s"/v2/$imageName/tags/list?n=3&last=1.0") ~> addCredentials(credentials) ~> route ~> check {
    //     status shouldEqual StatusCodes.OK
    //     header[Link] shouldBe empty
    //     val resp = responseAs[ImageTagsResponse]
    //     resp shouldEqual ImageTagsResponse(imageName, Vector("1.1", "2.0", "2.1"))
    //   }
    // }
  }
}
