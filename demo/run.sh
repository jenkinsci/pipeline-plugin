#!/bin/bash

cat << EOF > /usr/bin/jetty
#/bin/bash
export JETTY_ARGS=jetty.port=8081
exec /opt/jetty/bin/jetty.sh -d supervise
EOF
chmod 755 /usr/bin/jetty
jetty &

# TODO --informative-errors does not seem to be available in this version of Git
git daemon --verbose --user=jenkins --enable=receive-pack --base-path=/tmp/files --export-all &

su -c '/usr/local/bin/jenkins.sh' jenkins
# More complicated to use a clean login environment, because then we lose ENV directives from jenkinsci/docker/Dockerfile:
#su -c 'env JENKINS_HOME=/var/jenkins_home COPY_REFERENCE_FILE_LOG=/var/log/copy_reference_file.log /usr/local/bin/jenkins.sh' - jenkins
