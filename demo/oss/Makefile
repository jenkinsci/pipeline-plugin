ALL:	ws/plugins/workflow-aggregator.jpi
	docker build -t workflow .

ws/plugins/workflow-aggregator.jpi:	jenkins.war
	[ -d ws ] || mkdir ws
	cp init.groovy ws/init.groovy
	JENKINS_HOME=ws java -jar jenkins.war --httpPort=-1

jenkins.war:
	wget -O jenkins.war	https://updates.jenkins-ci.org/download/war/1.570/jenkins.war

