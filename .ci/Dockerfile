FROM node:10.15.2

ARG KIBANA_VERSION

WORKDIR /plugin

RUN echo "===> Cloning Kibana v$KIBANA_VERSION" \
  && git clone --depth 1 -b master https://github.com/elastic/kibana.git

WORKDIR /plugin/kibana

RUN set -ex && \
wget -O get-pip.py 'https://bootstrap.pypa.io/get-pip.py' && \
python get-pip.py \
		--disable-pip-version-check \
		--no-cache-dir && \
rm -f get-pip.py && \
apt-get update && apt-get install -y openjdk-8-jdk jq make python-dev sudo && \
pip install --upgrade awscli==1.14.5 s3cmd==2.0.1 python-magic && \
update-java-alternatives --jre-headless --jre --set java-1.8.0-openjdk-amd64 && \
usermod -aG sudo node && \
echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

USER node

RUN sudo yarn kbn bootstrap

USER root

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64

VOLUME /root/.aws
VOLUME /root/.m2
VOLUME /plugin/kibana-extra/java-langserver
WORKDIR /plugin/kibana-extra/java-langserver