#!/usr/bin/env bash

# Prepares the master node for computation

set -e

# Disable the swap memory
sudo swapoff -a

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
${WRAPPER_HOME}/spark/install-spark.sh
${WRAPPER_HOME}/spark/install-sbt.scala
. ${WRAPPER_HOME}/spark/my-env.sh

# The master node for now will be a worker node too
PKGS="lxc scala"
sudo apt-get update && sudo apt-get install --yes $PKGS
$WRAPPER_HOME/lxc/change-lxcpath.sh
$WRAPPER_HOME/lxc/create-debian-container.scala
$WRAPPER_HOME/lxc/create-jdoop-container.scala stretch jdoop
