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

**pendingDeletes data collection**

```
{
  user:<cguid>,
  message: <deleteMsg>
}
```

**dataObjectsRegistry data collection**

```
{
  user:<cguid>,
  message: <inviteMsg>,
  subscriptions: [<cguid>]
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

1- It retrieves the deleted Data Object (`msg.body.resource = <data object url>`) from the dataObjectsRegistry data collection and for each CGUID in the `subscriptions` it stores the received delete message in the `pendingDeleted` collection and executes `processPendingDelete(deleteMsg)`. 

2- Then it removes data object message from dataObjectsRegistry data collection and it replies with 200 OK.


### Subscription handler

**handlers:** Hyperty Address /subscription.

**message:**

Forward of [Subscribe](https://rethink-project.github.io/specs/messages/data-sync-messages/#observer-subscription-request-sent-to-data-object-subscription-handler) message sent by runtime sync manager.

**logic:**

1- It queries the Data Objects Registry collection for the data object URL to be subscribed (`message.body.body.resource`), and replies with 200 OK where `reply.body.value = message.body.body.value`.

2- [Queries the runtime registry](https://github.com/reTHINK-project/dev-java-hyperty/blob/master/docs/registry.md#readstatus-from-user) about cguid status and `offlineHandler` address.
 *at this point the offlineHandler can be hardcoded as the [CRM update handler](https://github.com/reTHINK-project/dev-java-hyperty/tree/master/docs/CRM#update-tickets) but it should be provided in the metadata of the Data Objetc registered in runtime registry*

3- if `offlineHandler` exists the following msg is sent to `offlineHandler` address:

```
{
type: "update",
from: "object url",
body: {
  status: "new-participant",
  participant: <agent-hyperty-url>
  }
}
```

4- If online it executes the `processPendingSubscription(subscribeMsg)` otherwise it stores it in the pendingSubscriptions collection.

### status handler

**handler:** <runtime-address> + `/registry` e.g. `runtime://sharing-cities-dsm/registry` .

**message:**

Status event message sent by the Vertx Runtime Registry.

**logic**

For all `online` events received:

1- it checks if the CGUID is associated to any pending subscription at pendingSubscriptions collection and if yes the `processPendingSubscription(subscribeMsg)` function is executed

2- it checks if the CGUID is associated to any pending delete at pendingDeletes collection and if yes the `processPendingDelete(deleteMsg)` function is executed


### `processPendingSubscription(subscribeMsg)` 

`subscribeMsg` message is forwarded to `CGUID` and in case a 200 Ok response is received, the `subscribeMsg` is removed from pendingSubscription collection and it retrieves the subscribed Data Object from the dataObjectsRegistry data collection and the `CGUID` in added to the `subscriptions` array.

### `processPendingDelete(deleteMsg)` 

`deleteMsg` message is forwarded to `CGUID` and in case a 200 Ok response is received, it is removed  from pendingDeletes collection.
