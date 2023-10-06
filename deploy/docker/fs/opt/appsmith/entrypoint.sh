#!/usr/bin/env bash

set -e

stacks_path=/appsmith-stacks

export SUPERVISORD_CONF_TARGET="$TMP/supervisor-conf.d/"  # export for use in supervisord.conf
export MONGODB_TMP_KEY_PATH="$TMP/mongodb-key"  # export for use in supervisor process mongodb.conf

CERTBOT_CONFIG_DIR="$stacks_path/letsencrypt"

mkdir -pv "$SUPERVISORD_CONF_TARGET" "$NGINX_WWW_PATH"

# ip is a reserved keyword for tracking events in Mixpanel. Instead of showing the ip as is Mixpanel provides derived properties.
# As we want derived props alongwith the ip address we are sharing the ip address in separate keys
# https://help.mixpanel.com/hc/en-us/articles/360001355266-Event-Properties
if [[ -n ${APPSMITH_SEGMENT_CE_KEY-} ]]; then
  ip="$(curl -sS https://api64.ipify.org || echo unknown)"
  curl \
    --user "$APPSMITH_SEGMENT_CE_KEY:" \
    --header 'Content-Type: application/json' \
    --data '{
      "userId":"'"$ip"'",
      "event":"Instance Start",
      "properties": {
        "ip": "'"$ip"'",
        "ipAddress": "'"$ip"'"
      }
    }' \
    https://api.segment.io/v1/track \
    || true
fi

if [[ -n "${FILESTORE_IP_ADDRESS-}" ]]; then

  ## Trim APPSMITH_FILESTORE_IP and FILE_SHARE_NAME
  FILESTORE_IP_ADDRESS="$(echo "$FILESTORE_IP_ADDRESS" | xargs)"
  FILE_SHARE_NAME="$(echo "$FILE_SHARE_NAME" | xargs)"

  echo "Running appsmith for cloudRun"
  echo "creating mount point"
  mkdir -p "$stacks_path"
  echo "Mounting File Sytem"
  mount -t nfs -o nolock "$FILESTORE_IP_ADDRESS:/$FILE_SHARE_NAME" /appsmith-stacks
  echo "Mounted File Sytem"
  echo "Setting HOSTNAME for Cloudrun"
  export HOSTNAME="cloudrun"
fi


function get_maximum_heap() {
    resource=$(ulimit -u)
    echo "Resource : $resource"
    if [[ "$resource" -le 256 ]]; then
        maximum_heap=128
    elif [[ "$resource" -le 512 ]]; then
        maximum_heap=256
    fi
}

function setup_backend_heap_arg() {
    if [[ ! -z ${maximum_heap} ]]; then
      export APPSMITH_JAVA_HEAP_ARG="-Xmx${maximum_heap}m"
    fi
}

init_env_file() {
  CONF_PATH="/appsmith-stacks/configuration"
  ENV_PATH="$CONF_PATH/docker.env"
  TEMPLATES_PATH="/opt/appsmith/templates"

  # Build an env file with current env variables. We single-quote the values, as well as escaping any single-quote characters.
  printenv | grep -E '^APPSMITH_|^MONGO_' | sed "s/'/'\\\''/g; s/=/='/; s/$/'/" > "$TMP/pre-define.env"

  echo "Initialize .env file"
  if ! [[ -e "$ENV_PATH" ]]; then
    # Generate new docker.env file when initializing container for first time or in Heroku which does not have persistent volume
    echo "Generating default configuration file"
    mkdir -p "$CONF_PATH"
    local default_appsmith_mongodb_user="appsmith"
    local generated_appsmith_mongodb_password=$(
      tr -dc A-Za-z0-9 </dev/urandom | head -c 13
      echo ""
    )
    local generated_appsmith_encryption_password=$(
      tr -dc A-Za-z0-9 </dev/urandom | head -c 13
      echo ""
    )
    local generated_appsmith_encription_salt=$(
      tr -dc A-Za-z0-9 </dev/urandom | head -c 13
      echo ""
    )
    local generated_appsmith_supervisor_password=$(
      tr -dc A-Za-z0-9 </dev/urandom | head -c 13
      echo ''
    )
    bash "$TEMPLATES_PATH/docker.env.sh" "$default_appsmith_mongodb_user" "$generated_appsmith_mongodb_password" "$generated_appsmith_encryption_password" "$generated_appsmith_encription_salt" "$generated_appsmith_supervisor_password" > "$ENV_PATH"
  fi


  echo "Load environment configuration"
  set -o allexport
  . "$ENV_PATH"
  . "$TMP/pre-define.env"
  set +o allexport
}

