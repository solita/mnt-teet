#!/bin/bash

if [ -f "~/.m2/settings.xml" ]; then
    echo "Maven settings already exists";
    exit 1;
fi

if [ -f "~/.clojure/deps.edn" ]; then
    echo "Clojure deps.edn already exists";
    exit 1;
fi

[ ! -d "~/.m2" ] && mkdir ~/.m2
[ ! -d "~/.clojure" ] && mkdir ~/.clojure

M2_SETTINGS="<settings>\n<servers>\n<server>\n<id>cognitect-dev-tools</id>\n<username>$DEV_TOOLS_REPO_USER</username>\n<password>$DEV_TOOLS_REPO_PASS</password>\n</server>\n<server><id>datomic-cloud</id><username>$AWS_ACCESS_KEY_ID</username><password>$AWS_SECRET_ACCESS_KEY</password></server></servers>\n</settings>"

DEPS_EDN="{:mvn/repos {\"cognitect-dev-tools\" {:url \"https://dev-tools.cognitect.com/maven/releases/\"}}}"

echo -e "$M2_SETTINGS" > ~/.m2/settings.xml
echo -e "$DEPS_EDN" > ~/.clojure/deps.edn
