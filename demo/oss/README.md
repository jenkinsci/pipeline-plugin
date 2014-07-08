Docker image for workflow demo
==============================

Run it like:

    docker run -p 8080:8080 -p 8081:8081 -p 8022:22 -ti workflow

You can login as root in the demo container via `ssh -p 8022 root@localhost`. The password is `root`.
(If you have `nsenter`, [you can use nsenter instead of ssh for a smoother demo](http://jpetazzo.github.io/2014/06/23/docker-ssh-considered-evil/)

