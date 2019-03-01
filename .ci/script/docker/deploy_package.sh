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

if [[ -z "${AWS_ACCESS_KEY_ID}" ]]; then
    echo "AWS_ACCESS_KEY_ID is undefined"
    exit 1
elif [[ -z "${AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "AWS_SECRET_ACCESS_KEY is undefined"
    exit 1
fi

# alias aws='docker run --rm -t $(tty &>/dev/null && echo "-i") -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" -e "AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION}" -v "$(pwd):/project" mesosphere/aws-cli'

touch test.sh

pip install awscli --user

/var/lib/jenkins/.local/bin/aws s3 cp test.sh s3://download.elasticsearch.org/code/java-langserver/$DESTINATION
                  