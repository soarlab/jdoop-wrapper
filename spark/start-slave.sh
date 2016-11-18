#!/usr/bin/env bash

SPARK_HOME=/mnt/storage/spark/spark-2.0.2-bin-hadoop2.7

${SPARK_HOME}/sbin/start-slave.sh spark://node-1.multinode.jpf-doop.emulab.net:7077
