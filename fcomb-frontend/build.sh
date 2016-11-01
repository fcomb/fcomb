#!/bin/sh

set -e

ROOT="$( cd "$( dirname "$0" )" && pwd )"

cd ${ROOT}

npm install --progress=false yarn
yarn install
yarn run build
