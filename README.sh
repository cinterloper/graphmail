#!/usr/bin/env bash
#You can execute this readme
#This service allows you to create a communications graph from your maildir
#On port 8182, you can connect to gremlin-server with gremlin shell
# to explore the graph.
#On http port 6502 you can retrieve your mail parsed into json format
# through kvdn-cli (kvdnc on pip)
#At http://localhost:6502/degDist is a service that will calculate the
# degree distribution of your communications graph
#In scripts/python there is a tool that will import your maildir,
# and draw a plot of the degree distribution
#You should review scripts/cmds.sh to see how this is used

set -e
virtualenv venv
source venv/bin/activate
pip install -r scripts/python/requirements.txt
./gradlew clean shadowJar
docker build -t graphmail .
docker run -d --name graphmail -t -i -p 6502:6502 -p 8182:8182 graphmail
set +e
echo "you can now use the mailtool in scripts/python/graphmail_tool to import a maildir, then graph the degree distribution"
source scripts/cmds.sh
