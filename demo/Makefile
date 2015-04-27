TAG=$(shell cat workflow-version.txt)
IMAGE=jenkinsci/workflow-demo

build:
	docker build -t $(IMAGE):$(TAG) .

run: build
	docker run -p 8080:8080 -p 8081:8081 -ti $(IMAGE):$(TAG)

build-snapshot: build
	[ -f ../aggregator/target/workflow-aggregator.hpi ] || mvn -f .. -DskipTests clean install
	mkdir -p snapshot/plugins
	for p in ../*/target/*.hpi; do cp -v $$p snapshot/plugins/$$(basename $${p%%.hpi}).jpi; done
	docker build -t $(IMAGE):SNAPSHOT snapshot

run-snapshot: build-snapshot
	docker run -p 8080:8080 -p 8081:8081 -ti $(IMAGE):SNAPSHOT

clean:
	rm -rf snapshot/plugins

push:
	docker push $(IMAGE):$(TAG)
	echo "consider also: docker tag -f $(IMAGE):$(TAG) $(IMAGE):latest"
