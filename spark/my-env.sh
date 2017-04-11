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


SPARK_MASTER_HOST=${SPARK_MASTER_HOST:-node-1.multinode.jpf-doop.emulab.net}

SPARK_DIR=/mnt/storage/spark
SPARK_HADOOP=spark-2.1.0-bin-hadoop2.7
SPARK_HADOOP_DIR=${SPARK_DIR}/${SPARK_HADOOP}

# This path has to be the same on the master and on all worker nodes
# in order to be able to start both the master and the worker nodes
# from a single script executed on the master.
SPARK_HOME=${SPARK_HOME:-$SPARK_HADOOP_DIR}

# Set a Scala version used by Spark
SPARK_SCALA_VERSION=${SPARK_SCALA_VERSION:-"2.11"}
