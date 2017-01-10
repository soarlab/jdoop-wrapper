#!/usr/bin/env bash

# Prepares a worker (slave) node for computation

set -e

# Disable the swap memory
sudo swapoff -a

PKGS="lxc scala"
sudo apt-get update && sudo apt-get install --yes $PKGS

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

$WRAPPER_HOME/lxc/create-debian-container.scala
$WRAPPER_HOME/lxc/create-jdoop-container.scala stretch jdoop
NFS_SERVER=$(grep node-1 /etc/hosts | grep big-lan | awk -F" " '{print $1}')
$WRAPPER_HOME/nfs/install-nfs-client.scala ${NFS_SERVER} ${SERVER_DIR} ${MOUNT_DIR}
$WRAPPER_HOME/spark/install-spark.sh
${WRAPPER_HOME}/spark/install-sbt.scala
$WRAPPER_HOME/cpu/disable-hyperthreading.sh || true

. ${WRAPPER_HOME}/spark/my-env.sh
cp ${WRAPPER_HOME}/spark/conf/spark-env-slave.sh ${SPARK_HOME}/conf/spark-env.sh
