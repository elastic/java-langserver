#!/usr/bin/env bash
set -e

if [[ -z "${AWS_ACCESS_KEY_ID}" ]]; then
    echo "AWS_ACCESS_KEY_ID is undefined"
    exit 1
elif [[ -z "${AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "AWS_SECRET_ACCESS_KEY is undefined"
    exit 1
fi

docker build --rm -f "ci/Dockerfile" -t code-lsp-java-langserver-ci:latest ci

docker run \
    --rm -t $(tty &>/dev/null && echo "-i") \
    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" \
    -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" \
    -v "$(pwd):/project" \
    -v "$HOME/.m2":/root/.m2 \
    code-lsp-java-langserver-ci \
    /bin/bash -c "mvn clean verify -B -e && \
                  mvn -DskipTests=true clean deploy -DaltDeploymentRepository=dev::default::file:./repository -B -e -Pserver-distro && \
                  find ./repository/org/elastic/jdt/ls/org.elastic.jdt.ls.product/ -type f -name \"org.elastic.jdt.ls.product*.tar.gz\" -print0 | xargs -0 -I{} aws s3 cp {} s3://download.elasticsearch.org/code/java-langserver/snapshot/"