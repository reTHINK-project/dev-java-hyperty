## Abstract hyperty

An abstract Hyperty is extended by all vertx Hyperties.

Use [Verticle Configuration](http://vertx.io/docs/vertx-core/java/#_passing_configuration_to_a_verticle) to set:

* `url` hyperty url
* `identity` Identity JSON compliant [reTHINK Identity](https://rethink-project.github.io/specs/datamodel/core/user-identity/readme/) to be associated with the hyperty.
* `streams` array with addresses of streams to be published by the Hyperty

### Headers

The Abstract Hyperty set the following Event Bus Message Headers (`DeliveryOptions().addHeaders(header-name,header-value)`):

* `from` with value `config().getString("url")`,
* `identity` with value `config().getString("identity")`,
* `type` with value set by the Hyperty itself e.g. `create`

### send( address, message, reply ) function

Set `from` and `identity` headers before calling `eb.send(..)`.

### publish( address, message ) function

Set `from` and `identity` headers before calling `eb.publish(..)`.

### onMessage( callback ) function

Vertx Event BUS handler at `config.url` address to receive messages targeting the Hyperty. *Do we need to use vertx [Buffers](http://vertx.io/docs/vertx-core/java/#_buffers)?*

Messages of type create are processed by the callback setup at `onNotification`.

### onNotification( callback ) function

Setup the callback to process invitations to be an Observer or to be notified some existing DataObjectObserver was deleted.

### subscribe( address, handler ) function

Send a subscription message towards `address` with a callback that sets the handler at `<address>/changes` (ie `eventBus.sendMessage( ..)`).

### storage management

When Hyperty handles information to be persisted it should have handlers at each `config.streams` to process incoming `read` messages that will return the queried data.

If the read message body does not contain any `resource` field, all persisted data is returned.
