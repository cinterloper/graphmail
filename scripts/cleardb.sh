rm db/kvdn-mapdb.db
rm -rf db/berkeleyje/*
rm -rf db/lucene/*
if [ -d ext/janus/janusgraph-0.2.0-hadoop2 ]
then
  rm -rf ext/janus/janusgraph-0.2.0-hadoop2/db/lucene/* ext/janus/janusgraph-0.2.0-hadoop2/db/berkeleyje/*
fi
