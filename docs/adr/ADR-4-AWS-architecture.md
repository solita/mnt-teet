# ADR-4 AWS architecture

## Context

TEET project originates on a similar Finnish project named Velho. The solution designed is based on and utilizes the Amazon Cloud Environment (AWS) turnkey solutions and components. AWS (Amazon Web Services) is a leading cloud service provider, who has excellent experience in service lifecycle and support systems. Cloud services make the solution flexible, scalable, and cost-effective for the environment.

## Decision

Both Frontend and Backend is implemented using the Datomic Cloud in conjunction with AWS services.

Datomic's data model - based on immutable facts stored over time - enables a physical design that is fundamentally different from traditional RDBMSs. Instead of processing all requests in a single server component, Datomic distributes transactions, queries, indexing, and caching to provide high availability, horizontal scaling, and elasticity. Datomic also allows for dynamic assignment of compute resources to tasks without any kind of pre-assignment or sharding.

The durable elements managed by Datomic are called Storage Resources, including:
- the DynamoDB Transaction Log
- S3 storage of Indexes
- an EFS cache layer
- operational logs
- a VPC and subnets in which computational resources will run

These resources are retained even when no computational resources are active, so you can shut down all the active elements of Datomic while maintaining your data.

Every running system has a single primary compute stack which provides computational resources and a means to access those resources. A Primary Compute Stack consists of:
- a primary compute group dedicated to transactions, indexing, and caching.
- Route53 and/or Network Load Balancer (NLB) endpoints
- a Bastion Server

Requests are forwarded to Datomic using the AWS API Gateway solution.

Database solution is based on AWS Aurora which is a cluster of PostgreSQL instances which consists of:

- a single primary DB instance - supports read and write operations, and performs all of the data modifications to the cluster volume. 
- 2 read replicas - connects to the same storage volume as the primary DB instance and supports only read operations.

Requests to the Database are sent using PostgrestAPI solution which ran in dockerized confinement. 

AWS ECS with Fargate task is used to deliver the PostgrestAPI solution.

Application and services CI/CD will be fully automatized using AWS Codebuild.
 
CloudFormation templates will support TEET environment's deployment to new AWS accounts.

## Status

Accepted

## Consequences

- Limited control and flexibility. Since the cloud infrastructure is entirely owned, managed, and monitored by the service provider, it transfers minimal control over to the customer.
- Vendor lock-in. Differences between vendor platforms may create difficulties in migrating from one cloud platform to another, which could equate to additional costs and configuration complexities.