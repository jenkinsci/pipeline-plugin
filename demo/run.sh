#!/bin/bash
mkdir /var/run/sshd
/usr/sbin/sshd &

# TODO consider setting jetty.port=8081 and using 8080 for Jenkins
cat << EOF > /usr/bin/jetty
#/bin/bash
exec /opt/jetty/bin/jetty.sh -d supervise
EOF
chmod 755 /usr/bin/jetty

jetty &
JENKINS_HOME=/var/lib/jenkins java -jar /var/lib/jenkins/jenkins.war --httpPort=8081
