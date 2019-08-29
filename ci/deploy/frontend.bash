#!/bin/bash
echo Fetching frontend build
aws s3 cp s3://teet-build/frontend/frontend-$COMMIT.zip frontend.zip
mkdir frontend
cd frontend
unzip ../frontend.zip
cd ..
aws s3 sync frontend s3://dev-teet-public
