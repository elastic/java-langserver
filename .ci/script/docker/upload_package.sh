if [[ -z "${AWS_ACCESS_KEY_ID}" ]]; then
    echo "AWS_ACCESS_KEY_ID is undefined"
    exit 1
elif [[ -z "${AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "AWS_SECRET_ACCESS_KEY is undefined"
    exit 1
fi

pip install awscli --user

/var/lib/jenkins/.local/bin/aws s3 cp test.sh s3://download.elasticsearch.org/code/java-langserver/snapshot