#!/usr/bin/env bash

CWD="$(cd "`dirname "$0"`"; pwd)"
$CWD/patch23_jwbf.scala /mnt/storage/sf110/23_jwbf
$CWD/patch-26_jipa.scala /mnt/storage/sf110/26_jipa
$CWD/patch-27_gangup.scala /mnt/storage/sf110/27_gangup
$CWD/patch.scala94_jclo /mnt/storage/sf110/94_jclo
