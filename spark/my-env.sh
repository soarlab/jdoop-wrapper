#!/usr/bin/env bash

SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-node-1.multinode.jpf-doop.emulab.net}

# This path has to be the same on the master and on all worker nodes
# in order to be able to start both the master and the worker nodes
# from a single script executed on the master.
SPARK_HOME=${SPARK_HOME:-/mnt/storage/spark/spark-2.0.2-bin-hadoop2.7}

# Set a Scala version used by Spark
SPARK_SCALA_VERSION=${SPARK_SCALA_VERSION:-"2.11"}
