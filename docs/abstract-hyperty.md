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

### onMessage( callback )

Vertx Event BUS handler at `config.url` address to receive messages targeting the Hyperty. *Do we need to use vertx [Buffers](http://vertx.io/docs/vertx-core/java/#_buffers)?*

Messages of type create are processed by the callback setup at `onNotification`.

### onNotification( callback )

Setup the callback to process invitations to be an Observer or to be notified some existing DataObjectObserver was deleted.

### subscribe( address, handler )

Send a subscription message towards `address` with a callback that sets the handler at `<address>/changes` (ie `eventBus.sendMessage( ..)`).
