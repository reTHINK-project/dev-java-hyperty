## Registry

This runtime feature is responsible to keep track of the status of users (online / offline) using hyperties executed in the Vertx Runtime, based on status events published by Vertx Runtime Protostub.

### Configuration:

* `checkStatusTimer`: frequency in seconds to execute checkStatus process.

### Storage

The registry handles the registry data collection:

```javascript
{
  guid: <cguid>,
  status: "online|offline", 
  lastModified: long
}
```


### status handler


**handler:** <runtime-address> + `/status`.

**message:**

```javascript
{
type: "update",
body: {
  resource: <cguid>,
  status: "online|offline>"
  }
}
```

**logic**

It updates the registry collection with received info including last modified timestamp.

### checkStatus timer

This function is executed by a timer every `config.checkStatusTimer` seconds.

For each entry in the registry collection where `timeNow - lastModified > config.checkStatusTimer` it updates its status to offline, and publishes its new status (ensure this event is not processed by the registry status handler specifiec above).

### readStatus from User

**handler:** <runtime-address> + `/status`.

**message to receive request of status:**

```javascript
{
type: "read",
body: {
  resource: <cguid>
  }
}
```

**message response:**

```javascript
{
body: {
  code: 200|404,
  value: {
    guid: <cguid>,
    status: "online|offline", 
    lastModified: long
    }
  }
}
```
### create status entry


**handler:** <runtime-address> + `/status`.

**message:**

```javascript
{
type: "create",
body: {
  resource: <cguid>,
  status: "online|offline>"
  }
}
```

**logic**

It create the status entry with received info.

