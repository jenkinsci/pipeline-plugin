FROM jenkins:1.609.1

USER root

# installed maven package is still 3.0.5, pretty old
ENV MAVEN_VERSION 3.3.1
RUN cd /usr/local; wget -O - http://mirrors.ibiblio.org/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xvzf -
RUN ln -sv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven

# install Jetty
WORKDIR /opt
# jetty package is still 8
ENV JETTY_VERSION 9.2.12.v20150709
RUN wget -O - "http://mirrors.ibiblio.org/eclipse/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz" | tar xvfz -
RUN ln -sv jetty-distribution-$JETTY_VERSION jetty
RUN cd /tmp; ln -s /opt/jetty/webapps
RUN chown -R jenkins /opt/jetty/logs
RUN chown -R jenkins /opt/jetty/webapps

ENV JENKINS_UC http://jenkins-updates.cloudbees.com
COPY workflow-version.txt plugins.txt /tmp/files/
RUN sed -i "s/@VERSION@/`cat /tmp/files/workflow-version.txt`/g" /tmp/files/plugins.txt
USER jenkins
RUN /usr/local/bin/plugins.sh /tmp/files/plugins.txt

ADD repo /tmp/files/repo-wc
USER root
RUN chown -R jenkins.jenkins /tmp/files
USER jenkins
WORKDIR /tmp/files
RUN git init --bare repo && cd repo-wc && git init && git add . && git -c user.email=demo@jenkins-ci.org -c user.name="Workflow Demo" commit -m 'demo' && git push ../repo master

# Prepare local Maven repo. Note that $JENKINS_HOME is a volume so we cannot populate it now.
RUN echo '<settings><mirrors><mirror><id>central</id><url>http://repo.jenkins-ci.org/simple/repo1-cache/</url><mirrorOf>central</mirrorOf></mirror></mirrors><localRepository>/usr/share/jenkins/ref/.m2/repository</localRepository></settings>' > /tmp/files/settings.xml
RUN cd repo-wc && /usr/local/maven/bin/mvn -s /tmp/files/settings.xml install && /usr/local/maven/bin/mvn -s /tmp/files/settings.xml -f sometests -Dmaven.test.failure.ignore clean install

ADD JENKINS_HOME /usr/share/jenkins/ref

USER root
RUN chown -R jenkins.jenkins /usr/share/jenkins/ref
COPY run.sh jetty.sh /usr/local/bin/
RUN chmod a+x /usr/local/bin/run.sh /usr/local/bin/jetty.sh

USER jenkins
CMD /usr/local/bin/run.sh

EXPOSE 8080 8081 9418
