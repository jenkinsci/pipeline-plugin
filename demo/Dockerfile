FROM jenkins:1.596.2

USER root

# installed maven package is still 3.0.5, pretty old
ENV MAVEN_VERSION 3.3.1
RUN cd /usr/local; wget -O - http://mirrors.ibiblio.org/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xvzf -
RUN ln -sv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven

# install Jetty
WORKDIR /opt
# jetty package is still 8
ENV JETTY_VERSION 9.2.9.v20150224
RUN wget -O - "http://mirrors.ibiblio.org/eclipse/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz" | tar xvfz -
RUN ln -sv jetty-distribution-$JETTY_VERSION jetty
RUN cd /tmp; ln -s /opt/jetty/webapps

# TODO why is it not in the package list by default?
RUN apt-get update
RUN apt-get install -y openssh-server
RUN echo 'jenkins:jenkins' | chpasswd

ADD repo /tmp/files/repo
RUN chown -R jenkins.jenkins /tmp/files
USER jenkins
WORKDIR /tmp/files/repo
RUN git config --global user.email "demo@jenkins-ci.org" && git config --global user.name "Workflow Demo" && git init && git add . && git commit -m 'demo'

# Prepare local Maven repo. Note that $JENKINS_HOME is a volume so we cannot populate it now.
RUN echo '<settings><localRepository>/usr/share/jenkins/ref/.m2/repository</localRepository></settings>' > /tmp/files/settings.xml
RUN /usr/local/maven/bin/mvn -s /tmp/files/settings.xml install && /usr/local/maven/bin/mvn -s /tmp/files/settings.xml -f sometests -Dmaven.test.failure.ignore clean install

ENV JENKINS_UC http://jenkins-updates.cloudbees.com
COPY workflow-version.txt plugins.txt /tmp/files/
RUN sed -i "s/@VERSION@/`cat /tmp/files/workflow-version.txt`/g" /tmp/files/plugins.txt
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD JENKINS_HOME /usr/share/jenkins/ref

USER root
COPY run.sh /usr/local/bin/run.sh
RUN chmod a+x /usr/local/bin/run.sh
CMD /usr/local/bin/run.sh

EXPOSE 8080 8081