setup_proxy_variables() {
  export NO_PROXY="${NO_PROXY-localhost,127.0.0.1}"

  # If one of HTTPS_PROXY or https_proxy are set, copy it to the other. If both are set, prefer HTTPS_PROXY.
  if [[ -n ${HTTPS_PROXY-} ]]; then
    export https_proxy="$HTTPS_PROXY"
  elif [[ -n ${https_proxy-} ]]; then
    export HTTPS_PROXY="$https_proxy"
  fi

  # If one of HTTP_PROXY or http_proxy are set, copy it to the other. If both are set, prefer HTTP_PROXY.
  if [[ -n ${HTTP_PROXY-} ]]; then
    export http_proxy="$HTTP_PROXY"
  elif [[ -n ${http_proxy-} ]]; then
    export HTTP_PROXY="$http_proxy"
  fi
}

unset_unused_variables() {
  # Check for enviroment vairalbes
  echo "Checking environment configuration"
  if [[ -z "${APPSMITH_MAIL_ENABLED}" ]]; then
    unset APPSMITH_MAIL_ENABLED # If this field is empty is might cause application crash
  fi

  if [[ -z "${APPSMITH_OAUTH2_GITHUB_CLIENT_ID}" ]] || [[ -z "${APPSMITH_OAUTH2_GITHUB_CLIENT_SECRET}" ]]; then
    unset APPSMITH_OAUTH2_GITHUB_CLIENT_ID # If this field is empty is might cause application crash
    unset APPSMITH_OAUTH2_GITHUB_CLIENT_SECRET
  fi

  if [[ -z "${APPSMITH_OAUTH2_GOOGLE_CLIENT_ID}" ]] || [[ -z "${APPSMITH_OAUTH2_GOOGLE_CLIENT_SECRET}" ]]; then
    unset APPSMITH_OAUTH2_GOOGLE_CLIENT_ID # If this field is empty is might cause application crash
    unset APPSMITH_OAUTH2_GOOGLE_CLIENT_SECRET
  fi

  if [[ -z "${APPSMITH_RECAPTCHA_SITE_KEY}" ]] || [[ -z "${APPSMITH_RECAPTCHA_SECRET_KEY}" ]] || [[ -z "${APPSMITH_RECAPTCHA_ENABLED}" ]]; then
    unset APPSMITH_RECAPTCHA_SITE_KEY # If this field is empty is might cause application crash
    unset APPSMITH_RECAPTCHA_SECRET_KEY
    unset APPSMITH_RECAPTCHA_ENABLED
  fi
}

check_mongodb_uri() {
  echo "Checking APPSMITH_MONGODB_URI"
  isUriLocal=1
  if [[ $APPSMITH_MONGODB_URI == *"localhost"* || $APPSMITH_MONGODB_URI == *"127.0.0.1"* ]]; then
    echo "Detected local MongoDB"
    isUriLocal=0
  fi
}

init_mongodb() {
  if [[ $isUriLocal -eq 0 ]]; then
    echo "Initializing local database"
    MONGO_DB_PATH="$stacks_path/data/mongodb"
    MONGO_LOG_PATH="$MONGO_DB_PATH/log"
    MONGO_DB_KEY="$MONGO_DB_PATH/key"
    mkdir -p "$MONGO_DB_PATH"
    touch "$MONGO_LOG_PATH"

    if [[ ! -f "$MONGO_DB_KEY" ]]; then
      openssl rand -base64 756 > "$MONGO_DB_KEY"
    fi
    use-mongodb-key "$MONGO_DB_KEY"
  fi
}

