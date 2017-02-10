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
# along with maline.  If not, see <http://www.gnu.org/licenses/>.


# Starts an Apache Spark master and all worker/slave nodes specified
# in conf/slaves. Make sure to prepare each worker node by running the
# prepare-worker-node.sh script on it first.

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"

. ${WRAPPER_HOME}/spark/my-env.sh
cp ${WRAPPER_HOME}/spark/conf/slaves ${SPARK_HOME}/conf/
. ${SPARK_HOME}/sbin/start-all.sh
