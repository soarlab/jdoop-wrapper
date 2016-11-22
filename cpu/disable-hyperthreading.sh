#!/usr/bin/env bash

# Disable hyperthreading on CPUs
cat /sys/devices/system/cpu/cpu*/topology/thread_siblings_list | \
awk -F, '{print $2}' | \
sort -n | \
uniq | \
( while read X ; do echo $X ; echo 0 | sudo tee /sys/devices/system/cpu/cpu$X/online ; done )
