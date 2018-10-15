## CRM Hyperty

The CRM hyperty manages CRM Agents and forwards tickets to available Agents.

### Configuration:

* `agents`: list of JSON with agent registration codes.

`{code: '<agent code>'}`

### Storage

The Hyperty handles the `agents` data collection and associated tickets. The first time the hyperty is executed the collection is initialised based on the `config.agents` info.

```
{
    address: <url of agent's Group Chat Manager hyperty>,
    code: <code>,
    user: <cguid of the user registered with this agent address>,
    tickets: [{
        user: <cguid of user that created the ticket>
        status: <new/ongoing/closed>,
        created: <date>,
        lastModified: <data>,
        message: <received invitation msg>
        }
    }],
    openedTickets: <int>,
    status: <online/offline>
  
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

It forwards the message to all agents (`msg.to = <agent address>` and `eb.send(<cguid>, msg)` ) and add the new ticket to newTickets array.

There is a timer to process newTickets array running every X seconds (eg 300 secs) to move new Tickets to pendingTickets array, in case no agent accepts new tickets ie if `timeNow - createdData > x`.

### Update Tickets

**handler:** CRM Address + `/tickets`.

**message:**

```javascript
{
type: "update",
from: "user hyperty url",
identity: <identity>,
body: {
  status: "new-participant|closed",
  participant: <hyperty-url>
  }
}
```

**logic**

`status: "new-participant"`: checks the ticket is still in the newTickets array or pendingTickets arrays and belongs to the user then it executes the `ticketAccepted` function. Otherwise, the message is ignored.

**`ticketAccepted` function:** the ticket is allocated to the agent in the  `agents` collection, the ticket is removed from the pendingTickets or newTickets array and a delete message is sent to all remaining invited Agents. The delete message is similar to this [one](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#delete-data-object-requested-by-reporter):

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

For all `online` events received it checks if the CGUID is associated to any agent and forwards to it pending tickets. Execute funtion `ticketAccepted` for 200 ok accepting messages.


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


