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


# Prepares a worker (slave) node for computation

set -e

# Disable the swap memory
sudo swapoff -a

PKGS="lxc scala"
sudo apt-get update && sudo apt-get install --yes $PKGS

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

# This is a mount point used for benchmarks and results
sudo chown $USER: /mnt/storage
sudo chown $USER: /mnt/scratch

$WRAPPER_HOME/lxc/change-lxcpath.sh
$WRAPPER_HOME/lxc/create-debian-container.scala
$WRAPPER_HOME/lxc/create-jdoop-container.scala stretch jdoop
$WRAPPER_HOME/spark/install-spark.sh
${WRAPPER_HOME}/spark/install-sbt.scala
$WRAPPER_HOME/cpu/disable-hyperthreading.sh || true

. ${WRAPPER_HOME}/spark/my-env.sh
cp ${WRAPPER_HOME}/spark/conf/spark-env-slave.sh ${SPARK_HOME}/conf/spark-env.sh
