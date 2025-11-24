sbt "runProd -Dmicroservice.hosts.allowList.1=localhost -Dauditing.consumer.baseUri.port=6001 -Dauditing.consumer.baseUri.host=localhost -Dauditing.enabled=true -Dmicroservice.services.access-control.enabled=true -Dmicroservice.services.access-control.allow-list.0=bavfe-acceptance-tests"

