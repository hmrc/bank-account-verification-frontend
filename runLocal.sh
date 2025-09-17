sbt "run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes -Dmicroservice.hosts.allowList.0=localhost -Dmicroservice.services.access-control.enabled=false"
