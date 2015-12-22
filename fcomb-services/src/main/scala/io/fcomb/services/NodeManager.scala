package io.fcomb.services

import io.fcomb.request.NodeJoinRequest
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.crypto.Certificate
import scala.concurrent.{ExecutionContext, Future}
import sun.security.pkcs10.PKCS10
import sun.security.x509._
import java.util.Base64

object NodeManager {
  val beginRequest = "-----BEGIN CERTIFICATE REQUEST-----\n"
  val beginNewRequest = "-----BEGIN NEW CERTIFICATE REQUEST-----\n"

  def joinByRequest(userId: Long, req: NodeJoinRequest)(
    implicit
    ec: ExecutionContext
  ): Future[PNode.ValidationModel] = {
    println(s"req: $req")
    val rs = req.certificationRequest.trim
    if ((rs.startsWith(beginRequest) || rs.startsWith(beginNewRequest))) {
      val body = rs.substring(rs.indexOf('\n'), rs.lastIndexOf('\n'))
      val csr = new PKCS10(Base64.getMimeDecoder().decode(body))
      NodeJoinProcessor.join(userId, csr).onComplete(println)
      ???
      // CertificateProcessor.generateUserCertificates(userId).flatMap {
      //   case (rootCert, rootKey) =>
      //     val signed = Certificate.signCertificationRequest(
      //       signerCert = rootCert,
      //       signerPrivateKey = rootKey,
      //       request = csr,
      //       name = nodeName
      //     )
      //     println(s"signed: $signed")
      //     PNode.create(???)
      // }
    } else ??? // TODO: return error
  }
}
