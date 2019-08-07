FROM ubuntu:disco

RUN apt-get -y update
RUN apt-get -y install maven

RUN mkdir /opt/teet-db

COPY pom.xml /opt/teet-db/pom.xml
COPY migrate.sh /opt/teet-db/migrate.sh
RUN chmod a+x /opt/teet-db/migrate.sh

ENV DB_URI= \
    DB_USER= \
    DB_PASSWORD=

# Download all dependencies to image
RUN cd /opt/teet-db && mvn dependency:go-offline && mvn flyway:migrate || true

# Copy migration sources
COPY src /opt/teet-db/src

ENTRYPOINT /opt/teet-db/migrate.sh