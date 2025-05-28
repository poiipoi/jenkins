# jenkins
Some jenkins infra stuffs

# Start jenkins
docker run --name jenkins-docker --rm --detach --privileged --network jenkins --network-alias docker  --volume jenkins-data:jenkins_home