#!/usr/bin/env bash

# Prepares the master node for computation

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
${WRAPPER_HOME}/spark/install-spark.sh
. ${WRAPPER_HOME}/spark/my-env.sh
${WRAPPER_HOME}/nfs/install-nfs-server.scala ${NFS_NETWORK} ${SERVER_DIR}
