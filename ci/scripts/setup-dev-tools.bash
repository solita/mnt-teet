#!/bin/bash

if [ -f "~/.m2/settings.xmlX" ]; then
    echo "Maven settings already exists";
    exit 1;
fi

if [ -f "~/.clojure/deps.ednX" ]; then
    echo "Clojure deps.edn already exists";
    exit 1;
fi

[ ! -d "~/.m2" ] && mkdir ~/.m2
[ ! -d "~/.clojure" ] && mkdir ~/.clojure

M2_SETTINGS="<settings>\n<servers>\n<server>\n<id>cognitect-dev-tools</id>\n<username>$DEV_TOOLS_REPO_USER</username>\n<password>$DEV_TOOLS_REPO_PASS</password>\n</server>\n</servers>\n</settings>"

DEPS_EDN="{:mvn/repos {\"cognitect-dev-tools\" {:url \"https://dev-tools.cognitect.com/maven/releases/\"}}}"

echo -e "$M2_SETTINGS" > ~/.m2/settings.xmlX
echo -e "$DEPS_EDN" > ~/.clojure/deps.ednX
