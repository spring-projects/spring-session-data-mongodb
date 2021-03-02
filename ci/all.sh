#!/bin/bash -x

set -euo pipefail

############################
#
# Use Docker to run the same suite of tests run on Jenkins.
#
# @author Greg Turnquist
#
############################

# Primary test run
docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk8 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=convergence ci/test.sh"

# Extra test scenarios
docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk8 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=spring-next,convergence ci/test.sh"

docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk11 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=convergence ci/test.sh"
docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk11 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=spring-next,convergence ci/test.sh"

docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk15 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=convergence ci/test.sh"
docker run --rm -v $(pwd):/spring-session-data-mongodb-github -v $HOME/.m2:/tmp/jenkins-home/.m2 -it adoptopenjdk/openjdk15 /bin/bash -c "cd spring-session-data-mongodb-github ; PROFILE=spring-next,convergence ci/test.sh"
