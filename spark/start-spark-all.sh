#!/usr/bin/env bash

# Starts an Apache Spark master and all worker/slave nodes specified
# in conf/slaves. Make sure to prepare each worker node by running the
# prepare-worker-node.sh script on it first.

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"

. ${WRAPPER_HOME}/spark/my-env.sh
cp ${WRAPPER_HOME}/spark/conf/slaves ${SPARK_HOME}/conf/
. ${SPARK_HOME}/sbin/start-all.sh
