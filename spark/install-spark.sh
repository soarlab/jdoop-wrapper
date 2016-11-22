#!/usr/bin/env bash

set -e

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

PKGS="scala"

sudo apt-get update && sudo apt-get install --yes $PKGS

sudo mkdir -p ${SPARK_DIR}
sudo chown $USER: ${SPARK_DIR}

cd ${SPARK_DIR}

SPARK_HADOOP_ARCHIVE=${SPARK_HADOOP}.tgz

if [ ! -d ${SPARK_HADOOP_DIR} ]; then
    if [ ! -e "${SPARK_DIR}/${SPARK_HADOOP_ARCHIVE}" ]; then
	wget http://d3kbcqa49mib13.cloudfront.net/${SPARK_HADOOP_ARCHIVE}
    fi
    tar xf ${SPARK_HADOOP_ARCHIVE}
fi
