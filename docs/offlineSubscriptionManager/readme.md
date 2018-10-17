## Offline Subscription Manager Hyperty

Provides functionalities to support data streams synchronisation setup between peers without requiring to have both online simultaneously.


### Storage

The Hyperty handles two collections:

**pendingSubscriptions data collection**

```
{
  user:<cguid>,
  message: <subscribeMsg>
}
```

**dataObjectsRegistry data collection**

```
{
  user:<cguid>,
  message: <inviteMsg>
}
```

### Data Object Registration handler

**handlers:** <Hyperty Address>/register.

**message:**

Forward of [Data Object Creation Message](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#hyperty-data-object-creation) message sent by runtime sync manager.

**logic:**

Stores message at dataObjectsRegistry data collection and it replies with 200 OK.

### Data Object Unregistration handler

**handlers:** <Hyperty Address>/register.

**message:**

Forward of [Data Object Delete Message](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#delete-data-object-requested-by-reporter) message sent by runtime sync manager.

**logic:**

Removes data object message from dataObjectsRegistry data collection and it replies with 200 OK.

### Subscription handler

**handlers:** Hyperty Address /subscription.

**message:**

Forward of [Subscribe](https://rethink-project.github.io/specs/messages/data-sync-messages/#observer-subscription-request-sent-to-data-object-subscription-handler) message sent by runtime sync manager.

**logic:**

1- It queries the Data Objects Registry collection for the data object URL to be subscribed (`message.body.body.resource`), and replies with 200 OK where `reply.body.value = message.body.body.value`.

2- Queries the registry about cguid status.

3- If online it executes the `processPendingSubscription(subscribeMsg)` otherwise it stores it in the pendingSubscriptions collection.

### status handler

**handler:** <runtime>/status.

**message:**

Status event message sent by the Vertx Runtime Registry.

**logic**

For all `online` events received it checks if the CGUID is associated to any pending subscription at pendingSubscriptionReplies collection and if yes the `processPendingSubscription(subscribeMsg)` function is executed


### `processPendingSubscription(subscribeMsg)` 

Subscribe message is forwarded to `subscribeMsg.to` and in case a 200 Ok response is received it executes the `subscribeMsg` is removed from pendingSubscription collection.

