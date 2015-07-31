#!/bin/bash

# TODO better to define in Dockerfile
cat << EOF > /tmp/files/jetty
#/bin/bash
export JETTY_ARGS=jetty.port=8081
exec /opt/jetty/bin/jetty.sh -d supervise
EOF
chmod 755 /tmp/files/jetty
/tmp/files/jetty &

# TODO --informative-errors does not seem to be available in this version of Git
git daemon --verbose --enable=receive-pack --base-path=/tmp/files --export-all &

/usr/local/bin/jenkins.sh
