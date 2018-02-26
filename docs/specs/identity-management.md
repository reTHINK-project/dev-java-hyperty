## Identity management

Users may use existing identities from Google, Facebook or others, as well as create a new account.

*todo: evaluate https://github.com/coreos/dex by using OpenLdap for new account management: https://github.com/coreos/dex/blob/master/Documentation/connectors/ldap.md. Study how to provision new users with open ldap via a remote API*

Wallet is handled as a data object that is reported by a server side (vertx) Wallet Manager hyperty. The Wallet Manager is listening for new users announcements that includes the user identifiers and creates a new wallet address for them. In future the Wallet may be a new runtime core component. The Wallet Manager invites the Smart Citizen and other components (eg citizen rating ) to subscribe or advertise the new wallet address.

Since there is dependency between the citizen wallet creation and citizen rating engines, it has to be ensured the full new user setup process is fully created.