init_replica_set() {
  echo "Checking initialized database"
  shouldPerformInitdb=1
  for path in \
    "$MONGO_DB_PATH/WiredTiger" \
    "$MONGO_DB_PATH/journal" \
    "$MONGO_DB_PATH/local.0" \
    "$MONGO_DB_PATH/storage.bson"; do
    if [ -e "$path" ]; then
      shouldPerformInitdb=0
      break
    fi
  done

  if [[ $isUriLocal -gt 0 && -f /proc/cpuinfo ]] && ! grep --quiet avx /proc/cpuinfo; then
    echo "====================================================================================================" >&2
    echo "==" >&2
    echo "== AVX instruction not found in your CPU. Appsmith's embedded MongoDB may not start. Please use an external MongoDB instance instead." >&2
    echo "== See https://docs.appsmith.com/getting-started/setup/instance-configuration/custom-mongodb-redis#custom-mongodb for instructions." >&2
    echo "==" >&2
    echo "====================================================================================================" >&2
  fi

  if [[ $shouldPerformInitdb -gt 0 && $isUriLocal -eq 0 ]]; then
    echo "Initializing Replica Set for local database"
    # Start installed MongoDB service - Dependencies Layer
    mongod --fork --port 27017 --dbpath "$MONGO_DB_PATH" --logpath "$MONGO_LOG_PATH"
    echo "Waiting 10s for MongoDB to start"
    sleep 10
    echo "Creating MongoDB user"
    mongosh "127.0.0.1/appsmith" --eval "db.createUser({
        user: '$APPSMITH_MONGODB_USER',
        pwd: '$APPSMITH_MONGODB_PASSWORD',
        roles: [{
            role: 'root',
            db: 'admin'
        }, 'readWrite']
      }
    )"
    echo "Enabling Replica Set"
    mongod --dbpath "$MONGO_DB_PATH" --shutdown || true
    mongod --fork --port 27017 --dbpath "$MONGO_DB_PATH" --logpath "$MONGO_LOG_PATH" --replSet mr1 --keyFile "$MONGODB_TMP_KEY_PATH" --bind_ip localhost
    echo "Waiting 10s for MongoDB to start with Replica Set"
    sleep 10
    mongosh "$APPSMITH_MONGODB_URI" --eval 'rs.initiate()'
    mongod --dbpath "$MONGO_DB_PATH" --shutdown || true
  fi

  if [[ $isUriLocal -gt 0 ]]; then
    echo "Checking Replica Set of external MongoDB"

    if appsmithctl check-replica-set; then
      echo "MongoDB ReplicaSet is enabled"
    else
      echo -e "\033[0;31m***************************************************************************************\033[0m"
      echo -e "\033[0;31m*      MongoDB Replica Set is not enabled                                             *\033[0m"
      echo -e "\033[0;31m*      Please ensure the credentials provided for MongoDB, has 'readWrite' role.      *\033[0m"
      echo -e "\033[0;31m***************************************************************************************\033[0m"
      exit 1
    fi
  fi
}

use-mongodb-key() {
  # We copy the MongoDB key file to `$MONGODB_TMP_KEY_PATH`, so that we can reliably set its permissions to 600.
  # Why? When the host machine of this Docker container is Windows, file permissions cannot be set on files in volumes.
  # So the key file should be somewhere inside the container, and not in a volume.
  cp -v "$1" "$MONGODB_TMP_KEY_PATH"
  chmod 600 "$MONGODB_TMP_KEY_PATH"
}

is_empty_directory() {
  [[ -d $1 && -z "$(ls -A "$1")" ]]
}

