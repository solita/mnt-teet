#!/usr/bin/env bash
set -eu

BUILDDIR=`aws ssm get-parameters --names "/teet/s3/builddir" --query "Parameters[0].Value" | tr -d '"'`
PUBLICDIR=`aws ssm get-parameters --names "/teet/s3/publicdir" --query "Parameters[0].Value" | tr -d '"'`

if [ -z "$PUBLICDIR" ]
then
    echo "Empty public dir variable value"
    exit 1;
fi

echo "Fetching frontend build: $CODEBUILD_RESOLVED_SOURCE_VERSION"
aws s3 cp s3://$BUILDDIR/teet-frontend/frontend-$CODEBUILD_RESOLVED_SOURCE_VERSION.zip frontend.zip
mkdir frontend
cd frontend
unzip ../frontend.zip

# Rename main.js to main-<commit>.js and modify index.html
mv js/main.js js/main-$CODEBUILD_RESOLVED_SOURCE_VERSION.js
sed -i -e "s/main.js/main-$CODEBUILD_RESOLVED_SOURCE_VERSION.js/g" index.html

cd ..

# Gzip all frontend files in-place
find frontend -type f -exec gzip {} \; -exec mv {}.gz {} \;

# Remove old files
aws s3 rm s3://$PUBLICDIR --exclude "js/deploy.json" --recursive

# Upload new files (with content encoding set to gzip)
aws s3 sync frontend s3://$PUBLICDIR --acl public-read --content-encoding gzip
