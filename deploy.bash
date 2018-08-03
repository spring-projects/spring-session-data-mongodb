#!/bin/bash -x

if [ "${CIRCLE_BRANCH}" == "master" ] || [ "${CIRCLE_BRANCH}" == "2.0.x" ]; then
	mvn -Pdistribute,snapshot,docs clean deploy
else
	echo "We only deploy 'master' branch"
fi