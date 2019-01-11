ARG KIBANA_VERSION
FROM blacktop/kibana-plugin-builder:${KIBANA_VERSION}

USER root

RUN set -ex && \
wget -O get-pip.py 'https://bootstrap.pypa.io/get-pip.py' && \
python get-pip.py \
		--disable-pip-version-check \
		--no-cache-dir && \
rm -f get-pip.py && \
pip install --upgrade awscli==1.14.5 s3cmd==2.0.1 python-magic && \
apt-get update && apt-get install -y openjdk-8-jdk jq make && \
update-java-alternatives --jre-headless --jre --set java-1.8.0-openjdk-amd64

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64

VOLUME /root/.aws
VOLUME /root/.m2
VOLUME /plugin/kibana-extra/java-langserver
WORKDIR /plugin/kibana-extra/java-langserver