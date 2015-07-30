#!/bin/bash

mkdir /var/run/sshd
/usr/sbin/sshd &

cat << EOF > /usr/bin/jetty
#/bin/bash
export JETTY_ARGS=jetty.port=8081
exec /opt/jetty/bin/jetty.sh -d supervise
EOF
chmod 755 /usr/bin/jetty
jetty &

# TODO --informative-errors does not seem to be available in this version of Git
git daemon --verbose --user=jenkins --enable=receive-pack --base-path=/tmp/files --export-all &

/usr/local/bin/jenkins.sh
