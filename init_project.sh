#!/usr/bin/env bash
set -e
set -x
wget $(cat janus.url ) -O ext/janus.zip
unzip ext/janus.zip -d ext/janus
cp conf/gremlin-server.yaml ext/janus/janusgraph-0.2.0-hadoop2/conf/gremlin-server/gremlin-server.yaml
pwd
set +x
set +e