setup-custom-ca-certificates() (
  local stacks_ca_certs_path="$stacks_path/ca-certs"
  local store="$TMP/cacerts"
  local opts_file="$TMP/java-cacerts-opts"

  rm -f "$store" "$opts_file"

  if [[ -n "$(ls "$stacks_ca_certs_path"/*.pem 2>/dev/null)" ]]; then
    echo "Looks like you have some '.pem' files in your 'ca-certs' folder. Please rename them to '.crt' to be picked up automatically.".
  fi

  if ! [[ -d "$stacks_ca_certs_path" && "$(find "$stacks_ca_certs_path" -maxdepth 1 -type f -name '*.crt' | wc -l)" -gt 0 ]]; then
    echo "No custom CA certificates found."
    return
  fi

  # Import the system CA certificates into the store.
  keytool -importkeystore \
    -srckeystore /opt/java/lib/security/cacerts \
    -destkeystore "$store" \
    -srcstorepass changeit \
    -deststorepass changeit

  # Add the custom CA certificates to the store.
  find "$stacks_ca_certs_path" -maxdepth 1 -type f -name '*.crt' \
    -exec keytool -import -noprompt -keystore "$store" -file '{}' -storepass changeit ';'

  {
    echo "-Djavax.net.ssl.trustStore=$store"
    echo "-Djavax.net.ssl.trustStorePassword=changeit"
  } > "$opts_file"
)

