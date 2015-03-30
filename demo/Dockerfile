FROM ubuntu:utopic
RUN apt-get update
RUN apt-get install -y openjdk-8-jre-headless
# TODO workaround for nonexistence of libcurl3-gnutls_7.37.1-1ubuntu3.1_amd64.deb mentioned in first update
RUN apt-get update
RUN apt-get install -y --fix-missing openssh-server wget git
RUN echo 'root:root' | chpasswd

# installed maven package is still 3.0.5, pretty old
ENV MAVEN_VERSION 3.3.1
RUN cd /usr/local; wget -O - http://mirrors.ibiblio.org/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xvzf -

# install Jetty
WORKDIR /opt
# jetty package is still 8
ENV JETTY_VERSION 9.2.9.v20150224
RUN wget -O - "http://mirrors.ibiblio.org/eclipse/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz" | tar xvfz -
RUN ln -sv jetty-distribution-$JETTY_VERSION jetty
RUN cd /tmp; ln -s /opt/jetty/webapps

# https://github.com/carlossg/docker-maven/issues/3
RUN apt-get install -y ca-certificates-java && update-ca-certificates -f
RUN apt-get install -y openjdk-8-jdk

RUN mkdir -p /var/lib/jenkins/plugins /var/lib/jenkins/jobs/cd
# make a local checkout of the demo workspace
WORKDIR /var/lib/jenkins
# Set this before clone so if we have to go to a newer head revision, docker-build will rerun git-clone:
ENV REV 107ea141f5c7581056c6eb53d2ccd222cdf0d58c
RUN git clone https://github.com/jenkinsci/workflow-plugin-pipeline-demo && cd workflow-plugin-pipeline-demo
WORKDIR /var/lib/jenkins/workflow-plugin-pipeline-demo
# Specify a particular master revision in the Dockerfile so we can track changes (and so Docker cache works correctly):
RUN git reset --hard $REV

# Prepare local Maven repo:
RUN ln -sv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven
RUN /usr/local/maven/bin/mvn install && /usr/local/maven/bin/mvn -f sometests -Dmaven.test.failure.ignore clean install

RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Workflow Demo"

ADD jenkins-war.war /var/lib/jenkins/jenkins.war
ADD JENKINS_HOME /var/lib/jenkins/

ADD run.sh /var/lib/jenkins/run.sh
ADD sshd_config /etc/ssh/sshd_config

CMD /var/lib/jenkins/run.sh
EXPOSE 8080 8081 22
