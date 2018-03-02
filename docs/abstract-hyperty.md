## Abstract hyperty

An abstract Hyperty is extended by all vertx Hyperties.

Configuration:

* `url` hyperty url
* `identity` Identity JSON compliant [reTHINK Identity](https://rethink-project.github.io/specs/datamodel/core/user-identity/readme/) to be associated with the hyperty.
*

*future feature* As soon as the Hyperty is instantiated it sends a registration message to Vertx Runtime Registry.

In addition it provides the following functions:

### send( address, message, reply )

Add `config.identity` to `message.identity` before calling `eb.send(..)`.

### publish( address, message )

Add `config.identity` to `message.identity` before calling `eb.publish(..)`.

### onMessage( message )

Vertx Event BUS handler at `config.url` address to receive messages targeting the Hyperty.
