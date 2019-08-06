# Maanteeamet Proof Of Concept AWS test

Test repo for project registry + sample frontend and CI

## Structure

* ci/  contains build and deployment scripts for AWS CodeBuild
* db/  contains migrations for setting up the database
* app/ contains different parts of the application
* app/api/ the TEET database API
* app/frontend/ TEET frontend app
* cfn/  contains all cloudformation templates
* cfn/ci/  contains templates to setup codebuild and buckets (singleton)
* cfn/db/  database cluster
* cfn/services  ECR cluster and ECS task defs

* cfn/auth  cognito user pool and TARA integration
