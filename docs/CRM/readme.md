## CRM Hyperty

The CRM hyperty manages CRM Agents and forwards tickets to available Agents.

### Configuration:

* `agents`: list of JSON with agent names and associated registration code.

`{<code>: {name: '<agent name>'}}`

### Storage

The Hyperty handles AgentsPool data collection and associated tickets. The first time the hyperty is executed the collection is initialised based on the `config.agents` info.

```
{
  <agent address>: {
    code: <code>,
    user: <cguid of the user registered with this agent address>,
    tickets: [{
      <cguid of user that created the ticket>: {
        status: <pending/ongoing/closed>,
        creation: <date>,
        lastModified: <data>,
        message: <received invitation msg>}
    }],
    openedTickets: <int>,
    status: <online/offline>
  }
}
```

### Agent Registration handler

**handlers:** CRM Address.

**message:**

Invitation message sent by Wallet Manager to observers.

**logic:**

It Checks that received `body.code` is in the `config.agents` array and if there is still no user allocated in the AgentsPool, it updates it the new user agent CGUID.

### New Ticket handlers

**handlers:** CRM Address + `/agents`.

**message:**

Standard create message sent to invite Data Object observers.

**logic**

1- It forwards the message to all agents and add the new ticket to newTickets array.

2- The first agent executes `ticketAccepted` function: the ticket is allocated to the agent in the agentsPool collection, the ticket is removed from the pendingTickets array and a delete message is sent to all remaining invited Agents (todo: specify this new message that should be similar to delete msg used to remove user from chat). 

3- In case no agent accepts the ticket, ie a timeout message is received for all invited Agents the message is moved from newTickets array to pendingTickets array.

### status handler

**handler:** <runtime>/status.

**message:**

Status event message sent by the Vertx Runtime Registry.

**logic**

For all `live` events received it checks if the CGUID is associated to any agent and forwards to it pending tickets. Execute funtion `ticketAccepted` for 200 ok accepting messages.


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

It checks there is an Agent for the identity, changing the status to "inactive" and moving its pending / opened tickets to other agents.


