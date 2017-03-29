#!/bin/sh

set -e

ROOT="$( cd "$( dirname "$0" )" && pwd )"

cd ${ROOT}

yarn install --prod --no-emoji --force --no-progress
yarn run build
