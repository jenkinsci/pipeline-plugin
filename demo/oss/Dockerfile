FROM dockerfile/java
RUN curl -o /tmp/maven.tgz http://mirror.cogentco.com/pub/apache/maven/maven-3/3.2.2/binaries/apache-maven-3.2.2-bin.tar.gz
RUN cd /usr/local; tar xvzf /tmp/maven.tgz; ln -s apache-maven-3.2.2 maven; cd /usr/bin; ln -s /usr/local/maven/bin/mvn .
RUN mkdir -p /var/lib/jenkins/plugins /var/lib/jenkins/jobs/cd

# install Jetty
WORKDIR /opt
ENV VERSION 9.2.1.v20140609
RUN curl "http://mirrors.ibiblio.org/eclipse/jetty/stable-9/dist/jetty-distribution-$VERSION.tar.gz" | tar xvfz -
RUN ln -sv jetty-distribution-$VERSION jetty
RUN cd /tmp; ln -s /opt/jetty/webapps

# make a local checkout of the demo workspace
WORKDIR /var/lib/jenkins
RUN git clone --bare https://github.com/jenkinsci/workflow-plugin-pipeline-demo.git
RUN git clone workflow-plugin-pipeline-demo.git workflow-plugin-pipeline-demo; cd workflow-plugin-pipeline-demo; mvn install; cd sometests; mvn install -Dmaven.test.failure.ignore=true

# preseed data
ADD jenkins.war /var/lib/jenkins/jenkins.war
ADD ws/plugins/ /var/lib/jenkins/plugins
ADD config.xml  /var/lib/jenkins/jobs/cd/
RUN find /var/lib/jenkins/plugins/* -type d | xargs rm -rf

ADD run.sh /var/lib/jenkins/run.sh

CMD /var/lib/jenkins/run.sh
EXPOSE 8080
