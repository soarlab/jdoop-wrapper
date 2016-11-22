#!/usr/bin/env bash

WRAPPER_HOME="$(cd "`dirname "$0"`"/..; pwd)"

. ${WRAPPER_HOME}/spark/my-env.sh
${SPARK_HOME}/sbin/stop-all.sh
