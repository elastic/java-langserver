#!/usr/bin/env bash
set -e

if [ $# -eq 0 ]; then
    echo "deploy snapshot package.."
    KIBANA_VERSION=8.0.0
    DESTINATION=snapshot/
    CMD="./mvnw clean verify -B -e"
elif [ $# -eq 2 ]; then
    echo "deploy release package.."
    echo "plugin version: $1"
    echo "Kibana version: $2"
    VERSION=$1
    KIBANA_VERSION=$2
    DESTINATION=release/
    CMD="./mvnw -Dtycho.mode=maven -DnewVersion=$VERSION -D-Pserver-distro org.eclipse.tycho:tycho-versions-plugin:set-version && \
         jq '.version=\"$VERSION\"' package.json > tmp && mv tmp package.json"
else
    echo "Wrong number of parameters!"
    exit 2
fi

docker build --rm -f ".ci/Dockerfile" --build-arg CI_USER_UID=$(id -u) --build-arg KIBANA_VERSION=$KIBANA_VERSION -t code-lsp-java-langserver-ci:latest .ci

KIBANA_MOUNT_ARGUMENT=""
if [[ -n $KIBANA_MOUNT ]]; then
    if [[ -d $KIBANA_MOUNT ]]; then
        echo "KIBANA_MOUNT '$KIBANA_MOUNT' will be used as the kibana source for the build."
    else
        echo "KIBANA_MOUNT '$KIBANA_MOUNT' is not a directory, aborting."
        exit 1
    fi
else
  # if the Kibana source repo is not set as KIBANA_MOUNT, we clone the repo
  echo "===> Cloning Kibana v$KIBANA_VERSION"
  git clone --depth 1 -b master https://github.com/elastic/kibana.git "$(pwd)/kibana"
  KIBANA_MOUNT="$(pwd)/kibana"
fi


ABSOLUTE_KIBANA_MOUNT=$(realpath "$KIBANA_MOUNT")
KIBANA_MOUNT_ARGUMENT=-v\ "$ABSOLUTE_KIBANA_MOUNT:/plugin/kibana:rw"

docker run \
    --rm -t $(tty &>/dev/null && echo "-i") \
    --user $(id -u):ciagent \
    -v "$(pwd):/plugin/kibana-extra/java-langserver:rw" \
    -v "$HOME/.m2":/home/ciagent/.m2 \
    $KIBANA_MOUNT_ARGUMENT \
    code-lsp-java-langserver-ci \
    /bin/bash -c "set -ex

                  # if the kibana repo is mounted from disk run the yarn
                  # commands as the node user to prepare it for the build
                  if test -n '$KIBANA_MOUNT_ARGUMENT'; then
                    (
                      cd /plugin/kibana
                      yarn kbn bootstrap
                      yarn add git-hash-package
                    )
                  fi
                  # fail fast if required kibana files are missing
                  for file in /plugin/kibana/node_modules/git-hash-package/index.js /plugin/kibana/packages/kbn-plugin-helpers/bin/plugin-helpers.js; do
                    if ! test -f \$file; then
                      echo \"Missing required \$file, aborting.\"
                      exit 1
                    fi
                  done

                  $CMD
                  ./mvnw -DskipTests=true clean deploy -DaltDeploymentRepository=dev::default::file:./repository -B -e -Pserver-distro
                  /plugin/kibana/node_modules/git-hash-package/index.js
                  jq '.version=\"\\(.version)-linux\"' package.json > package-linux.json
                  jq '.version=\"\\(.version)-darwin\"' package.json > package-darwin.json
                  jq '.version=\"\\(.version)-windows\"' package.json > package-windows.json
                  mkdir packages
                  for PLATFORM in linux darwin windows
                  do
                      mv org.elastic.jdt.ls.product/distro/jdt-language-server*\$PLATFORM* lib
                      mv package-\$PLATFORM.json package.json
                      echo $KIBANA_VERSION | /plugin/kibana/packages/kbn-plugin-helpers/bin/plugin-helpers.js build
                      mv build/java-langserver*.zip packages
                      [ -e ./lib ] && rm -rf ./lib
                  done"

ls ./packages
