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


# The location of rootfs for LXC containers
OLD=/var/lib/lxc
NEW=/mnt/scratch/lxc
sudo rm -rf $OLD
sudo mkdir -p $NEW
sudo ln -s $NEW $OLD

# Also change the location of LXC cache
OLDC=/var/cache/lxc
NEWC=/mnt/scratch/cache/lxc
sudo rm -rf $OLDC
sudo mkdir -p $NEWC
sudo chmod og-rwx $NEWC
sudo ln -s $NEWC $OLDC
