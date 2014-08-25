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

jenkins.war:
	wget -O jenkins.war	https://updates.jenkins-ci.org/download/war/1.577/jenkins.war

clean:
	rm -rf ws jenkins.war

push:
	docker push jenkinsci/workflow-demo
