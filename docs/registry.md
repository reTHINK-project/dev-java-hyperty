## Registry

This runtime feature is responsible to keep track of the status of users (online / offline) using hyperties executed in the Vertx Runtime, based on status events published by Vertx Runtime Protostub.

### Configuration:

* `checkStatusTimer`: frequency in seconds to execute checkStatus process.

### Storage

The registry handles the registry data collection:

```javascript
{
  <cguid>: { status: "online|offline", lastModified: date}
}
```


### status handler


**handler:** <runtime-address> + `/status`.

**message:**

```JSON
{
type: 'update',
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

