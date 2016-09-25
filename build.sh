#!/bin/bash

set -e

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION="$( cd "$ROOT" && git name-rev --tags --name-only $(git rev-parse HEAD) )"
IMAGE="fcomb/fcomb:${VERSION}"

docker build -t ${IMAGE} ${ROOT}

if [ -n "${PUBLISH}" ];then
  docker tag ${IMAGE} "fcomb/fcomb:latest"
  docker push ${IMAGE}
  docker push "fcomb/fcomb:latest"
fi
