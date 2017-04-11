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


WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"
. ${WRAPPER_HOME}/spark/my-env.sh

hostname=$(hostname)
if [ "$hostname" != "${SPARK_MASTER_HOST}" ]; then
    echo "This script must be executed on the master node!"
    exit 1
fi

${WRAPPER_HOME}/spark/prepare-master.sh &

nodes=$(cat ${WRAPPER_HOME}/spark/conf/slaves | grep "node-")

for node in $nodes; do
    ssh $node ${WRAPPER_HOME}/spark/prepare-worker-node.sh &
done
