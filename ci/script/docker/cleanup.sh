#!/usr/bin/env bash

docker stop $(docker ps -a -q)
docker rm -v $(docker ps -a -q)
docker volume prune -f

[-e ./repository] && rm -rf ./repository

exit 0