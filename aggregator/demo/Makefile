TAG=2.0-SNAPSHOT
IMAGE=jenkinsci/workflow-demo
DOCKER_RUN=docker run --rm -p 2222:2222 -p 8080:8080 -p 8081:8081 -p 9418:9418 -ti

build:
	docker build -t $(IMAGE):$(TAG) .

run: build
	$(DOCKER_RUN) $(IMAGE):$(TAG)

build-snapshot:
	docker build -t $(IMAGE):RELEASE .
	mkdir -p snapshot-plugins
	for p in $$(cat plugins.txt|perl -pe s/:.+//g; cat snapshot-only-plugins.txt); do echo looking for snapshot builds of $$p; for g in org/jenkins-ci/plugins org/jenkins-ci/plugins/workflow org/jenkins-ci/plugins/pipeline-stage-view; do if [ -f ~/.m2/repository/$$g/$$p/maven-metadata-local.xml ]; then cp -v $$(ls -1 ~/.m2/repository/$$g/$$p/*-SNAPSHOT/*.hpi | tail -1) snapshot-plugins/$$p.jpi; fi; done; done
	docker build -f Dockerfile-snapshot -t $(IMAGE):SNAPSHOT .

run-snapshot: build-snapshot
	$(DOCKER_RUN) $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot-plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
