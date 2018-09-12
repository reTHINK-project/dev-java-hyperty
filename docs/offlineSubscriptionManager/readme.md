## Offline Subscription Manager Hyperty

Provides functionalities to support data streams synchronisation setup between peers without requiring to have both online simultaneously.


### Storage

The Hyperty handles pendingSubscriptions data collection.

```
{
  user:<cguid>,
  message: <subscribeMsg>
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

For all `live` events received it checks if the CGUID is associated to any pending subscription and if yes the `processPendingSubscription(subscribeMsg)` function is executed.

### `processPendingSubscription(subscribeMsg)` 

Subscribe message is forwarded to `subscribeMsg.to` and in case a 200 Ok response is received the `subscribeMsg` is removed from pendingSubscription collection.

