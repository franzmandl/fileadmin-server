#!/bin/sh

readonly DATE="$(date "+%Y-%m-%d %H:%M:%S")"
readonly YYYYMMDD="${DATE:0:10}"
readonly HHMMSS="${DATE:11:8}"

cat << EOF
[
  {"label": "label1 $YYYYMMDD", "url": "url1 $*"},
  {"label": "label2 $HHMMSS", "url": "url2"}
]
EOF
