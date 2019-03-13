#!/usr/bin/env bash
set -e

if [[ -z "${AWS_ACCESS_KEY_ID}" ]]; then
    echo "AWS_ACCESS_KEY_ID is undefined"
    exit 1
elif [[ -z "${AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "AWS_SECRET_ACCESS_KEY is undefined"
    exit 1
fi

docker run \
    --rm -t $(tty &>/dev/null && echo "-i") \
    -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" \
    -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" \
    -v "$(pwd):/plugin/kibana-extra/java-langserver" \
    code-lsp-java-langserver-ci \
    /bin/bash -c "set -x && \
                  for filename in packages/java-langserver-*.zip; do
                    if [[ \$filename == *\"SNAPSHOT\"* ]]; then
                        aws s3 cp \$filename s3://download.elasticsearch.org/code/java-langserver/snapshot/
                    else 
                        aws s3 cp \$filename s3://download.elasticsearch.org/code/java-langserver/release/
                    fi
                  done"
