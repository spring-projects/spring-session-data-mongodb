#!/bin/bash -x

if [ "${CIRCLE_BRANCH}" == "master" ]; then
	mvn -Pdistribute,snapshot,docs clean deploy
else
	echo "We only deploy 'master' branch"
fi