## Java based Hyperties

Hyperties in java are currently supported by using the [Vertx framework](http://vertx.io/) and an abstract java Hyperty verticle that implements some required features.

Interoperability with javascript core runtime Hyperties are currently supported by using a P2P vertx runtime protostub i.e. communication is directly established between the javascript Hyperty runtime and the JVM Hyperty runtime without using the Message Node.

![Vertx Hyperties Architecture](jvm-abstract-hyperty.png)
