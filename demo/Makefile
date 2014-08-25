build:	ws/plugins/workflow-aggregator.jpi
	docker build -t jenkinsci/workflow-demo .

# TODO this is quite crude, but we need to install dependencies of workflow plugins, not only ../*/target/*.jpi:
build-from-src: jenkins.war ../aggregator/work/plugins/workflow-job.jpi
	[ -d ws/plugins ] || mkdir -p ws/plugins
	cp -v ../aggregator/work/plugins/*.jpi ws/plugins
	docker build -t jenkinsci/workflow-demo-snapshot .

ws/plugins/workflow-aggregator.jpi:	jenkins.war
	[ -d ws ] || mkdir ws
	cp init.groovy ws/init.groovy
	JENKINS_HOME=ws java -jar jenkins.war --httpPort=-1

JENKINS_VERSION=$(shell perl -n -e 'if (m{<version>(.+)</version>}) {print "$$1\n"; exit}' ../pom.xml)

jenkins.war:
	mvn -U org.apache.maven.plugins:maven-dependency-plugin:2.5.1:get -Dartifact=org.jenkins-ci.main:jenkins-war:$(JENKINS_VERSION):war -Dtransitive=false
	cp -v ~/.m2/repository/org/jenkins-ci/main/jenkins-war/$(JENKINS_VERSION)/jenkins-war-$(JENKINS_VERSION).war jenkins.war

clean:
	rm -rf ws jenkins.war

push:
	docker push jenkinsci/workflow-demo
