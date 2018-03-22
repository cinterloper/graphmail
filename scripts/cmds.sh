checkReady(){
  docker logs graphmail | grep "listeners setup"
  if [ $? -eq 0 ]
  then
    echo service is ready
  else
    echo service is still starting or has failed, check graphmail container logs
  fi
}
recalcDegDist(){
  curl http://localhost:6502/degDist | jq .
}
importMaildir(){
  python scripts/python/graphmail_tool/mailtool.py --import_maildir $1
}
plotDegDist(){
  recalcDegDist
  python scripts/python/graphmail_tool/mailtool.py --plot
}
runApp(){
  java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory  -jar build/libs/*-fat.jar -conf $( cat conf/app-config.json | jq -c .) 2>&1
}
startJanusBackground(){
  ext/janus/janusgraph-0.2.0-hadoop2/bin/gremlin-server.sh &
  export JANUS_ROOT_PID=$$
}
echo "Interactive commands: importMaildir plotDegDist recalcDegDist"
