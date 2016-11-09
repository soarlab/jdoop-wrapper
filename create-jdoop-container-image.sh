#!/usr/bin/env bash

# Stop on an error
set -e

# Run a command in the new container
in_container() {
    sudo lxc-attach --name $NEW -- "$@"
}

[ -z "$1" ] && echo "A base container name missing" && exit 1
[ -z "$2" ] && echo "A destination container name missing" && exit 1
[ -z "$Z3_DIR" ] && echo "The Z3_DIR environment variable wasn't passed in" && exit 1

BASE=$1
sudo lxc-info --name $BASE --state > /dev/null

NEW=$2

LXC_Z3=/z3
# TODO: This mounting will not work. Mounting works only in ephemeral
# containers, but I can't make copies of ephemeral containers later
# on. Instead, put Z3 binary packages to soarlab.org/files and
# download them from there in JDoop's env/install-dep.sh script.
sudo lxc-copy --name $BASE --newname $NEW --mount bind=${Z3_DIR}:${LXC_Z3}:ro
sudo lxc-start --name $NEW

PKGS="sudo \
      fakeroot \
      build-essential \
      git \
      mercurial \
      ant \
      ant-optional \
      openjdk-8-jre \
      openjdk-8-jre-headless \
      openjdk-8-jdk \
      antlr3 \
      libguava-java \
      python \
      maven \
      wget \
      unzip \
      libgomp1"

LXC_USER=debian
# Make sure networking works properly
in_container /etc/init.d/networking restart
sudo lxc-info --name $NEW 2>&1 > /dev/null
# Because we are running a Debian Snapshot Archive image, it might be
# unsafe to use it due to outdated package versions. Ignore that
# security aspect for the sake of repeatability.
in_container apt-get -o Acquire::Check-Valid-Until=false update
in_container apt-get install --yes $PKGS
in_container su -c "echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers"
in_container useradd --create-home --groups sudo --shell /bin/bash $LXC_USER

JDOOP_REPO=https://github.com/psycopaths/jdoop
JDOOP_BRANCH=custom-built-z3
LXC_HOME=/home/$LXC_USER
JDOOP_DIR=$LXC_HOME/jdoop
JDOOP_PROJECT=$LXC_HOME/jdoop-project
in_container su --login -c "git clone $JDOOP_REPO" $LXC_USER
in_container su --login -c "cd $JDOOP_DIR && git checkout $JDOOP_BRANCH" $LXC_USER
in_container su --login -c "PROJECT_ROOT=${JDOOP_PROJECT} INSTALL_PACKAGES=0 Z3_DIR=${LXC_Z3} sudo $JDOOP_DIR/env/install-dep.sh" $LXC_USER
