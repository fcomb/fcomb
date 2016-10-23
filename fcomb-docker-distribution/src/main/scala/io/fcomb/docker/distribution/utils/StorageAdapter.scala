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

package io.fcomb.docker.distribution.utils

import java.io.File

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.{ByteString, ByteStringBuilder}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.mfglabs.commons.aws.s3._
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.models.common.{Enum, EnumItem}
import io.fcomb.utils.Config

import scala.concurrent._

sealed trait StorageAdapter extends EnumItem {
  def sink(file: File)(implicit ec: ExecutionContext): Sink[ByteString, Future[Any]]

  def source(file: File)(implicit ec: ExecutionContext): Source[ByteString, Any]

  def rename(file: File, newFile: File)(implicit ec: ExecutionContext): Any

  def sourcePart(file: File, offset: Long, limit: Long)(
      implicit ec: ExecutionContext): Source[ByteString, Any] =
    source(file).via(new Chunker(1)).drop(offset).take(limit)

  def destroy(file: File)(implicit ec: ExecutionContext): Future[Any]
}

object StorageAdapter extends Enum[StorageAdapter] {
  case object LocalStorage extends StorageAdapter with LocalStorageImpl

  case object AmazonStorage extends StorageAdapter with AmazonStorageImpl

  val values = findValues

  def getInstance() = Config.storageAdapter match {
    case "local"  => LocalStorage
    case "amazon" => AmazonStorage
  }
}

trait LocalStorageImpl extends LazyLogging {
  def sink(file: File)(implicit ec: ExecutionContext) =
    FileIO.toPath(file.toPath)

  def source(file: File)(implicit ec: ExecutionContext) =
    FileIO.fromPath(file.toPath)

  def destroy(file: File)(implicit ec: ExecutionContext) =
    Future.successful(file.delete())

  def rename(file: File, newFile: File)(implicit ec: ExecutionContext) =
    if (!newFile.exists()) {
      if (!newFile.getParentFile.exists()) newFile.getParentFile.mkdirs()
      file.renameTo(newFile)
    }
}

trait AmazonStorageImpl extends LazyLogging {
  private val chunkPerFile: Int      = 10000
  private val uploadConcurrency: Int = 4

  private lazy val streamBuilder = S3StreamBuilder(
    new AmazonS3AsyncClient(
      new BasicAWSCredentials(Config.s3.accessKeyId, Config.s3.secretAccessKey)
    )
  )

  streamBuilder.client.client.setRegion(Region.getRegion(Regions.fromName(Config.s3.region)))

  private implicit def fileToPathString(file: File): String = file.getPath.substring(1)

  def sink(file: File)(implicit ec: ExecutionContext) =
    streamBuilder
      .uploadStreamAsMultipartFile(
        Config.s3.bucketName,
        file,
        nbChunkPerFile = chunkPerFile,
        chunkUploadConcurrency = uploadConcurrency
      )
      .toMat(Sink.ignore)(Keep.right)
      .mapMaterializedValue(identity)

  def source(file: File)(implicit ec: ExecutionContext) =
    streamBuilder.getMultipartFileAsStream(Config.s3.bucketName, file)

  def destroy(file: File)(implicit ec: ExecutionContext) =
    streamBuilder.client.deleteFile(Config.s3.bucketName, file)

  def rename(file: File, newFile: File)(implicit ec: ExecutionContext) =
    streamBuilder.client
      .copyObject(Config.s3.bucketName, file, Config.s3.bucketName, newFile)
      .flatMap(res => destroy(file))
}

class Chunker(val chunkSize: Int) extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in             = Inlet[ByteString]("Chunker.in")
  val out            = Outlet[ByteString]("Chunker.out")
  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      private val buffer = new ByteStringBuilder

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          buffer ++= grab(in)
          if (buffer.length < chunkSize) pull(in)
          else {
            val (emit, rest) = buffer.result.splitAt(chunkSize)
            buffer.clear()
            buffer ++= rest
            push(out, emit)
          }
        }

        override def onUpstreamFinish() = {
          if (buffer.nonEmpty) emitMultiple(out, buffer.result.grouped(chunkSize))
          completeStage()
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pull(in)
      })
    }
}
