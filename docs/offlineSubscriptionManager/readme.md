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

**pendingSubscriptionReplies data collection**

```
{
  user:<cguid>,
  message: <subscribeReplyMsg>
}
```

### Subscription handler

**handlers:** Hyperty Address.

**message:**

Forward of [Subscribe](https://rethink-project.github.io/specs/messages/data-sync-messages/#observer-subscription-request-sent-to-data-object-subscription-handler) message sent by runtime sync manager as specified at .

**logic:**

1- It replies with 200 OK.

2- Queries the registry about cguid status.

3- If online it executes the `processPendingSubscription(subscribeMsg)` otherwise it stores it in the pendingSubscriptions collection.

### status handler

**handler:** <runtime>/status.

**message:**

Status event message sent by the Vertx Runtime Registry.

**logic**

For all `online` events received it checks if the CGUID is associated to

1- any pending subscription at pendingSubscriptionReplies collection and if yes the `processPendingSubscription(subscribeMsg)` function is executed

2- any pending subscription reply at pendingSubscriptionReplies collection and if yes the `processPendingSubscriptionReply(subscribeReply)` function is executed


### `processPendingSubscription(subscribeMsg)` 

Subscribe message is forwarded to `subscribeMsg.to` and in case a 200 Ok response is received it executes:

1- the `subscribeMsg` is removed from pendingSubscription collection.

2- Queries the registry about cguid status.

3- If online it executes the `processPendingSubscriptionReply(subscribeReply)` otherwise it stores it in the pendingSubscriptionReplies collection.

### `processPendingSubscriptionReply(subscribeReply)` 

Reply message is forwarded to `subscribeReply.to` and the `subscribeReply` is removed from pendingSubscriptionReplies collection.
