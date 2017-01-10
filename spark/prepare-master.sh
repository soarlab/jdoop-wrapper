#!/usr/bin/env bash

# Prepares the master node for computation

set -e

# Disable the swap memory
sudo swapoff -a

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

# The master node for now will be a worker node too
PKGS="lxc scala"
sudo apt-get update && sudo apt-get install --yes $PKGS
$WRAPPER_HOME/lxc/create-debian-container.scala
$WRAPPER_HOME/lxc/create-jdoop-container.scala stretch jdoop
