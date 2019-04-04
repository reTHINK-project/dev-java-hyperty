## CRM Hyperty

The CRM hyperty manages CRM Agents, forwards tickets to available Agents.

It also provides functionalities to support data streams synchronisation setup between Agents and Users without requiring to have both online simultaneously.

### Configuration:


* `agents`: list of JSON with agent registration codes.

`{code: '<agent code>'}`


### Storage

The Hyperty handles the `agents` data collection and associated tickets. The first time the hyperty is executed the collection is initialised based on the `config.agents` info.

**Agents Collection**

```
{
    address: <url of agent's Group Chat Manager hyperty>,
    code: <code>,
    user: <cguid of the user registered with this agent address>,
    tickets: [<ticketUrl>],
    openedTickets: <int>,
    status: <online/offline>
}
```

**Tickets Collection**

```
    {
        url: <data object url>,
        user: <cguid of user that created the ticket>,
        status: <new (still not accepted)|pending|ongoing (accepted by agent)/closed>,
        created: <date>,
        lastModified: <data>,
        message: <received invitation msg>,
        agent: <cguid>
    }
```

**pendingSubscriptions data collection**

```
{
  user:<cguid>,
  message: <subscribeMsg>
}
```

**pendingCancels data collection**

```
{
  agent:<cguid>,
  message: <subscribeMsg>
}
```

### resolve-role handler

**handlers:** /resolve-role

**message:**

Message containing agent code.

**logic:**

It Checks that received `code` is in the `config.agents` array and if there is still no user allocated in the `agents` collection, it returns `role: "agent"`, otherwise returns `role: "user"`.

### Agent Registration handler

**handlers:** CRM Address.

**message:**

Invitation message sent by Wallet Manager to observers.

**logic:**

It Checks that received `body.code` is in the `config.agents` array and if there is still no user allocated in the `agents` collection, it updates it the new user agent CGUID and its address.

### New Ticket handlers

**handler:** CRM Address + `/tickets`.

**message:**

Standard create message sent to [invite Data Object observers](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#observer-invitation).

**logic**

It forwards the message to all agents (`msg.to = <agent address>` and `eb.send(<cguid>, msg)` ) and adds the new ticket to the `tickets` collection (status: new).

There is a timer to process `new` tickets running every X seconds (eg 300 secs) to change their status to `pending`, in case no agent accepts new tickets ie if `timeNow - createdData > x`.

### Update Tickets

**handler:** CRM Address + `/tickets`.

**message:**

```javascript
{
type: "update",
from: "object url",
identity: <identity>,
body: {
  status: "new-participant|closed",
  participant: <hyperty-url>
  }
}
```

**logic**

`status: "new-participant"`: checks the ticket is still in the `new` or `pending` status and belongs to the user then it executes the `ticketAccepted` function. Otherwise, the message is ignored.

**`ticketAccepted` function:** the ticket is associated to the agent, its status changed to `ongoing` and a delete message is sent to all remaining invited Agents. The delete message is similar to this [one](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#delete-data-object-requested-by-reporter):

```javascript
"type" : "delete",
"from" : "CRM Address",
"to"   : "Agente Hyperty URL",
"body" : { "resource" : "<ObjectURL>" }
```

`status: "closed"`: Checks if ticket belongs to user, change its status to closed and update collection.

### status handler

**handler:** CRM Address + `/status`.

**message:**

Status event message sent by the Vertx Runtime Registry.

**logic**

For all `online` events received it checks if the CGUID is associated to any agent and if yes:

1- forwards to it pending tickets and executes funtion `ticketAccepted` for 200 ok accepting messages.

2- check if there is any pending cancel in the `pendingCancels` collection and if yes forwards to it pending cancels. In case a 200 Ok response is received it is removed from the collection.

### unregistration of Agents

*to be implemented later*

**handler:** CRM address.

**message:**

```
type: delete,
identity: <compliant with reTHINK identity model>,
from: <wallet observer hyperty address>
```

**logic**

It checks there is an Agent for the identity, changing the status to "offline" and moving its opened tickets to other agents.

