#!/usr/bin/env bash
set -e

if [ $# -eq 0 ]; then 
    echo "deploy snapshot package.."
    KIBANA_VERSION=8.0.0
    DESTINATION=snapshot/
    CMD="./mvnw clean verify -B -e && \\"
elif [ $# -eq 2 ]; then
    echo "deploy release package.."
    echo "plugin version: $1"
    echo "Kibana version: $2"
    VERSION=$1
    KIBANA_VERSION=$2
    DESTINATION=release/
    CMD="./mvnw -Dtycho.mode=maven -DnewVersion=$VERSION -D-Pserver-distro org.eclipse.tycho:tycho-versions-plugin:set-version && \
         jq '.version=\"$VERSION\"' package.json > tmp && mv tmp package.json && \\"
else
    echo "Wrong number of parameters!"
    exit 2
fi

docker build --rm -f ".ci/Dockerfile" --build-arg KIBANA_VERSION=$KIBANA_VERSION -t code-lsp-java-langserver-ci:latest .ci

docker run \
    --rm -t $(tty &>/dev/null && echo "-i") \
    -v "$(pwd):/plugin/kibana-extra/java-langserver" \
    -v "$HOME/.m2":/root/.m2 \
    code-lsp-java-langserver-ci \
    /bin/bash -c "set -x && \
                  chown -R node:node /plugin && \
                  su node && \
                  yarn kbn bootstrap && \
                  jq '.version=\"\\(.version)-linux\"' package.json > package-linux.json && \
                  jq '.version=\"\\(.version)-darwin\"' package.json > package-darwin.json && \
                  jq '.version=\"\\(.version)-windows\"' package.json > package-windows.json && \
                  mkdir packages && \
                  for PLATFORM in linux darwin windows
                  do 
                      mv org.elastic.jdt.ls.product/distro/jdt-language-server*\$PLATFORM* lib
                      mv package-\$PLATFORM.json package.json
                      echo $KIBANA_VERSION | yarn build
                      mv build/java-langserver*.zip packages
                      [ -e ./lib ] && rm -rf ./lib
                  done"

 ls ./packages