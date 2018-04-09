## Abstract hyperty

An abstract Hyperty is extended by all vertx Hyperties.

Use [Verticle Configuration](http://vertx.io/docs/vertx-core/java/#_passing_configuration_to_a_verticle) to set:

* `url` hyperty url
* `identity` Identity JSON compliant [reTHINK Identity](https://rethink-project.github.io/specs/datamodel/core/user-identity/readme/) to be associated with the hyperty.
* `streams` json object identifying streams to be published by the Hyperty. Example:

```
   {
     "id": "mystream",
     "url": "stream://mydomain.com/mystream"
   }
```
### Headers

The Abstract Hyperty set the following Event Bus Message Headers (`DeliveryOptions().addHeaders(header-name,header-value)`):

* `from` with value `config().getString("url")`,
* `identity` with value `config().getString("identity")`,
* `type` with value set by the Hyperty itself e.g. `create`

### start

The Verticle `start()` where Vertx Event BUS handler at `config.url` address to receive messages targeting the Hyperty, is set. Received messages are processed by `onMessage(msg)`.

### send( address, message, reply ) function

Set `from` and `identity` headers before calling `eb.send(..)`.

### publish( address, message ) function

Set `from` and `identity` headers before calling `eb.publish(..)`.

### onMessage( msg ) function

Invitations (ie type = create and from has `/subscription`) are processed by the callback setup at `onNotification`.

### onNotification( invitationMsg ) function

Process invitations to be an Observer or notifications that some existing DataObjectObserver was deleted. By default, invitations are accepted and the `subscribe()` function is called.

If a different logic is needed this function must be overwritten.

### subscribe( address ) function

Send a subscription message towards `address` with a callback that sets the `onChanges()` as an handler at `<address>/changes` (ie `eventBus.sendMessage( ..)`).

### onChanges( changesMsg )

Function to process messages with changes on the observed Data Object subscribed with `subscribe()`. To be overwrite by classes extending the AbstractHyperty.

### create(dataObjectUrl, observers, initialData ) function

Send the following message to all `observers`:

```
  type: "create",
  from: "dataObjectUrl/subscription",
  body: { source: <hypertyUrl>, schema: <catalogueURL>, value: <initialData> }
```

Some of these observers may be "Subscription Managers" that are running in [P2P Vertx Protostubs](https://github.com/reTHINK-project/dev-protostubs/blob/develop/docs/p2p-vertx/readme.md#subscription_manager), that locally create Data Object Reporters when this message is created.

It returns a Reporter object compliant with [Syncher DataObjectReporter](https://github.com/reTHINK-project/specs/blob/master/service-framework/syncher.md) i.e. it adds a handler to `dataObjectUrl/subscription` that will fire onSubscription events.

### storage management

When Hyperty handles information to be persisted it should have handlers at each `config.streams` to process incoming `read` messages that will return the queried data.

If the read message body does not contain any `resource` field, all persisted data is returned.
