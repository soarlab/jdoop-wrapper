#!/usr/bin/env bash

# Copyright 2017 Marko Dimjašević
#
# This file is part of jdoop-wrapper.
#
# jdoop-wrapper is free software: you can redistribute it and/or modify it
# under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# jdoop-wrapper is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with jdoop-wrapper.  If not, see <http://www.gnu.org/licenses/>.


set -e

if [ "$(hostname)" != "node-1.multinode.jpf-doop.emulab.net" ]; then
    echo "Must run this on the master node, and not on a worker node!"
    exit 1
fi

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh
cd $WRAPPER_HOME/spark/driver
sbt compile
sbt assembly

echo "Stopping all containers..."
${WRAPPER_HOME}/lxc/stop-containers.scala

cd -
${WRAPPER_HOME}/spark/stop-all.sh || true
${WRAPPER_HOME}/spark/cleanup-work-dirs.scala
${WRAPPER_HOME}/spark/start-spark-all.sh

echo "Starting the Spark application..."
JAR_FILE=/users/marko/jdoop-wrapper/spark/driver/target/scala-2.11/jdoop-spark-application-assembly-1.0.jar
# MASTER=local[4]
MASTER=spark://${SPARK_MASTER_HOST}:7077
INPUT_LIST=/users/marko/jdoop-wrapper/sf110/a-random-subset-of-10-benchmarks.txt
# INPUT_LIST=/users/marko/jdoop-wrapper/sf110/benchmarks-that-work-with-jdoop.txt
TIMELIMIT=60
REP=2
EXP_NAME=test33

${SPARK_HADOOP_DIR}/bin/spark-submit --class jdoop.Main --master $MASTER \
		   ${JAR_FILE} $TIMELIMIT \
		   jdoop,${EXP_NAME},$REP,${INPUT_LIST} \
		   randoop,${EXP_NAME},$REP,${INPUT_LIST} \
