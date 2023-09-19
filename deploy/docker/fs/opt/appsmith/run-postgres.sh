#!/bin/bash

rm -f /appsmith-stacks/data/postgres/main/core.*
mkdir -p "$TMP/postgres-stats"
exec /usr/lib/postgresql/13/bin/postgres -D "/appsmith-stacks/data/postgres/main" -c listen_addresses=127.0.0.1 -c stats_temp_directory="$TMP/postgres-stats"
