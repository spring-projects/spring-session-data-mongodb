#!/bin/bash

set -euo pipefail

[[ -d $PWD/maven && ! -d $HOME/.m2 ]] && ln -s $PWD/maven $HOME/.m2

rm -rf $HOME/.m2/repository/org/springframework/ws 2> /dev/null || :

cd spring-session-data-mongodb-github

./mvnw -P${PROFILE} clean dependency:list test -Dsort
