#!/bin/bash
# Copies the program and its dependencies to a remote host
if [ -z "$1" ]; then
    printf "Usage: bin/rsync.sh <host>\n"
    exit 1
fi

host=$1
dest=src/netbeans-proxies
path="mkdir -p $dest && rsync"

list=(bin/start.sh external/binaries-list target/lib/ target/*.jar)
rsync -avR --rsync-path="$path" "${list[@]}" "$host:$dest/"
