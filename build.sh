#!/bin/bash

set -e

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VERSION="$( cd "$ROOT" && git tag )"
IMAGE="fcomb/fcomb:v${VERSION}"

docker build -t ${IMAGE} ${ROOT}

if [ -n "${PUBLISH}" ];then
  docker tag ${IMAGE} "fcomb/fcomb:latest"
  docker push ${IMAGE}
  docker push "fcomb/fcomb:latest"
fi
