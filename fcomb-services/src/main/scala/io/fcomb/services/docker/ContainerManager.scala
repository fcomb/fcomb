package io.fcomb.services.docker

import io.fcomb.models.application.{Application ⇒ MApplication}
import io.fcomb.models.docker.{Container ⇒ MContainer}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.services.node.NodeProcessor
import scala.concurrent.{ExecutionContext, Future}
import scalaz._, Scalaz._

object ContainerManager {
  def create(app: MApplication)(
    implicit
    ec: ExecutionContext
  ): Future[PContainer.ValidationModel] = {
    // TODO: ask UserNodeProcessor
    PNode.findAllAvailableByUserId(app.userId).flatMap { nodes ⇒
      println(s"nodes: $nodes")
      val node = nodes.head
      NodeProcessor.createContainer(node.getId, app)
    }
  }
}
