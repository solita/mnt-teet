#!/usr/bin/env bash

# script that installs teet app, postgresql db, and postgrest api on one server
# (to be decided: use datomic-local vs existing datomic?)

# exit on unhandled error status, error on undefined var references, consider pipelines failed when either side fails

set -euo pipefail 


TEET_BRANCH=master
TEET_GIT_URL=https://github.com/solita/mnt-teet.git
DATOMIC_SOURCE_DB_NAME=$(aws ssm get-parameter --name /teet/datomic/db-name --query "Parameter.Value" --output text)
TEET_ENV_NAME=$(aws ssm get-parameter --name /teet/env --query "Parameter.Value" --output text)

function gensecret {
    name="$1"
    touch "$name.secret"
    chmod 600 "$name.secret"
    head -c 20 /dev/urandom | base64 > "$name.secret"
}

function start-teet-app {
    cd app/api
    nohup ./dev.sh > api.out &
    cd ../..
    uuid="$(uuidgen)"
    tmux new -d -s "$uuid"
    tmux splitw -h -t "${uuid}:0.0"
    tmux send-keys -t "${uuid}.0" "cd app/backend && clj -A:dev" ENTER "(restart)" ENTER
    tmux send-keys -t "${uuid}.1" "cd app/frontend && ./dev.sh" ENTER
    # tmux a -t "$uuid"
}

function import-datomic-to-dev-local {
    clojure -e "
     (datomic.dev-local/import-cloud
    	    {:source {:system \"teet-datomic\"
              :db-name \"$DATOMIC_SOURCE_DB_NAME\" ; use SSM /teet/datomic/db-name 
              :server-type :ion
              :region \"eu-central-1\"
              :endpoint \"http://entry.teet-datomic.eu-central-1.datomic.net:8182/\"
              :query-geoup \"teet-datomic\"
              :proxy-port 8182} ; not needed when running in same vpc as compute group
     :dest {:system \"teet-test-env\"
            :server-type :dev-local
            :db-name \"teet\"}
     :filters {:since 0}})"
}


function install_deps_and_app {
    # set up deps and env
    test "$(whoami)" = root
    mkdir /var/tmp/teetinstall
    cd /var/tmp/teetinstall

    apt-get update
    apt-get -y install docker.io git openjdk-11-jdk coreutils python3-pip python3-venv rlwrap postgresql-client-{12,common} maven

    curl -O https://download.clojure.org/install/linux-install-1.10.3.822.sh
    chmod +x linux-install-1.10.3.822.sh
    ./linux-install-1.10.3.822.sh
    clojure -e true > /dev/null


    # fetch app & run setup scripts therein
    git clone -b $TEET_BRANCH $TEET_GIT_URL
    
    # db setup
    gensecret pgpasswd
    docker network create docker_teet
    docker run -d -p 127.0.0.1:5432:5432 --name teetdb --network docker_teet -e POSTGRES_PASSWORD="$(cat pgpasswd.secret)" postgres:11
    while [ "$(docker inspect -f '{{.State.Running}}' teetdb)" != true ]; do
	echo Waiting for pg to come up
	docker ps
	sleep 5
    done       
    for cmd in "apt-get update" "apt-get -y install --no-install-recommends postgresql-11-postgis-2.5 postgresql-11-postgis-2.5-scripts" ; do
	docker exec -it teetdb $cmd
    done
    docker exec teetdb sed -i -e '1i host all all 172.16.0.0/14 trust' /var/lib/postgresql/data/pg_hba.conf
    docker restart teetdb    

    test -f dev-tools.zip || aws s3 cp s3://${TEET_ENV}-build/cognitect-dev-tools.zip .
    unzip cognitect-dev-tools.zip
    cd cognitect-dev-tools-*
    ./install
    cd ..      
    
    cd mnt-teet
    cd db
    ./devdb_create_template.sh
    ./devdb_clean.sh

    cd ../..

    start-teet-app # backend, frontend & postgrest
    
    sleep 10
        
    ## unneeded when restoring pg backup
    # cd app/datasource-import
    # clojure -A:import example-config.edn     
    
    # todo: config.edn for teet app & datomic-local setup

    # todo: use datomic.dev-local/import-cloud for datomic data
    
    pg_dump 
    # todo: use postgres backups for pg data (-> remove unneeded datasource-import run)
}



function main() {
    kvm -smp 4 -m 4096 -vga qxl --boot once=c --drive "file=$HOME/src/mnt-teet/ci/vmcow1.qcow2"
}
