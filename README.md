
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/321d2b735989493ab00ab602cdcf34ef)](https://app.codacy.com/gh/felipegutierrez/explore-akka-grpc-kubernetes?utm_source=github.com&utm_medium=referral&utm_content=felipegutierrez/explore-akka-grpc-kubernetes&utm_campaign=Badge_Grade)
[![Build Status](https://travis-ci.com/felipegutierrez/explore-akka-grpc-kubernetes.svg?branch=master)](https://travis-ci.com/felipegutierrez/explore-akka-grpc-kubernetes)
[![Coverage Status](https://coveralls.io/repos/github/felipegutierrez/explore-akka-grpc-kubernetes/badge.svg?branch=master)](https://coveralls.io/github/felipegutierrez/explore-akka-grpc-kubernetes?branch=master)
[![CodeFactor](https://www.codefactor.io/repository/github/felipegutierrez/explore-akka-grpc-kubernetes/badge)](https://www.codefactor.io/repository/github/felipegutierrez/explore-akka-grpc-kubernetes)
![GitHub issues](https://img.shields.io/github/issues-raw/felipegutierrez/explore-akka-grpc-kubernetes)
![GitHub closed issues](https://img.shields.io/github/issues-closed-raw/felipegutierrez/explore-akka-grpc-kubernetes)
![Lines of code](https://img.shields.io/tokei/lines/github/felipegutierrez/explore-akka-grpc-kubernetes)

![Docker Image Size (latest by date)](https://img.shields.io/docker/image-size/felipeogutierrez/grpcservice) [felipeogutierrez/grpcservice](https://hub.docker.com/repository/docker/felipeogutierrez/grpcservice)

![Docker Image Size (latest by date)](https://img.shields.io/docker/image-size/felipeogutierrez/httptogrpc) [felipeogutierrez/httptogrpc](https://hub.docker.com/repository/docker/felipeogutierrez/httptogrpc)

# Akka gRPC Kubernetes

This is an example of an [Akka HTTP](https://doc.akka.io/docs/akka-http/current) application communicating with an [Akka gRPC](https://developer.lightbend.com/docs/akka-grpc/current/) application inside of Kubernetes. The Akka HTTP application discovers the Akka gRPC application using [Akka Discovery](https://developer.lightbend.com/docs/akka-management/current/discovery.html). It uses the `akka-dns` mechanism which relies on the `SRV` records created by kubernetes. The docker images [felipeogutierrez/grpcservice](https://hub.docker.com/repository/docker/felipeogutierrez/grpcservice) and [felipeogutierrez/httptogrpc](https://hub.docker.com/repository/docker/felipeogutierrez/httptogrpc) are available at Docker Hub. They are push automatically by Travis-CI when new code is committed to github.

### Running

Create the docker images `httptogrpc` and `grpcservice` of this project and publish them locally:
```
sbt docker:stage
sbt docker:publishLocal
docker images
```
Once minikube is running and ingress enabled with:
```
minikube start --cpus 4 --memory 8192
minikube addons enable ingress
```
The two applications can be deployed using:
```
kubectl apply -f kubernetes/grpcservice.yml
kubectl apply -f kubernetes/httptogrpc.yml
```

Verify the deployments:
```
$ kubectl get deployments
NAME                          DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
grpcservice                   1         1         1            1           40s
httptogrpc                    1         1         1            1           40s

```

There are services for both:
```
$ kubectl get services
NAME          TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
grpcservice   ClusterIP   10.106.188.203   <none>        8080/TCP   1m
httptogrpc    ClusterIP   10.103.134.197   <none>        8080/TCP   1m
```

Ingress just for the HTTP app:

```
$ kubectl get ingress
NAME         HOSTS              ADDRESS   PORTS     AGE
httptogrpc   superservice.com             80        2m
```

The HTTP application periodically hits the gRPC applicaton which you can see in the logs:

```
[INFO] [08/15/2018 07:02:51.712] [HttpToGrpc-akka.actor.default-dispatcher-4] [akka.actor.ActorSystemImpl(HttpToGrpc)] Scheduled say hello to chris
[INFO] [08/15/2018 07:02:51.730] [HttpToGrpc-akka.actor.default-dispatcher-4] [akka.actor.ActorSystemImpl(HttpToGrpc)] Scheduled say hello response Success(HelloReply(Hello, Christopher))
```

And you can send a HTTP request via `Ingress` to the `httptogrpc` application:

```
$ curl -v --header 'Host: superservice.com' $(minikube ip)/hello/donkey
> GET /hello/donkey HTTP/1.1
> Host: superservice.com
> User-Agent: curl/7.59.0
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: nginx/1.13.12
< Date: Wed, 15 Aug 2018 07:03:56 GMT
< Content-Type: text/plain; charset=UTF-8
< Content-Length: 13
< Connection: keep-alive
< 
* Connection #0 to host 192.168.99.100 left intact
Hello, donkey%
```
Alternatively, you can use the port-forward to redirect the `service/httptogrpc` to the browser on port 8080 and access from [http://127.0.0.1:8080/hello/donkey](http://127.0.0.1:8080/hello/donkey).
```
$ kubectl port-forward service/httptogrpc 8080
Forwarding from 127.0.0.1:8080 -> 8080
Forwarding from [::1]:8080 -> 8080
```

### Overview of this project in action

![Screencast of Akka-gRPC running with akka-http on top of minikube](images/akka-grpc-http-k8s.gif)

### Troubleshooting

```
docker image rm <IMAGE_ID>
sbt docker:stage
sbt docker:publishLocal
docker login --username=felipeogutierrez
docker images
docker push felipeogutierrez/httptogrpc:1.0
docker push felipeogutierrez/grpcservice:1.0
```

