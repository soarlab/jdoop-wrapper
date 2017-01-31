#!/usr/bin/env bash

# Prepares a worker (slave) node for computation

set -e

# Disable the swap memory
sudo swapoff -a

PKGS="lxc scala"
sudo apt-get update && sudo apt-get install --yes $PKGS

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

# This is a mount point used for benchmarks and results
sudo chown $USER: /mnt/storage
sudo chown $USER: /mnt/scratch

$WRAPPER_HOME/lxc/change-lxcpath.sh
$WRAPPER_HOME/lxc/create-debian-container.scala
$WRAPPER_HOME/lxc/create-jdoop-container.scala stretch jdoop
$WRAPPER_HOME/spark/install-spark.sh
${WRAPPER_HOME}/spark/install-sbt.scala
$WRAPPER_HOME/cpu/disable-hyperthreading.sh || true

. ${WRAPPER_HOME}/spark/my-env.sh
cp ${WRAPPER_HOME}/spark/conf/spark-env-slave.sh ${SPARK_HOME}/conf/spark-env.sh
