#!/usr/bin/env bash

SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-node-1.multinode.jpf-doop.emulab.net}

SPARK_DIR=/mnt/storage/spark
SPARK_HADOOP=spark-2.0.2-bin-hadoop2.7
SPARK_HADOOP_DIR=${SPARK_DIR}/${SPARK_HADOOP}

# This path has to be the same on the master and on all worker nodes
# in order to be able to start both the master and the worker nodes
# from a single script executed on the master.
SPARK_HOME=${SPARK_HOME:-$SPARK_HADOOP_DIR}

# Set a Scala version used by Spark
SPARK_SCALA_VERSION=${SPARK_SCALA_VERSION:-"2.11"}

NFS_SERVER=10.1.1.2
NFS_NETWORK=10.1.1.0/24
SERVER_DIR=/mnt/storage/to-share-over-nfs
MOUNT_DIR=/mnt/storage/to-share-over-nfs