configure_supervisord() {
  local supervisord_conf_source="/opt/appsmith/templates/supervisord"
  if [[ -n "$(ls -A "$SUPERVISORD_CONF_TARGET")" ]]; then
    rm -f "$SUPERVISORD_CONF_TARGET"/*
  fi

  cp -f "$supervisord_conf_source"/application_process/*.conf "$SUPERVISORD_CONF_TARGET"

  # Disable services based on configuration
  if [[ -z "${DYNO}" ]]; then
    if [[ $isUriLocal -eq 0 ]]; then
      cp "$supervisord_conf_source/mongodb.conf" "$SUPERVISORD_CONF_TARGET"
    fi
    if [[ $APPSMITH_REDIS_URL == *"localhost"* || $APPSMITH_REDIS_URL == *"127.0.0.1"* ]]; then
      cp "$supervisord_conf_source/redis.conf" "$SUPERVISORD_CONF_TARGET"
      mkdir -p "$stacks_path/data/redis"
    fi
    if [[ $runEmbeddedPostgres -eq 1 ]]; then
      cp "$supervisord_conf_source/postgres.conf" "$SUPERVISORD_CONF_TARGET"
    fi
  fi

}

# This is a workaround to get Redis working on different memory pagesize
# https://github.com/appsmithorg/appsmith/issues/11773
check_redis_compatible_page_size() {
  local page_size
  page_size="$(getconf PAGE_SIZE)"
  if [[ $page_size -gt 4096 ]]; then
    curl \
      --silent \
      --user "$APPSMITH_SEGMENT_CE_KEY:" \
      --header 'Content-Type: application/json' \
      --data '{ "userId": "'"$HOSTNAME"'", "event":"RedisCompile" }' \
      https://api.segment.io/v1/track \
      || true
    echo "Compile Redis stable with page size of $page_size"
    apt-get update
    apt-get install --yes build-essential
    curl --location https://download.redis.io/redis-stable.tar.gz | tar -xz -C /tmp
    pushd /tmp/redis-stable
    make
    make install
    popd
    rm -rf /tmp/redis-stable
  else
    echo "Redis is compatible with page size of $page_size"
  fi
}

init_postgres() {
  # Initialize embedded postgres by default; set APPSMITH_ENABLE_EMBEDDED_DB to 0, to use existing cloud postgres mockdb instance
  if [[ ${APPSMITH_ENABLE_EMBEDDED_DB: -1} != 0 ]]; then
    echo ""
    echo "Checking initialized local postgres"
    POSTGRES_DB_PATH="$stacks_path/data/postgres/main"

    mkdir -p "$POSTGRES_DB_PATH" "$TMP/pg-runtime"

    # Postgres does not allow it's server to be run with super user access, we use user postgres and the file system owner also needs to be the same user postgres
    chown -R postgres:postgres "$POSTGRES_DB_PATH" "$TMP/pg-runtime"

    if [[ -e "$POSTGRES_DB_PATH/PG_VERSION" ]]; then
      echo "Found existing Postgres, Skipping initialization"
    else
      echo "Initializing local postgresql database"
      mkdir -p "$POSTGRES_DB_PATH" "$TMP/postgres-stats"

      # Postgres does not allow it's server to be run with super user access, we use user postgres and the file system owner also needs to be the same user postgres
      chown postgres:postgres "$POSTGRES_DB_PATH" "$TMP/postgres-stats"

      # Initialize the postgres db file system
      su postgres -c "/usr/lib/postgresql/13/bin/initdb -D $POSTGRES_DB_PATH"
      echo "unix_socket_directories = '/tmp/appsmith/pg-runtime'" >> "$POSTGRES_DB_PATH/postgresql.conf"

      # Start the postgres server in daemon mode
      su postgres -c "/usr/lib/postgresql/13/bin/pg_ctl start -D $POSTGRES_DB_PATH"

      # Create mockdb db and user and populate it with the data
      seed_embedded_postgres
      # Stop the postgres daemon
      su postgres -c "/usr/lib/postgresql/13/bin/pg_ctl stop -D $POSTGRES_DB_PATH"
    fi
  else
    runEmbeddedPostgres=0
  fi

}

seed_embedded_postgres(){
    # Create mockdb database
    psql -U postgres -c "CREATE DATABASE mockdb;"
    # Create mockdb superuser
    su postgres -c "/usr/lib/postgresql/13/bin/createuser mockdb -s"
    # Dump the sql file containing mockdb data
    psql -U postgres -d mockdb --file='/opt/appsmith/templates/mockdb_postgres.sql'

    # Create users database
    psql -U postgres -c "CREATE DATABASE users;"
    # Create users superuser
    su postgres -c "/usr/lib/postgresql/13/bin/createuser users -s"
    # Dump the sql file containing mockdb data
    psql -U postgres -d users --file='/opt/appsmith/templates/users_postgres.sql'
}

safe_init_postgres(){
runEmbeddedPostgres=1
# fail safe to prevent entrypoint from exiting, and prevent postgres from starting
init_postgres || runEmbeddedPostgres=0
}

init_loading_pages(){
  local starting_page="/opt/appsmith/templates/appsmith_starting.html"
  local initializing_page="/opt/appsmith/templates/appsmith_initializing.html"
  local editor_load_page="$NGINX_WWW_PATH/loading.html"
  cp "$initializing_page" "$NGINX_WWW_PATH/index.html"
  # TODO: Also listen on 443, if HTTP certs are available.
  cat <<EOF > "$TMP/nginx-app.conf"
    client_body_temp_path '$TMP/nginx-client-body-temp';
    proxy_temp_path '$TMP/nginx-proxy-temp';
    fastcgi_temp_path '$TMP/nginx-fastcgi-temp';
    uwsgi_temp_path '$TMP/nginx-uwsgi-temp';
    scgi_temp_path '$TMP/nginx-scgi-temp';

    server {
      listen ${PORT:-80} default_server;
      location / {
        try_files \$uri \$uri/ /index.html =404;
      }
    }
EOF
  # Start nginx page to display the Appsmith is Initializing page
  nginx
  # Update editor nginx page for starting page
  cp "$starting_page" "$editor_load_page"
}

# Main Section
init_loading_pages
init_env_file
setup_proxy_variables
unset_unused_variables

check_mongodb_uri
if [[ -z "${DYNO}" ]]; then
  # Don't run MongoDB if running in a Heroku dyno.
  init_mongodb
  init_replica_set
else
  # These functions are used to limit heap size for Backend process when deployed on Heroku
  get_maximum_heap
  setup_backend_heap_arg
  # set the hostname for heroku dyno
  export HOSTNAME="heroku_dyno"
fi

setup-custom-ca-certificates

check_redis_compatible_page_size

safe_init_postgres
export "_IS_EMBEDDED_POSTGRES_RUNNING=$runEmbeddedPostgres"

configure_supervisord

mkdir -p "$CERTBOT_CONFIG_DIR" /appsmith-stacks/ssl

# Ensure the restore path exists in the container, so an archive can be copied to it, if need be.
mkdir -p /appsmith-stacks/data/{backup,restore}

# Create sub-directory to store services log in the container mounting folder
mkdir -p /appsmith-stacks/logs/{supervisor,backend,editor,rts,mongodb,redis,postgres,appsmithctl}

# Stop nginx gracefully
nginx -s quit

# Handle CMD command
exec "$@"
