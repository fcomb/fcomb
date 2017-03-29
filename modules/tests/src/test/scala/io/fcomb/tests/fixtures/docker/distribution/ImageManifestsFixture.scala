/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.tests.fixtures.docker.distribution

import cats.data.Validated
import io.circe.jawn._
import io.fcomb.docker.distribution.manifest.{SchemaV1 => SchemaV1Manifest}
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution._
import io.fcomb.persist.docker.distribution._
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ImageManifestsFixture {
  def createV1(
      userId: Int,
      imageSlug: String,
      blob: ImageBlob,
      tag: String
  ): Future[ImageManifest] = {
    val schemaV1JsonBlob = getSchemaV1JsonBlob(imageSlug, tag, blob)
    val digest           = DigestUtils.sha256Hex(schemaV1JsonBlob)
    val manifest = SchemaV1.Manifest(
      name = imageSlug,
      tag = tag,
      fsLayers = List(SchemaV1.FsLayer(s"sha256:${blob.digest.get}")),
      architecture = "amd64",
      history = Nil,
      signatures = Nil
    )
    for {
      Some(image) <- ImagesRepo.findById(blob.imageId)
      Validated.Valid(im) <- ImageManifestsRepo.upsertSchemaV1(
        image = image,
        manifest = manifest,
        schemaV1JsonBlob = schemaV1JsonBlob,
        digest = digest
      )
    } yield im
  }

  private def getSchemaV1JsonBlob(imageSlug: String, tag: String, blob: ImageBlob) = {
    val Right(jsonBlob) = parse(s"""
    {
      "name": "$imageSlug",
      "tag": "$tag",
      "fsLayers": [
        {"blobSum": "sha256:${blob.digest.get}"}
      ],
      "architecture": "amd64",
      "history": [
        {"v1Compatibility": "{\\"architecture\\":\\"amd64\\",\\"config\\":{\\"Hostname\\":\\"27c9668b3d5e\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":null,\\"Cmd\\":null,\\"Image\\":\\"\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":null},\\"container\\":\\"27c9668b3d5e3a2abeefdb725e1ff739cedda4b19eff906336298608f635b00e\\",\\"container_config\\":{\\"Hostname\\":\\"27c9668b3d5e\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":null,\\"Cmd\\":[\\"/bin/sh\\",\\"-c\\",\\"#(nop) ADD file:614a9122187935fccfa72039b9efa3ddbf371f6b029bb01e2073325f00c80b9f in /\\"],\\"Image\\":\\"\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":null},\\"created\\":\\"2016-05-06T14:56:49.723208146Z\\",\\"docker_version\\":\\"1.9.1\\",\\"history\\":[{\\"created\\":\\"2016-05-06T14:56:49.723208146Z\\",\\"created_by\\":\\"/bin/sh -c #(nop) ADD file:614a9122187935fccfa72039b9efa3ddbf371f6b029bb01e2073325f00c80b9f in /\\"}],\\"os\\":\\"linux\\",\\"rootfs\\":{\\"type\\":\\"layers\\",\\"diff_ids\\":[\\"sha256:8f01a53880b9b96424f0034d75102ed915cc2125d887c3b186a8122be08c09c0\\"]}}"}
      ],
      "schemaVersion": 1
    }
    """)
    SchemaV1Manifest.prettyPrint(jsonBlob)
  }

  def createV2(
      userId: Int,
      imageSlug: String,
      blob: ImageBlob,
      tags: List[String]
  ): DBIO[ImageManifest] = {
    val schemaV1JsonBlob = getSchemaV1JsonBlob(imageSlug, tags.headOption.getOrElse(""), blob)
    val schemaV2JsonBlob = s"""
    {
      "schemaVersion": 2,
      "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
      "config": {
        "mediaType": "application/vnd.docker.container.image.v1+json",
        "size": ${blob.length},
        "digest": "sha256:${blob.digest.get}"
      },
      "layers": [
        {
          "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
          "size": ${blob.length},
          "digest": "sha256:${blob.digest.get}"
        }
      ]
    }
    """
    val Right(manifest)  = decode[SchemaV2.Manifest](schemaV2JsonBlob)
    val digest           = DigestUtils.sha256Hex(schemaV2JsonBlob)
    val reference        = Reference.Digest(digest)
    for {
      Some(image) <- ImagesRepo.findById(blob.imageId)
      Validated.Valid(im) <- ImageManifestsRepo.upsertSchemaV2(
        image = image,
        manifest = manifest,
        reference = reference,
        configBlob = blob,
        schemaV1JsonBlob = schemaV1JsonBlob,
        schemaV2JsonBlob = schemaV2JsonBlob,
        digest = digest
      )
      _ <- for {
        _ <- ImageManifestTagsRepo.upsertTagsDBIO(im.imageId, im.getId(), tags)
        _ <- ImageManifestsRepo.updateDBIO(im.copy(tags = tags))
      } yield ()
    } yield im
  }
}
