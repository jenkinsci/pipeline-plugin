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
/usr/local/bin/jenkins.sh
