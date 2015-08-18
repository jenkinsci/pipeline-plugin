TAG=$(shell cat workflow-version.txt)
IMAGE=jenkinsci/workflow-demo
DOCKER_RUN=docker run --rm -p 8080:8080 -p 8081:8081 -p 9418:9418 -ti

build:
	docker build -t $(IMAGE):$(TAG) .

run: build
	$(DOCKER_RUN) $(IMAGE):$(TAG)

build-snapshot:
	docker build -t $(IMAGE):RELEASE .
	[ -f ../aggregator/target/workflow-aggregator.hpi ] || mvn -f .. -DskipTests clean install
	mkdir -p snapshot/plugins
	for p in ../*/target/*.hpi; do cp -v $$p snapshot/plugins/$$(basename $${p%%.hpi}).jpi; done
	docker build -t $(IMAGE):SNAPSHOT snapshot

run-snapshot: build-snapshot
	$(DOCKER_RUN) $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot/plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: make push-latest"

push-latest: push
	docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest
	docker push $(IMAGE):latest
