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


# Disable hyperthreading on CPUs. Taken from
# https://serverfault.com/a/792264.

cat /sys/devices/system/cpu/cpu*/topology/thread_siblings_list | \
awk -F, '{print $2}' | \
sort -n | \
uniq | \
( while read X; do echo $X; echo 0 | sudo tee /sys/devices/system/cpu/cpu$X/online; done )
