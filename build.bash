#!/bin/bash -x

if [ "${TRAVIS_PULL_REQUEST}" = "false" -a "${TRAVIS_BRANCH}" = "master" -a "${DEPLOY}" = "true" ]; then
	mvn -Pdistribute,snapshot,docs clean deploy
else
	mvn clean dependency:list test -P${PROFILE} -Dsort
fi