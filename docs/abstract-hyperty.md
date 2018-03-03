## Abstract hyperty

An abstract Hyperty is extended by all vertx Hyperties.

Use [Verticle Configuration](http://vertx.io/docs/vertx-core/java/#_passing_configuration_to_a_verticle) to set:

* `url` hyperty url
* `identity` Identity JSON compliant [reTHINK Identity](https://rethink-project.github.io/specs/datamodel/core/user-identity/readme/) to be associated with the hyperty.

### Headers

The Abstract Hyperty set the following Event Bus Message Headers (`DeliveryOptions().addHeaders(header-name,header-value)`):

* `from` with value `config().getString("url")`,
* `identity` with value `config().getString("identity")`,
* `type` with value set by the Hyperty itself e.g. `create`

### send( address, message, reply )

Set `from` and `identity` headers before calling `eb.send(..)`.

### publish( address, message )

Set `from` and `identity` headers before calling `eb.publish(..)`.

### onMessage( message )

Vertx Event BUS handler at `config.url` address to receive messages targeting the Hyperty. *Do we need to use vertx [Buffers](http://vertx.io/docs/vertx-core/java/#_buffers)?*

### future features

**Registration**

Use [Vertx factory API](http://vertx.io/docs/vertx-core/java/#_deploying_verticles_programmatically) to register an Hyperty in a Runtime Registry using a JSON object compliant with [reTHINK Hyperty Instance Data Model](https://rethink-project.github.io/specs/datamodel/core/hyperty-registry/readme/).
As soon as the Hyperty is instantiated it sends a registration message to Vertx Runtime Registry.
