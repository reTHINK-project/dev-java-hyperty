## Java based Hyperties

Hyperties in java are supported by using the [Vertx framework](http://vertx.io/) and an [abstract java Hyperty verticle](abstract-hyperty.md) that implements some required features.

![Vertx Hyperties Architecture](jvm-abstract-hyperty.png)

Interoperability with javascript core runtime Hyperties are  supported by using a P2P vertx runtime protostub i.e. communication is directly established between the javascript Hyperty runtime and the JVM Hyperty runtime without using the Message Node. The P2P vertx stub uses Vertx Event BUS over Encrypted Web Sockets to connect with JVM Vertx Hyperty Runtime, making the bridge between Vertx Event BUS and Javascript Hyperty Runtime Message BUS.

### JVM Vertx Hyperties -> Javascript Hyperties

The main data flows for Vertx Hyperties Observing Data Objects reported by Javascript Hyperties, are:

![Vertx Hyperties Observing Data Objects reported by Javascript Hyperties](observer-interoperability.png)

### Javascript Hyperties -> JVM Vertx Hyperties

The main data flows for Vertx Hyperties Reporting Data Objects that are observed by Javascript Hyperties, are:

![Vertx Hyperties Reporting Data Objects that are observed by Javascript Hyperties](reporter-interoperability.png)

## Protostubs

This is a [reTHINK protostub like](https://rethink-project.github.io/specs/concepts/protofly/) component to interface with remote Hyperty Runtimes by using some messaging protocol when the connection is triggered by the Vertx Runtime. Currently, Vertx protostubs are not published in reTHINK Catalogue from where they are dynamically deployed on Vertx Hyperty Runtime. Instead, they are implemented and deployed as Vertx Verticles. *should we also have an Abstract Protostub?*

Remote addresses that are interfaced with protostubs are set as a Verticle configuration in an array. Event BUS handlers are set with these addresses to forward Vertx Hyperty messages to remote entities.

## Runtime registry

Use [Vertx service discovery API](http://vertx.io/docs/vertx-service-discovery) to register and discover Hyperties and Data Objects  using a JSON object compliant with [reTHINK Hyperty Instance Data Model](https://rethink-project.github.io/specs/datamodel/core/hyperty-registry/readme/).
