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
