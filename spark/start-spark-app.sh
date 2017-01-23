#!/usr/bin/env bash

set -e

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh
cd $WRAPPER_HOME/spark/driver
sbt compile
sbt assembly
${WRAPPER_HOME}/lxc/stop-containers.scala
cd -
${SPARK_HADOOP_DIR}/bin/spark-submit --class jdoop.Main --master spark://${SPARK_MASTER_HOST}:7077 /users/marko/jdoop-wrapper/spark/driver/target/scala-2.11/jdoop-spark-application-assembly-1.0.jar /users/marko/jdoop-wrapper/sf110/benchmarks-that-work-with-jdoop.txt
