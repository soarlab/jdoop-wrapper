#!/usr/bin/env bash

HOSTNAME=node-1.multinode.jpf-doop.emulab.net

SPARK_HOME=/mnt/storage/spark/spark-2.0.2-bin-hadoop2.7

echo "SPARK_MASTER_HOST='${HOSTNAME}'" > ${SPARK_HOME}/conf/spark-env.sh

${SPARK_HOME}/sbin/start-master.sh
