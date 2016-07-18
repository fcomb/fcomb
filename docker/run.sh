#!/bin/sh

set -e

mkdir -p /data
chown -R fcomb:fcomb /data

su fcomb -c "${APP}/bin/start"
