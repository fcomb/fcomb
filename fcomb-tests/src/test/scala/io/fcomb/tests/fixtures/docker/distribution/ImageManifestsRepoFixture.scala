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

package io.fcomb.tests.fixtures.docker.distribution

import cats.data.{Validated, Xor}
import io.fcomb.models.docker.distribution._
import io.fcomb.persist.docker.distribution._
import io.fcomb.Db.db
import io.fcomb.docker.distribution.manifest.{SchemaV1 ⇒ SchemaV1Manifest}
import io.fcomb.json.docker.distribution.Formats._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.parser._
import io.circe.generic.auto._
import org.apache.commons.codec.digest.DigestUtils

object ImageManifestsRepoFixture {
  def createV1(
    userId:    Long,
    imageName: String,
    blob:      ImageBlob,
    tag:       String
  ): Future[ImageManifest] = {
    val schemaV1JsonBlob = getSchemaV1JsonBlob(imageName, tag, blob)
    val sha256Digest = DigestUtils.sha256Hex(schemaV1JsonBlob)
    val manifest = SchemaV1.Manifest(
      name = imageName,
      tag = tag,
      fsLayers = List(SchemaV1.FsLayer(s"sha256:${blob.sha256Digest.get}")),
      architecture = "amd64",
      history = Nil,
      signatures = Nil
    )
    for {
      Some(image) ← ImagesRepo.findByPk(blob.imageId)
      Validated.Valid(im) ← ImageManifestsRepo.upsertSchemaV1(
        image = image,
        manifest = manifest,
        schemaV1JsonBlob = schemaV1JsonBlob,
        sha256Digest = sha256Digest
      )
    } yield im
  }

  private def getSchemaV1JsonBlob(imageName: String, tag: String, blob: ImageBlob) = {
    val Xor.Right(jsonBlob) = parse(s"""
    {
      "name": "$imageName",
      "tag": "$tag",
      "fsLayers": [
        {"blobSum": "sha256:${blob.sha256Digest.get}"}
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
    userId:    Long,
    imageName: String,
    blob:      ImageBlob,
    tags:      List[String]
  ): Future[ImageManifest] = {
    val schemaV1JsonBlob = getSchemaV1JsonBlob(imageName, tags.headOption.getOrElse(""), blob)
    val schemaV2JsonBlob = s"""
    {
      "schemaVersion": 2,
      "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
      "config": {
        "mediaType": "application/vnd.docker.container.image.v1+json",
        "size": ${blob.length},
        "digest": "sha256:${blob.sha256Digest.get}"
      },
      "layers": [
        {
          "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
          "size": ${blob.length},
          "digest": "sha256:${blob.sha256Digest.get}"
        }
      ]
    }
    """
    val Xor.Right(manifest) = decode[SchemaV2.Manifest](schemaV2JsonBlob)
    val sha256Digest = DigestUtils.sha256Hex(schemaV2JsonBlob)
    val reference = Reference.Digest(sha256Digest)
    for {
      Some(image) ← ImagesRepo.findByPk(blob.imageId)
      Validated.Valid(im) ← ImageManifestsRepo.upsertSchemaV2(
        image = image,
        manifest = manifest,
        reference = reference,
        configBlob = blob,
        schemaV1JsonBlob = schemaV1JsonBlob,
        schemaV2JsonBlob = schemaV2JsonBlob,
        sha256Digest = sha256Digest
      )
      _ ← db.run(for {
        _ ← ImageManifestTagsRepo.upsertTagsDBIO(im.imageId, im.getId, tags)
        _ ← ImageManifestsRepo.updateDBIO(im.copy(tags = tags))
      } yield ())
    } yield im
  }
}
