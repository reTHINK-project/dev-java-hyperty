package walletManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import data_objects.DataObjectReporter;
import hyperty.AbstractHyperty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtils;

public class WalletManagerHyperty extends AbstractHyperty {

	private String walletsCollection = "wallets";

	@Override
	public void start() {

		System.out.println("Handling requests");
		handleRequests();

		super.start();
	}

	/**
	 * Handle requests.
	 */
	private void handleRequests() {

		vertx.eventBus().<JsonObject>consumer(config().getString("url"), message -> {
			mandatoryFieldsValidator(message);
			System.out.println("Message received: " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "delete":
				walletDelete(msg);
				break;
			case "create":
				if (msg.getJsonObject("body") == null) {
					// Wallet creation requests
					handleCreationRequest(msg, message);
				} else {
					// Wallet transfer
					handleTransfer(msg);
				}
				break;
			case "read":
				JsonObject body = msg.getJsonObject("body");
				final String resource = body.getString("resource");
				System.out.println("Resource is " + resource);
				switch (resource) {
				case "user":
					// Wallet address request
					walletAddressRequest(msg, message);
					break;
				case "wallet":
					// Wallet read
					walletRead(msg, message);
					break;
				default:
					break;
				}

				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * It checks there is wallet for the identity and deletes from the storage.
	 * 
	 * @param msg
	 */
	private void walletDelete(JsonObject msg) {
		System.out.println("Deleting wallet");

		JsonObject query = new JsonObject();
		query.put("identity", msg.getJsonObject("identity"));

		mongoClient.removeDocument(walletsCollection, query, res -> {
			System.out.println("Wallet removed from DB");
		});

	}

	private void changeWalletStatus(JsonObject wallet, String status) {
		wallet.put("status", status);
		JsonObject document = new JsonObject(wallet.toString());

		JsonObject query = new JsonObject().put("identity", wallet.getString("identity"));
		mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
			System.out.println("Document with ID:" + id + " was updated");
		});
	}

	/**
	 * Add new transfer to a wallet.
	 * 
	 * @param msg
	 */
	@Override
	public void handleTransfer(JsonObject msg) {

		System.out.println("Transfer op");
		JsonObject body = msg.getJsonObject("body");
		String walletAddress = body.getString("resource").split("/")[1];
		JsonObject transaction = body.getJsonObject("value");

		validateTransaction(transaction, walletAddress);

	}

	public void inviteObservers(String dataObjectUrl, Handler<Message<JsonObject>> subscriptionHandler,
			Handler<Message<JsonObject>> readHandler) {
		// An invitation is sent to config.observers
		DataObjectReporter reporter = create(dataObjectUrl, new JsonObject(), true, subscriptionHandler, readHandler);
		reporter.setMongoClient(mongoClient);
		// pass handler function that will handle subscription events
		// reporter.setSubscriptionHandler(requestsHandler);
		// reporter.setReadHandler(readHandler);
	}

	private void performTransaction(String walletAddress, JsonObject transaction) {
		System.out.println("Transaction valid" + "to" + walletAddress);
		// get wallet document
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject walletInfo = res.result().get(0);

			int currentBalance = walletInfo.getInteger("balance");
			int transactionValue = transaction.getInteger("value");

			// store transaction
			JsonArray transactions = walletInfo.getJsonArray("transactions");
			transactions.add(transaction);
			// update balance
			if (transactionValue > 0 ) {
				walletInfo.put("balance", currentBalance + transactionValue);
			} else {
				walletInfo.put("balance", currentBalance);
			}
			
			JsonObject document = new JsonObject(walletInfo.toString());

			JsonObject query = new JsonObject().put("address", walletAddress);
			mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
				System.out.println("Transaction added to wallet");

				// send wallet update
				JsonObject updateMessage = new JsonObject();
				updateMessage.put("type", "update");
				updateMessage.put("from", url);
				updateMessage.put("to", walletAddress + "/changes");
				JsonObject updateBody = new JsonObject();
				updateBody.put("balance", walletInfo.getInteger("balance"));
				updateBody.put("transactions", walletInfo.getJsonArray("transactions"));
				updateMessage.put("body", updateBody);

				// publish transaction in the event bus using the wallet address.
				String toSendChanges = walletAddress + "/changes";
				System.out.println("PUBLISHING ON " + toSendChanges + "\nData:" + updateMessage.toString());

				publish(toSendChanges, updateMessage);
			});
		});

	}

	CompletableFuture<Boolean> result = new CompletableFuture<>();

	private void validateTransaction(JsonObject transaction, String walletAddress) {

		System.out.println("Validating " + transaction.toString());

		// check the fields themselves
		if (!transaction.containsKey("recipient") || !transaction.containsKey("source")
				|| !transaction.containsKey("date") || !transaction.containsKey("value")
				|| !transaction.containsKey("nonce")) {
			System.out.println("Invalid");
		}

		// check date validity
		if (!DateUtils.validateDate(transaction.getString("date"))) {
			System.out.println("Invalid date format");
		}

		// check tokens amount
		if (transaction.getInteger("value") <= 0) {
			System.out.println("Transaction value must be greater than 0");
		}

		// check if wallet address exists
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			if (!res.succeeded()) {
				System.out.println("Wallet does not exist");
				// return false
			} else {
				System.out.println("Wallet exists");

				performTransaction(walletAddress, transaction);

				// // check if nonce is repeated
				// JsonObject wallet = res.result().get(0);
				// JsonArray transactions = wallet.getJsonArray("transactions");
				//
				// ArrayList<JsonObject> a = (ArrayList<JsonObject>) transactions.getList();
				// List<JsonObject> repeatedNonces = (List<JsonObject>) a.stream() // convert
				// list to stream
				// .filter(element -> transaction.getInteger("nonce") ==
				// element.getInteger("nonce"))
				// .collect(Collectors.toList());
				//
				// if (repeatedNonces.size() > 0) {
				// // nonce is repeated
				//
				// }
			}

		});

	}

	/**
	 * Return wallet address for a user.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletAddressRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("Getting wallet address  msg:" + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", body.getString("value")));

		JsonObject toSearch = new JsonObject().put("identity", identity);

		System.out.println("Search on " + this.collection + "  with data" + toSearch.toString());

		mongoClient.find(this.collection, toSearch, res -> {
			if (res.result().size() != 0) {
				JsonObject walletInfo = res.result().get(0);
				// reply with address
				System.out.println("Returned wallet: " + walletInfo.toString());
				message.reply(walletInfo);
			}
		});

	}

	/**
	 * Return wallet.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletRead(JsonObject msg, Message<JsonObject> message) {
		System.out.println("Getting wallet by address");
		JsonObject body = msg.getJsonObject("body");
		String walletAddress = body.getString("value");

		JsonObject walletInfo = new JsonObject();

		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject wallet = res.result().get(0);
			message.reply(wallet.toString());
		});

	}

	/**
	 * Create a new wallet.
	 * 
	 * @param msg
	 * @param message
	 */
	@Override
	public void handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("Creating wallet: " + msg);
		/*
		 * Before the wallet is created, it checks there is no wallet yet for the
		 * identity.
		 */
		String to = msg.getString("from");
		// send message to Vertx P2P stub and wait for reply
		
		// TODO change msg
		message.reply(msg, reply2 -> {
			
			System.out.println("Reply from P2P stub " + reply2.result().body().toString());

			JsonObject rep = new JsonObject(reply2.result().body().toString());

			System.out.println("rep " + rep.toString());
			// check if 200
			int code = rep.getJsonObject("body").getInteger("code");
			if (code == 200) {
				mongoClient.find(walletsCollection, new JsonObject().put("identity", msg.getJsonObject("identity")),
						res -> {

							if (res.result().size() == 0) {
								System.out.println("no wallet yet, creating");

								// build wallet document
								JsonObject newWallet = new JsonObject();

								String address = generateWalletAddress(msg.getJsonObject("identity"));
								newWallet.put("address", address);
								newWallet.put("identity", msg.getJsonObject("identity"));
								newWallet.put("created", new Date().getTime());
								newWallet.put("balance", rep.getJsonObject("body").getJsonObject("value").getInteger("balance"));
								newWallet.put("transactions", new JsonArray());
								newWallet.put("status", "active");

								JsonObject document = new JsonObject(newWallet.toString());

								mongoClient.save(walletsCollection, document, id -> {
									System.out.println("New wallet with ID:" + id);

									inviteObservers(address, requestsHandler(), readHandler());
								});
								JsonObject response = new JsonObject().put("code", 200).put("wallet", newWallet);
								// JsonObject response = new JsonObject().put("body", body);
								System.out.println("wallet created, reply" + response.toString());
								reply2.result().reply(response);

							} else {
								System.out.println("wallet already exists...");
								JsonObject wallet = res.result().get(0);
								JsonObject response = new JsonObject().put("code", 200).put("wallet", wallet);
								// check its status
								switch (wallet.getString("status")) {
								case "active":
									System.out.println("... and is active.");
									break;
								case "deleted":
									System.out.println("... and was deleted, activating");
									changeWalletStatus(wallet, "active");
									// TODO send error back
									break;

								default:
									break;
								}
								reply2.result().reply(response);

							}
						});
			}
		});

	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> requestsHandler() {
		return msg -> {
			System.out.println("REQUESTS HANDLER: " + msg.body().toString());
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", "");
			response.put("to", msg.body().getString("from"));
			JsonObject sendMsgBody = new JsonObject();
			if (validateSource(from, msg.body().getString("address"), msg.body().getJsonObject("identity"),
					walletsCollection)) {
				sendMsgBody.put("code", 200);
				response.put("body", sendMsgBody);
				msg.reply(response);
			} else {
				sendMsgBody.put("code", 403);
				response.put("body", sendMsgBody);
				msg.reply(response);
			}

		};

	}

	/**
	 * Handler for read requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readHandler() {
		return msg -> {
			System.out.println("READ HANDLER: " + msg.body().toString());
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", "");
			response.put("to", msg.body().getString("from"));

			JsonObject sendMsgBody = new JsonObject();
			if (!validateSource(from, msg.body().getString("address"), msg.body().getJsonObject("identity"),
					walletsCollection)) {
				sendMsgBody.put("code", 403);
				response.put("body", sendMsgBody);
				msg.reply(response);
			}

			mongoClient.find(walletsCollection, new JsonObject().put("identity", identity), res -> {
				JsonObject wallet = res.result().get(0);
				System.out.println(wallet);

				sendMsgBody.put("code", 200).put("wallet", wallet);
				response.put("body", sendMsgBody);
				msg.reply(response);
			});

		};

	}

	/**
	 * The Wallet Address is generated by using some crypto function that uses the
	 * identity GUID as seed and returned.
	 * 
	 * @param jsonObject
	 * @return wallet address
	 */
	private String generateWalletAddress(JsonObject jsonObject) {
		System.out.println("JSON is " + jsonObject);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed1 = digest.digest(jsonObject.toString().getBytes());
			return new String(hashed1);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

}
