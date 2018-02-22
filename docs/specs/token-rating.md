## Abstract Token Rating Vertx verticle

Abstract class to be extended, that implements the following generic token rating arch pattern:

![token rating arch](token_mining.png)

Config file with:

* hyperty resource where to setup announcement handlers in case the data source is dynamic eg produced by the smart citizen
* the stream address to setup the handler in case the address is static e.g. when the stream is produced via the Smart IoT.

### private addStreamHandler()

Add stream handlers and forwards it to `rate()` if rate returns a valid uint it calls `mine()` and transfers it to associated address


### private rate(data)

An empty rating engine function (separate class?) when the data evaluation in tokens is implemented according to a certain algorithm.

### private mine(int numTokens, data)

A Token miner function that generates numTokens as uint type as well as associated transaction that is stored in a DB (future in a blockchain?):

```
{
  recipient: <wallet address of the recipient>,
  source: <data stream address>,
  date: <ISO 8601 compliant>,
  value: <amount of tokens in the transaction>
  nonce: < the count of the number of performed mining transactions, starting with 0>
}
```

*reference:* https://medium.com/@codetractio/inside-an-ethereum-transaction-fa94ffca912f

### transfer(transaction)

Publishes the transaction in the wallet address stream.

### getWalletAddress()

### getSource()

### addAnnouncementHandler()

Has an handler in the announcement address specified in the config file and calls `addStreamHandler()` or `removeStreamHandler` according to received announcement events.

### private removeStreamHandler()
