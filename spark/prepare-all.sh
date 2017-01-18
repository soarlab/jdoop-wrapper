#!/usr/bin/env bash

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

hostname=$(hostname)
if [ "$hostname" != "${SPARK_MASTER_HOST}" ]; then
    echo "This script must be executed on the master node!"
    exit 1
fi

${WRAPPER_HOME}/spark/prepare-master.sh &

nodes=$(cat ${WRAPPER_HOME}/spark/conf/slaves | grep "node-")

for node in $nodes; do
    ssh $node ${WRAPPER_HOME}/spark/prepare-worker-node.sh &
done
