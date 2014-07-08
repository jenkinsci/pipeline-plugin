build:	ws/plugins/workflow-aggregator.jpi
	docker build -t jenkinsci/workflow-demo .

ws/plugins/workflow-aggregator.jpi:	jenkins.war
	[ -d ws ] || mkdir ws
	cp init.groovy ws/init.groovy
	JENKINS_HOME=ws java -jar jenkins.war --httpPort=-1

jenkins.war:
	wget -O jenkins.war	https://updates.jenkins-ci.org/download/war/1.570/jenkins.war

clean:
	rm -rf ws jenkins.war

push:
	docker push jenkinsci/workflow-demo
