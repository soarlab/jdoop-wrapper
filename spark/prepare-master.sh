#!/usr/bin/env bash

# Prepares the master node for computation

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
${WRAPPER_HOME}/spark/install-spark.sh
${WRAPPER_HOME}/spark/install-sbt.scala
. ${WRAPPER_HOME}/spark/my-env.sh

NFS_SERVER=$(grep node-1 /etc/hosts | grep big-lan | awk -F" " '{print $1}')
IFS='.' read -ra ADDR <<< "$NFS_SERVER"
counter=0
NFS_NETWORK=""
for i in "${ADDR[@]}"; do
    let counter=counter+1
    NFS_NETWORK="$NFS_NETWORK$i."
    if [ "$counter" -eq "3" ]; then
	break;
    fi
done
NFS_NETWORK="${NFS_NETWORK}0/24"

${WRAPPER_HOME}/nfs/install-nfs-server.scala ${NFS_NETWORK} ${SERVER_DIR}
