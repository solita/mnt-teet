#!/usr/bin/env bash

# script that installs teet app, postgresql db, datomic local, postgrest api on one vm.
#
# usage:
# run this from shell on your dev machine:
# ci/scripts/install-standalone-vm.bash launchvm <ssh-key-name>
# where ssh key name a key name from the registered ec2 key pairs in the aws env.
# This will start the vm, detect what git branch you are on and try to check out that same branch at the remote vm.


: ${MACHINE_ID:=a} # pick one from a-e that doesn't conflict with someone else


# exit on unhandled error status, error on undefined var references, consider pipelines failed when either side fails
set -euo pipefail 

TEET_GIT_URL=https://github.com/solita/mnt-teet.git
export AWS_REGION=eu-central-1
export AWS_DEFAULT_REGION=$AWS_REGION

function gensecret {
    name="$1"
    touch "$name.secret"
    chmod 600 "$name.secret"
    head -c 42 /dev/urandom | base64 > "$name.secret"
}

function patient-docker-pull {
    for x in $(seq 10); do
	if docker pull "$1"; then
	    break
	else
	    echo docker pull failed, retrying after 30s sleep, retry $x of 10
	    sleep 30
	fi
    done
}


function start-teet-app {
    local DB_URI="postgres://authenticator@teetdb:5432/teet"
    local JWT_SECRET="$(cat jwt.secret)"
    mkdir -p mnt-teet-private

    patient-docker-pull postgrest/postgrest
    
    docker run -d --network docker_teet --name teetapi -p 127.0.0.1:3000:3000 \
       -e PGRST_DB_URI="$DB_URI" \
       -e PGRST_DB_ANON_ROLE="teet_anon" \
       -e PGRST_DB_SCHEMA="teet" \
       -e PGRST_JWT_SECRET="$JWT_SECRET" \
       postgrest/postgrest    
    cat > ~/.datomic/dev-local.edn <<EOF
{:storage-dir "/home/root/.datomic/dev-local-data"}
EOF
    
    cat > mnt-teet-private/config.edn <<EOF
{:datomic
 {:db-name "teet"
  :asset-db-name "asset"
  :client {:server-type :dev-local
           :storage-dir "$HOME/.datomic/dev-local-data"
           :system "teet-test-env"}}
 :listen-address "0.0.0.0"
 :auth {:jwt-secret "$JWT_SECRET"
        :basic-auth-password "$(ssm-get /teet/api/basic-auth-password)"}
 :env :dev
 :api-url "https://$MYDNS:3443"
 :enabled-features #{:road-information-view
                     :meetings
                     :component-view
                     :data-frisk
                     :my-role-display
                     :dummy-login
                     :cooperation
                     :admin-inspector
                     ; :vektorio
                     :asset-db
                     :cost-items
                     :land-owner-opinions
                     :assetmanager}

 :file {:allowed-suffixes #{"doc" "docx" "xlsx" "xls" "ppt" "pptx" "rtf" "odf" "pdf" "dwf" "dwg" "dgn" "dxf" "shp" "dbf" "kml" "kmz" "ifc" "xml" "bcf" "rvt" "skp" "3dm" "ags" "gpx" "png" "jpg" "jpeg" "tif" "tiff" "ecw" "shx" "lin" "wav" "mp3" "ogg" "aac" "mov" "mp4" "m4v" "avi" "las"}
        :image-suffixes #{"png" "jpg" "jpeg" "tif" "tiff" "ecw"}}
 :xroad {:query-url "$(ssm-get /teet/xroad-query-url)"
         :instance-id "$(ssm-get /teet/xroad-instance-id)"}

 :document-storage {:bucket-name "teet-dev2-documents"
                    :export-bucket-name "teet-dev-zip-export"}

 :road-registry {:wfs-url "https://teeregister.mnt.ee/290424/wfs"
                 :wms-url "https://teeregister.mnt.ee/teenus/wms"
                 :api {:endpoint "https://teeregister-api.mnt.ee"
                       :username "$(ssm-get /teet/road-registry/api/username)"
                       :password "$(ssm-get /teet/road-registry/api/password)"}}
 ;; Estonian Nature Information System (EELIS)
 :eelis {:wms-url "https://gsavalik.envir.ee/geoserver/eelis/ows"}
 :heritage {:wms-url "https://xgis.maaamet.ee/xgis2/service/clnl/muinsus"}
 :base-url "https://$MYDNS:4443"
 :notify {:application-expire-days "45"}
 :email {:subject-prefix "LOCAL"
         :contact-address "local@dev.fi"
         :from "local@dev.fi"
         :server "localhost:25"}
 :asset {:default-owner-code "DEV"}
 :contract {:state-procurement-url "$(ssm-get /teet/contract/state-procurement-url)"
            :thk-procurement-url "$(ssm-get /teet/contract/thk-procurement-url)"}
 }
EOF
    uuid="$(uuidgen)"
    tmux new -d -s "$uuid"
    tmux splitw -h -t "${uuid}:0.0"
    tmux send-keys -t "${uuid}.0" "cd mnt-teet/app/backend && clj -A:dev" ENTER "(restart)" ENTER
    tmux send-keys -t "${uuid}.1" "cd mnt-teet/app/frontend && chown -R ubuntu:ubuntu . && sudo -u ubuntu npm install && bash build.sh && ./dev.sh" ENTER
    # tmux a -t "$uuid"
}

