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

KIBANA_MOUNT_ARGUMENT=""
if [[ -n $KIBANA_MOUNT ]]; then
    if [[ -d $KIBANA_MOUNT ]]; then
        echo "KIBANA_MOUNT '$KIBANA_MOUNT' will be used as the kibana source for the build."
        ABSOLUTE_KIBANA_MOUNT=$(realpath "$KIBANA_MOUNT")
        KIBANA_MOUNT_ARGUMENT=-v\ "$ABSOLUTE_KIBANA_MOUNT:/plugin/kibana"
    else
        echo "KIBANA_MOUNT '$KIBANA_MOUNT' is not a directory, aborting."
        exit 1
    fi
fi

docker run \
    --rm -t $(tty &>/dev/null && echo "-i") \
    -v "$(pwd):/plugin/kibana-extra/java-langserver" \
    -v "$HOME/.m2":/root/.m2 \
    $KIBANA_MOUNT_ARGUMENT \
    code-lsp-java-langserver-ci \
    /bin/bash -c "set -x && \
                  $CMD
                  ./mvnw -DskipTests=true clean deploy -DaltDeploymentRepository=dev::default::file:./repository -B -e -Pserver-distro && \
                  ../../kibana/node_modules/git-hash-package/index.js && \
                  jq '.version=\"\\(.version)-linux\"' package.json > package-linux.json && \
                  jq '.version=\"\\(.version)-darwin\"' package.json > package-darwin.json && \
                  jq '.version=\"\\(.version)-windows\"' package.json > package-windows.json && \
                  mkdir packages
                  for PLATFORM in linux darwin windows
                  do
                      mv org.elastic.jdt.ls.product/distro/jdt-language-server*\$PLATFORM* lib
                      mv package-\$PLATFORM.json package.json
                      echo $KIBANA_VERSION | ../../kibana/packages/kbn-plugin-helpers/bin/plugin-helpers.js build
                      mv build/java-langserver*.zip packages
                      [ -e ./lib ] && rm -rf ./lib
                  done"

ls ./packages
