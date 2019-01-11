#!/usr/bin/env bash

docker stop $(docker ps -a -q)
docker rm -v $(docker ps -a -q)
docker volume prune -f

[ -e ./repository ] && rm -rf ./repository
[ -e ./build ] && rm -rf ./build
[ -e ./lib ] && rm -rf ./lib

exit 0