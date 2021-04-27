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

function start-datomic-portforward {
    uuid="$(uuidgen)"
    tmux new -d -s "$uuid"
    tmux send-keys -t "${uuid}" "datomic client access teet-datomic" ENTER
}



function import-datomic-to-dev-local {
    # run in backend directory, needs the deps
    clojure -A:dev -e "
     (require 'datomic.dev-local)
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
    apt-get -y install docker.io git openjdk-11-jdk coreutils python3-pip python3-venv rlwrap postgresql-client-{12,common} maven unzip wget jq

    curl -O https://download.clojure.org/install/linux-install-1.10.3.822.sh
    chmod +x linux-install-1.10.3.822.sh
    ./linux-install-1.10.3.822.sh
    clojure -e true > /dev/null


    # fetch app & run setup scripts therein
    git clone -b $TEET_BRANCH $TEET_GIT_URL
    
    # db setup
    gensecret pgpasswd
    systemctl status docker | grep -q running || systemctl restart docker
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

    test -f cognitect-dev-tools.zip || aws s3 cp s3://${TEET_ENV}-build/cognitect-dev-tools.zip .
    unzip cognitect-dev-tools.zip
    cd cognitect-dev-tools-*
    ./install
    cd ..      

    test -f datomic-deps.tar.gz || aws s3 cp s3://${TEET_ENV}-build/datomic-deps.tar.gz .
    tar -C ~ -zxf datomic-deps.tar.gz
    test -f datomic-deps2.tar.gz || aws s3 cp s3://${TEET_ENV}-build/datomic-deps2.tar.gz .
    tar -C ~ -zxf datomic-deps2.tar.gz

    wget https://datomic-releases-1fc2183a.s3.amazonaws.com/tools/datomic-cli/datomic-cli-0.10.82.zip
    unzip datomic-cli*.zip
    install --mode=755 datomic-cli/datomic* /usr/local/bin/ 

    # datomic-cli in tmux
    start-datomic-portforward
    until netstat -pant | egrep -q '^tcp.*:8666\b.*LISTEN'; do
	echo "waiting for ssh forward to come up (attach to tmux and check there if stuck here)"
	sleep 1
    done
    
    

    import-datomic-to-dev-local
    
    cd mnt-teet
    cd db
    ./devdb_create_template.sh
    ./devdb_clean.sh

    cd ../..

    start-teet-app # backend, frontend & postgrest
    
    sleep 10 # fixme, wait for some sensible signal (poll backend tcp port with netstat?)
        
    ## unneeded when restoring pg backup
    # cd app/datasource-import
    # clojure -A:import example-config.edn     
    
    # todo: docker run problem - after experimenting with running this in codebuild, systemctl complains about init. privileged codebuild container didn't help. need to ditch codebuild & use ec2? or, run pg and postgrest in same container?
    
    # todo: config.edn for teet app & datomic-local setup

    # todo: use datomic.dev-local/import-cloud for datomic data
    
    # todo: use postgres backups for pg data (-> remove unneeded datasource-import run)


}

UBUNTU_AMI_ID=ami-0767046d1677be5a0
function run-in-ec2 {
    # todo: switch to using ec2 instance templates

    #
    aws ec2 describe-vpcs --output json > vpc.json
    TEET_VPCID=$(jq -r '.Vpcs[] | [.VpcId, (.Tags[]?|select(.Key=="Name")|.Value)] | select (.[1] == "Main TEET VPC") | .[0]' < vpc.json)
    # aws ec2 run-instances --image-id $UBUNTU_AMI_ID --count 1 --instance-type m5.xlarge \
    # 	--key-name $SSHKEYID --subnet-id subnet-abcd1234 --security-group-ids sg-abcd1234 \
    # 	--user-data file://$PWD/ci/scripts/install-standalone-vm.bash

    # fails, need to specify subnet id and security group etc.
    aws ec2 run-instances --image-id $UBUNTU_AMI_ID --count 1 --instance-type m5.xlarge \
	--key-name $SSHKEYID 
	--user-data file://$PWD/ci/scripts/install-standalone-vm.bash
}


