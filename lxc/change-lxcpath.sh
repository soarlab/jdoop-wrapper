#!/usr/bin/env bash

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