function start-datomic-portforward {
    uuid="$(uuidgen)"
    tmux new -d -s "${uuid}"
    cat >> ~/.ssh/config <<EOF
StrictHostKeyChecking no
EOF
    tmux send-keys -t "$uuid" "datomic client access teet-datomic" ENTER
    until netstat -pant | grep LISTEN | grep -E -q '/ssh *$'; do
	echo sleeping until datomic ssh forward seems active
	sleep 3
    done
    
}

function control-datomic-bastion-ssh-access {
    # also set up revocation for it in exit trap (but it's fail-open..)
    local SG
    SG=$(aws ec2 describe-security-groups  --filters Name=tag:Name,Values=teet-datomic-bastion --query "SecurityGroups[*].{ID:GroupId}" --output text)    
    if [ "$1" = on ]; then
	aws ec2 authorize-security-group-ingress \
	    --group-id "$SG" \
	    --protocol tcp \
	    --port 22 \
	    --cidr "$MYIP"/32
	echo allowed ssh access to datomic bastion from ip "$MYIP"
    else
	echo revoke ssh access to datomic ip from "$MYIP"
	aws ec2 revoke-security-group-ingress \
	    --group-id "$SG" \
	    --protocol tcp \
	    --port 22 \
	    --cidr "$MYIP"/32
    fi
}



function import-datomic-to-dev-local {
    # run in backend directory, needs the deps
    local DATOMIC_SOURCE_DB_NAME="$1"
    mkdir -p ~/.datomic
    cd mnt-teet/app/backend
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
            :storage-dir \"$HOME/.datomic/dev-local-data\"
            :server-type :dev-local
             :db-name \"$2\"}
     :filters {:since 0}})"
    cd -
}

