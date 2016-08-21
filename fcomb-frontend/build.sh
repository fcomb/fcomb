#!/bin/sh

set -e

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd ${ROOT}

npm install --progress=false
npm run build
