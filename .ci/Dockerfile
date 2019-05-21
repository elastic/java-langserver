FROM node:10.15.2

ARG KIBANA_VERSION

USER root

RUN set -ex && \
wget -O get-pip.py 'https://bootstrap.pypa.io/get-pip.py' && \
python get-pip.py \
		--disable-pip-version-check \
		--no-cache-dir && \
rm -f get-pip.py && \
apt-get update && apt-get install -y openjdk-8-jdk jq make python-dev && \
pip --no-cache-dir install --upgrade awscli==1.14.5 s3cmd==2.0.1 python-magic && \
update-java-alternatives --jre-headless --jre --set java-1.8.0-openjdk-amd64 && \
apt-get -qq -y autoremove && \
apt-get -qq -y clean && \
rm -rf /var/lib/apt/lists/*

####
# Build CI User
###
ARG CI_USER_UID=13000
ENV CI_USER ciagent
ENV CI_GROUP ciagent
ENV HOME /home/${CI_USER}

# - Create user and group with specific ids
# - Create needed directories
RUN useradd --create-home --user-group -u ${CI_USER_UID} --shell /bin/bash ${CI_USER} \
   && mkdir -p ${HOME}/.aws  \
   && mkdir -p ${HOME}/.m2 \
   && chown -R ${CI_USER}:${CI_GROUP} ${HOME}

USER ${CI_USER}

ENV JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64

VOLUME /home/ciagent/.aws
VOLUME /home/ciagent/.m2
VOLUME /plugin/kibana
VOLUME /plugin/kibana-extra/java-langserver
WORKDIR /plugin/kibana-extra/java-langserver