function setup-postgres {
    AWS_SSM_DB_URI=$(ssm-get /teet/migrate/db-uri)
    SOURCE_DB_USER=$(ssm-get /teet/migrate/db-user)
    SOURCE_DB_PASS=$(ssm-get /teet/migrate/db-password)
    
    SOURCE_DB_NAME=$AWS_SSM_DB_URI; SOURCE_DB_NAME=${AWS_SSM_DB_URI#*//}; SOURCE_DB_NAME=${SOURCE_DB_NAME#*/}
    SOURCE_DB_HOST=$AWS_SSM_DB_URI; SOURCE_DB_HOST=${AWS_SSM_DB_URI#*//}; SOURCE_DB_HOST=${SOURCE_DB_HOST%/*};
    cd mnt-teet/db
    bash devdb_create_template.sh
    env PSQL_TEET_DB_OWNER=teetmaster bash devdb_clean.sh
    cd ../..
    
    PGPASSWORD=$SOURCE_DB_PASS /usr/bin/pg_dump -Fc -h "$SOURCE_DB_HOST" -U "$SOURCE_DB_USER" "$SOURCE_DB_NAME" > t.dump
    du -h t.dump
    pg_restore -c -d teet -h localhost -U postgres < t.dump || true # sadly no way to tell between drop errors and actual restore errors
    psql -c "GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA teet TO teet_user;" -h localhost -U postgres teet
}

function ssm-get {
    aws ssm get-parameter --name "$1" --query "Parameter.Value" --output text
}

# unused now, could be used in cofig generation from ssm
# function ssm-get-csv {
#     local v="$(aws ssm get-parameter --name "$1" --output text --query Parameter.Value)"
#     IFS=", " read -ra SSMVALS <<< $v
# }

function update-dyndns {
    local dnsbasename="$1"
    local dnssuffix="$(ssm-get /dev-testsetup/testvm-dns-suffix)"
    local myv4addr="$(ec2metadata --public-ipv4)"
    local format="$(ssm-get /dev-testsetup/testvm-dyndns-url-template)"
    local url="$(printf ${format} "${dnsbasename}.${dnssuffix}" "${myv4addr}")"
    curl  "${url}"
    MYDNS="${dnsbasename}.${dnssuffix}"
    MYDNSSUFFIX="$dnssuffix"
}


function new-certs {
    local MY_EMAIL
    MY_EMAIL=$(ssm-get /teet/email/from)    
    local MYDOMAINS
    MYDOMAINS="$(for x in a b c d e; do echo -n ,${x}."$DNS_SUFFIX"; done | sed s/,//)"
    certbot certonly \
	    --standalone \
	    --non-interactive \
	    --agree-tos \
	    --preferred-challenges http \
	    --email "$MY_EMAIL" \
	    --domains "$MYDOMAINS"
 # output for reference:
 # - Congratulations! Your certificate and chain have been saved at:
 #   /etc/letsencrypt/live/a.${DNS_SUFFIX}/fullchain.pem
 #   Your key file has been saved at:
 #   /etc/letsencrypt/live/a.${DNS_SUFFIX}/privkey.pem
 #   Your cert will expire on <date>. To obtain a new or tweaked
 #   version of this certificate in the future, simply run certbot
 #   again. To non-interactively renew *all* of your certificates, run
 #   "certbot renew"

}

function get-certs {
    # we have to do the renew / cert bundle caching thing because of https://letsencrypt.org/docs/rate-limits/
    aws s3 cp "${BUILD_S3_BUCKET}"/standalonevm-certs.tgz .
    tar -C /etc -xzf standalonevm-certs.tgz    
    certbot renew # will renew only if expiry is imminent
    tar -C /etc -zcf standalonevm-certs.tgz letsencrypt
    aws s3 cp standalonevm-certs.tgz "${BUILD_S3_BUCKET}"/standalonevm-certs.tgz

    # don't want bundled cron job to do renewals without saving certs
    rm -f /etc/cron.d/certbot
    systemctl stop certbot.timer
    systemctl disable certbot.timer
    
    openssl pkcs12 -export \
	    -inkey "/etc/letsencrypt/live/a.${DNS_SUFFIX}/privkey.pem" -in "/etc/letsencrypt/live/a.${DNS_SUFFIX}/fullchain.pem" \
	    -out jetty.pkcs12 -passout 'pass:dummypass'

    keytool -importkeystore -noprompt \
	    -srckeystore jetty.pkcs12 -srcstoretype PKCS12 -srcstorepass dummypass \
	    -destkeystore teet.keystore -deststorepass dummypass

}

function read-instance-tag {
    local myid
    myid="$(curl -s http://169.254.169.254/latest/meta-data/instance-id)"
    TEET_BRANCH="$(aws ec2 describe-tags --filters "Name=resource-id,Values=$myid" "Name=key,Values=$1" | jq -r ".Tags[0].Value")"

}

function run-caddy-revproxy {
    patient-docker-pull caddy:2-alpine
    cat > Caddyfile <<EOF
{
  default_sni ${MYDNS}
}

${MYDNS}:443 {
  reverse_proxy teetapi:3000
  tls /etc/letsencrypt/live/a.${MYDNSSUFFIX}/fullchain.pem /etc/letsencrypt/live/a.${MYDNSSUFFIX}/privkey.pem

}

EOF
    docker run -d --network docker_teet -p 0.0.0.0:3443:443 -v $PWD/Caddyfile:/etc/caddy/Caddyfile:ro -v /etc/letsencrypt:/etc/letsencrypt caddy:2-alpine
}



function install-deps-and-app {
    # set up deps and env
    test "$(whoami)" = root
    export HOME=~root
    mkdir -p /var/tmp/teetinstall
    cd /var/tmp/teetinstall

    apt-get update
    apt-get -y install docker.io git openjdk-11-jdk coreutils python3-pip python3-venv rlwrap postgresql-client-{12,common} maven unzip wget jq awscli net-tools certbot npm

    curl -O https://download.clojure.org/install/linux-install-1.10.3.822.sh
    chmod +x linux-install-1.10.3.822.sh
    ./linux-install-1.10.3.822.sh
    clojure -e true > /dev/null

    TEET_BRANCH=$(read-instance-tag teet-branch)
    echo checking out "$TEET_GIT_URL" branch "$TEET_BRANCH"
    git clone -b "$TEET_BRANCH" "$TEET_GIT_URL"
    
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
	docker exec -i teetdb bash -c "$cmd"
    done
    docker exec teetdb sed -i -e '1i host all all 172.16.0.0/14 trust' /var/lib/postgresql/data/pg_hba.conf
    echo -e "max_wal_senders = 0\nwal_level = minimal\nfsync = off\nfull_page_writes = off\n" | docker exec -i teetdb bash -c 'cat >> /var/lib/postgresql/data/postgresql.conf' # try to make loading faster
    docker restart teetdb    

    gensecret jwt
    
    TEET_ENV=$(ssm-get /teet/env)
    DNS_SUFFIX=$(ssm-get /dev-testsetup/testvm-dns-suffix)
    BUILD_S3_BUCKET="s3://${TEET_ENV}-build"
    
    
    test -f cognitect-dev-tools.zip || aws s3 cp "${BUILD_S3_BUCKET}"/cognitect-dev-tools.zip .
    unzip cognitect-dev-tools.zip
    cd cognitect-dev-tools-*
    ./install
    cd ..      

    test -f datomic-deps.tar.gz || aws s3 cp "${BUILD_S3_BUCKET}"/datomic-deps.tar.gz .
    tar -C ~ -zxf datomic-deps.tar.gz
    test -f datomic-deps2.tar.gz || aws s3 cp "${BUILD_S3_BUCKET}"/datomic-deps2.tar.gz .
    tar -C ~ -zxf datomic-deps2.tar.gz

    wget https://datomic-releases-1fc2183a.s3.amazonaws.com/tools/datomic-cli/datomic-cli-0.10.82.zip
    unzip datomic-cli*.zip
    install --mode=755 datomic-cli/datomic* /usr/local/bin/ 

    start-datomic-portforward    
    
    import-datomic-to-dev-local "$(ssm-get /teet/datomic/db-name)" teet
    import-datomic-to-dev-local "$(ssm-get /teet/datomic/asset-db-name)" asset
    
    MACHINE_ID=$(read-instance-tag teet-machine-id)
    update-dyndns $MACHINE_ID # sets $MYDNS. tbd: select which dns name from the pool to assume
    get-certs
    
    run-caddy-revproxy
    setup-postgres
    
    start-teet-app # config generation, backend, frontend & postgrest

    
    until netstat -pant | grep LISTEN | grep -E -q ':4000.*LISTEN.*/java *$'; do
	echo sleeping until backend starts listening on :4000
	sleep 3
    done

}

function public-addr-of-instance {
    aws ec2 describe-instances --instance-ids "$1" --output text|grep ASSOCIATION | grep compute.amazonaws.com | cut -f 3 | head -1
}

function run-in-ec2 {
    local SCRIPTPATH
    local SSHKEYID
    local THISBRANCH
    local RUNINFOFILE
    local INSTANCEID
    local SLEEPSECS
    local ADDR
    RUNINFOFILE="$(mktemp /tmp/runinfoXXXXX.json)"
    THISBRANCH="$(git rev-parse --abbrev-ref HEAD)"
    echo using branch "$THISBRANCH"
    SCRIPTPATH="$(realpath "$0")"
    if [ $# -gt 0 ]; then
	SSHKEYID="$1"
    else
	read -r -p 'ssh keypair name> ' SSHKEYID
    fi
    aws ec2 run-instances --count 1 --instance-type t3.xlarge --key-name "${SSHKEYID}" \
        --user-data "file://${SCRIPTPATH}" \
	--tag-specifications "ResourceType=instance,Tags=[{Key=teet-branch,Value=${THISBRANCH}},{Key=teet-machine-id,Value=${MACHINE_ID}}]"  \
        --launch-template LaunchTemplateName=standalone-teetapp-template | tee "$RUNINFOFILE"
    INSTANCEID="$(jq -r .Instances[0].InstanceId < "$RUNINFOFILE")"
    echo "waiting for address assignment"    
    while sleep 10; do
	ADDR="$(public-addr-of-instance "$INSTANCEID")"
	if [ -z "$ADDR" ]; then
	    continue
	else
	    break
	fi	
    done
    echo vm ec2 dns is "$ADDR"
    MYIP="$(host "$ADDR" | sed -n "s/.*has address //p")"
    control-datomic-bastion-ssh-access on
        
    SLEEPSECS="$[60 * 60 * 8 - 300]"
    echo Will terminate instance "$INSTANCEID" on "$(date --iso=seconds -d "now + $SLEEPSECS seconds")"
    echo "Toggle termination protection on the instance to prevent automatic termination"
    echo "To terminate early, run: aws ec2 terminate-instances --instance-ids $INSTANCEID"
    
    sleep $[60 * 60 * 8 - 300] # 5 mins short of 8 hours, for 8 build codebuild time limit
    echo not running aws ec2 terminate-instances --instance-ids "$INSTANCEID"
    control-datomic-bastion-ssh-access off
}



if [ $# -gt 0 ]; then
    if [ "$1" != launchvm ]; then
	echo unknown arg "$1"
	exit 1
    fi
    shift
    # assume we're running in codebuild or dev workstation
    run-in-ec2 "$@" # keypair name passed as arg
    
else
    # we're running as the cloud-init script (aka user-data script) inside the vm
    # - output will appear as cloud-init messages in /var/log/syslog
    echo starting teet app install
    set -x
    install-deps-and-app
fi
