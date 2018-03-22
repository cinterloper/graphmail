set -e
source init_project.sh
source scripts/cmds.sh
startJanusBackground
set +e
sleep 10
runApp
