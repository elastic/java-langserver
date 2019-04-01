FROM node:10.15.2

ARG KIBANA_VERSION

WORKDIR /home/node/plugin

RUN echo "===> Cloning Kibana v$KIBANA_VERSION" \
  && git clone --depth 1 -b master https://github.com/elastic/kibana.git

WORKDIR /home/node/plugin/kibana

RUN set -ex && \
wget -O get-pip.py 'https://bootstrap.pypa.io/get-pip.py' && \
python get-pip.py \
		--disable-pip-version-check \
		--no-cache-dir && \
rm -f get-pip.py && \
apt-get update && apt-get install -y openjdk-8-jdk jq make python-dev && \
pip install --upgrade awscli==1.14.5 s3cmd==2.0.1 python-magic && \
update-java-alternatives --jre-headless --jre --set java-1.8.0-openjdk-amd64

USER node

RUN yarn kbn bootstrap

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64

VOLUME /root/.aws
VOLUME /root/.m2
VOLUME /home/node/plugin/kibana-extra/java-langserver
WORKDIR /home/node/plugin/kibana-extra/java-langserver