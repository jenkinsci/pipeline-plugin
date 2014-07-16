Running demo behind a proxy
--------------------
If you are running this demo behind a corporate proxy, then you must alter the docker image prior to running the continuous delivery pipeline.

* Run docker as described below:
  * `HTTP_PROXY=http://www-proxy.mycompany.com:8080 HTTPS_PROXY=http://www-proxy.mycompany.com:8080 docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti jenkinsci/workflow-demo`
  * As an alternative, you can setup proxies within the default docker file. See http://stackoverflow.com/questions/23111631/cannot-download-docker-images-behind-a-proxy for more info.
* Login as root to the demo container.
* Create the file `/root/.m2/settings.xml` with the contents:

```xml
  <proxies>
    <proxy>
      <active>true</active>
      <protocol>http</protocol>
      <host>www-proxy.mycompany.com</host>
      <port>8080</port>
      <username></username>
      <password></password>
      <nonProxyHosts>127.0.0.1|localhost|*.mycompany.com</nonProxyHosts>
    </proxy>
  </proxies>
```

* Commit the change within docker
  * Determine `CONTAINER ID`
    * `docker ps`
  * Commit the change
    * `docker commit <container_id> jenkinsci/workflow-demo:proxy`
* Stop the container
    * `docker stop <container_id>`
* Run demo using:
    * `HTTP_PROXY=http://www-proxy.mycompany.com:8080 HTTPS_PROXY=http://www-proxy.mycompany.com:8080 docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti jenkinsci/workflow-demo:proxy`

