#!/bin/bash

set -euo pipefail

RELEASE=$1
SNAPSHOT=$2

git branch -f release-2.0.x
git checkout release-2.0.x

# Bump up the version in pom.xml to the desired version and commit the change
./mvnw versions:set -DnewVersion=$RELEASE -DgenerateBackupPoms=false
git add .
git commit --message "Releasing Spring Session MongoDB v$RELEASE"

# Tag the release
git tag -s v$RELEASE -m "v$RELEASE"

# Bump up the version in pom.xml to the next snapshot
git checkout 2.0.x
./mvnw versions:set -DnewVersion=$SNAPSHOT -DgenerateBackupPoms=false
git add .
git commit --message "Continue development on v$SNAPSHOT"


