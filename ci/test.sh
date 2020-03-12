#!/bin/bash

set -euo pipefail

MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/spring-session-maven-repository" ./mvnw -P${PROFILE} clean dependency:list test -Dsort -U -B
