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
        status: <ongoing/closed>,
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

**handlers:** CRM Address + `/tickets`.

**message:**

Standard create message sent to [invite Data Object observers](https://github.com/reTHINK-project/specs/blob/master/messages/data-sync-messages.md#observer-invitation).

**logic**

1- It forwards the message to all agents (`msg.to = <agent address>` and `eb.send(<cguid>, msg)` ) and add the new ticket to newTickets array.

2- The first agent executes `ticketAccepted` function: the ticket is allocated to the agent in the  `agents` collection, the ticket is removed from the pendingTickets array and a delete message is sent to all remaining invited Agents (todo: specify this new message that should be similar to delete msg used to remove user from chat). 

3- In case no agent accepts the ticket, ie a timeout message is received for all invited Agents the message is moved from newTickets array to pendingTickets array.

### Update Tickets

**handler:** CRM Address + `/tickets`.

**message:**

```javascript
{
type: "update",
identity: <identity>,
body: {
  id: <ticket id>,
  status: "closed",
  user: <user cguid>
  }
}
```

**logic**

Checks if ticket belongs to user, change its status and update collection.

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


