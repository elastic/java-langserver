#!/usr/bin/env bash
set -e

if [[ -z "${AWS_ACCESS_KEY_ID}" ]]; then
    echo "AWS_ACCESS_KEY_ID is undefined"
    exit 1
elif [[ -z "${AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "AWS_SECRET_ACCESS_KEY is undefined"
    exit 1
fi
# run maven test first
./mvnw clean verify 

# deploy jdt language server to local 
./mvnw -DskipTests=true clean deploy -DaltDeploymentRepository=dev::default::file:./repository -B -e -Pserver-distro 

alias aws='docker run --rm -t $(tty &>/dev/null && echo "-i") -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" -e -v "$(pwd):/project" mesosphere/aws-cli'

RELEASE=$(find ./repository/org/elastic/jdt/ls/org.elastic.jdt.ls.product/ -type f -name "org.elastic.jdt.ls.product*.tar.gz")
if [ ${#RELEASE[@]} -eq 0 ]; then
    echo "cannot find any packages"
    exit 1
elif [ ${#RELEASE[@]} -gt 1 ]; then 
    echo "the actual number of packages is more than one"
    exit 1
else 
    echo "upload ${RELEASE[0]} to s3..."
    aws s3 cp ${RELEASE[0]} s3://download.elasticsearch.org/code/java-langserver/snapshot/
fi