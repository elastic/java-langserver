#!/usr/bin/env bash

#! usage: ./scripts/bump_version.sh ${new_version}
set -e

if [ $# -eq 1 ]; then
    echo "#################################################"
    echo "Bump version for Java language server to $1..."
    echo "#################################################"
else 
    echo 'Please enter the new version, usage: ./scripts/bump_version.sh ${new_version}'
    exit 1
fi

if python -c "import bumpversion" &> /dev/null; then
    :
else
    echo 'Install bumpversion...'
    pip install --user bumpversion
    echo 'bumpversion has been installed'
fi

bumpversion --new-version $1 part

echo 'Done!'
