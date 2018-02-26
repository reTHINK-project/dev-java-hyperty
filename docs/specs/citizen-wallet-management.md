## Citizen Wallet management

Wallet is handled as a data object that is reported by a server side (vertx) Wallet Manager hyperty.

There is a Smart Citizen side hyperty that observes the Wallet to be synchronised with the wallet managed by the Vertx Wallet Manager.

The first time the user uses the App, ie there is no wallet data object observer to be resumed, a new wallet is requested to the Wallet Manager, that, if approved, will creates a new wallet DO, retrieving its address. In future the Wallet may be a new runtime core component.
