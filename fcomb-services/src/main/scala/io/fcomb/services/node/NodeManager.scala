package io.fcomb.services.node

import io.fcomb.request.NodeJoinRequest
import io.fcomb.models.node.{Node ⇒ MNode}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.crypto.Certificate
import io.fcomb.validations.ValidationResultUnit
import scala.concurrent.{ExecutionContext, Future}
import sun.security.pkcs10.PKCS10
import java.util.Base64
import java.net.InetAddress
import scalaz._, Scalaz._

object NodeManager {
  def joinByRequest(userId: Long, req: NodeJoinRequest)(
    implicit
    ec: ExecutionContext
  ): Future[PNode.ValidationModel] = {
    val rs = req.certificationRequest.trim
    if ((rs.startsWith(beginRequest) || rs.startsWith(beginNewRequest))) {
      val body = rs.substring(rs.indexOf('\n'), rs.lastIndexOf('\n'))
      val csr = new PKCS10(Base64.getMimeDecoder().decode(body))
      NodeJoinProcessor.join(userId, csr).map(_.success)
    }
    else Future.successful(unknownHeaderError)
  }

  private val beginRequest = "-----BEGIN CERTIFICATE REQUEST-----"
  private val beginNewRequest = "-----BEGIN NEW CERTIFICATE REQUEST-----"
  private val unknownHeaderError = PNode.validationError(
    "certificationRequest",
    s"Unknown format: `$beginRequest` or `$beginNewRequest` prefix is not found"
  )

  def register(nodeId: Long, ipAddress: InetAddress)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationResultUnit] =
    NodeProcessor.register(nodeId, ipAddress).map(_.success)
}
