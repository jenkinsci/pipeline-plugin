FROM jenkins:1.642.3

USER root

# installed maven package is still 3.0.5, pretty old
ENV MAVEN_VERSION 3.3.1
RUN cd /usr/local; wget -O - http://mirrors.ibiblio.org/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xvzf -
RUN ln -sv /usr/local/apache-maven-$MAVEN_VERSION /usr/local/maven

# install Jetty
WORKDIR /opt
# jetty package is still 8
ENV JETTY_VERSION 9.2.12.v20150709
RUN wget -O - "http://archive.eclipse.org/jetty/$JETTY_VERSION/dist/jetty-distribution-$JETTY_VERSION.tar.gz" | tar xvfz -
RUN ln -sv jetty-distribution-$JETTY_VERSION jetty
RUN cd /tmp; ln -s /opt/jetty/webapps
RUN chown -R jenkins /opt/jetty/logs
RUN chown -R jenkins /opt/jetty/webapps

WORKDIR /tmp/files

# Prepare local Maven repo. Note that $JENKINS_HOME is a volume so we cannot populate it now.
RUN mkdir repo-wc
ADD repo/pom.xml repo-wc/pom.xml
ADD repo/src repo-wc/src
ADD repo/sometests repo-wc/sometests
RUN chown -R jenkins.jenkins .
USER jenkins
RUN echo '<settings><mirrors><mirror><id>central</id><url>http://repo.jenkins-ci.org/simple/repo1-cache/</url><mirrorOf>central</mirrorOf></mirror></mirrors><localRepository>/usr/share/jenkins/ref/.m2/repository</localRepository></settings>' > settings.xml
RUN /usr/local/maven/bin/mvn -s settings.xml -f repo-wc install && /usr/local/maven/bin/mvn -s settings.xml -f repo-wc/sometests -Dmaven.test.failure.ignore clean install

ENV JENKINS_UC http://jenkins-updates.cloudbees.com
COPY plugins.txt .
RUN /usr/local/bin/plugins.sh plugins.txt

# Now copy the complete repo including Pipeline script (not only the files needed to warm up the Maven cache).
RUN rm -rf repo-wc
ADD repo repo-wc
USER root
RUN chown -R jenkins.jenkins repo-wc
USER jenkins
RUN git init --bare repo && \
    cd repo-wc && \
    git init && \
    git add . && \
    git -c user.email=demo@jenkins-ci.org -c user.name="Pipeline Demo" commit -m 'demo' && \
    git push ../repo master && \
    cd .. && \
    rm -rfv repo-wc
# TODO deletion of repo-wc does not seem to work: claims to have been deleted, but then still there in subsequent steps; anyway it is small
# RUN ls -lRa; false

ADD JENKINS_HOME /usr/share/jenkins/ref

USER root
RUN chown -R jenkins.jenkins /usr/share/jenkins/ref
COPY run.sh jetty.sh /usr/local/bin/
RUN chmod a+x /usr/local/bin/run.sh /usr/local/bin/jetty.sh

USER jenkins
CMD /usr/local/bin/run.sh

EXPOSE 8080 8081 9418
