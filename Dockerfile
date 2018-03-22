FROM java:8
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update
RUN apt-get -y install python2.7
RUN cd /tmp && curl -s https://bootstrap.pypa.io/get-pip.py > get-pip.py && python get-pip.py
RUN apt-get -y install gcc libpython2.7-dev jq wget
ADD . /opt/graphmail
WORKDIR /opt/graphmail
ENTRYPOINT bash -c 'source init.sh'
