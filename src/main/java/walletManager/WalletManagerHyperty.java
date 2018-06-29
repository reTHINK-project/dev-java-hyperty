package walletManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import data_objects.DataObjectReporter;
import hyperty.AbstractHyperty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import util.DateUtils;

public class WalletManagerHyperty extends AbstractHyperty {

	private String walletsCollection = "wallets";
	private String causeWalletAddress = "wallet2bGranted";

	private static final String logMessage = "[WalletManager] ";
	private static final String smartMeterEnabled = "smartMeterEnabled";

	// supporters
	public static final String causeSupportersTotal = "causeSupportersTotal";
	public static final String causeSupportersWithSM = "causeSupportersWithSM";

	// counters
	public static final String counters = "counters";

	@Override
	public void start() {

		super.start();

		handleRequests();

		// check public wallets to be created
		JsonArray publicWallets = config().getJsonArray("publicWallets");
		if (publicWallets != null) {
			createPublicWallets(publicWallets);
		}

		eb.consumer("wallet-cause", message -> {
			JsonObject received = (JsonObject) message.body();
			String cause = received.getString("causeID");
			getCauseSupporters(cause, message);
		});

		eb.consumer("wallet-cause-read", message -> {
			JsonObject received = (JsonObject) message.body();
			System.out.println(logMessage + "wallet-cause-read():" + received);
			String walletID = received.getJsonObject("body").getString("value");
			String publicWalletAddress = getPublicWalletAddress(walletID);
			message.reply(new JsonObject().put("wallet", getPublicWallet(publicWalletAddress)));
		});

		eb.consumer("wallet-cause-transfer", message -> {
			JsonObject received = (JsonObject) message.body();
			transferToPublicWallet(received.getString("address"), received.getJsonObject("transaction"));
		});

	}

	public void createPublicWallets(JsonArray publicWallets) {

		JsonObject walletMain = new JsonObject();
		JsonArray wallets = new JsonArray();

		// create wallets
		for (Object pWallet : publicWallets) {
			JsonObject wallet = (JsonObject) pWallet;
			String address = wallet.getString("address");
			String identity = wallet.getString("identity");
			JsonObject newWallet = new JsonObject();
			newWallet.put("address", address);
			newWallet.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", identity)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", 0);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");

			// counters (by source)
			JsonObject counters = new JsonObject();
			counters.put("user-activity", 0);
			counters.put("elearning", 0);
			counters.put("checkin", 0);
			counters.put("energy-saving", 0);
			newWallet.put("counters", counters);
			wallets.add(newWallet);
		}

		// set identity
		JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("guid", "public-wallets"));
		walletMain.put("wallets", wallets);
		walletMain.put("identity", identity);
		JsonObject document = new JsonObject(walletMain.toString());
		mongoClient.save(walletsCollection, document, id -> {
			System.out.println(logMessage + "createPublicWallets(): " + document);
		});

	}

	private void createStreams() {
		JsonObject streams = config().getJsonObject("streams");

		// shops stream
		String shopsStreamAddress = streams.getString("ranking");
		create(shopsStreamAddress, new JsonObject(), false, subscriptionHandler(), readRankingHandler());
	}

	/**
	 * Handler for read requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readRankingHandler() {
		return msg -> {
			System.out.println("Handler: " + msg.body().toString());
			final String userToFind = msg.body().getString("guid");
			JsonObject response = new JsonObject();
			if (msg.body().getJsonObject("resource") != null) {

			} else {
				// mongoClient.find("wallets", new JsonObject(), res -> {
				FindOptions findOptions = new FindOptions();
				findOptions.setSort(new JsonObject().put("balance", -1));
				mongoClient.findWithOptions("wallets", new JsonObject(), findOptions, res -> {
					int ranking = 0;
					// iterate through all
					for (JsonObject entry : res.result()) {
						ranking++;
						if (entry.getJsonObject("identity").getJsonObject("userProfile")
								.getString("guid") == userToFind) {
							break;
						}
					}
					// get position of wallet with that identity ()
					response.put("ranking", ranking);
					msg.reply(response);
				});
			}
		};
	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> subscriptionHandler() {
		return msg -> {
			mongoClient.find("wallets", new JsonObject(), res -> {
				JsonArray quizzes = new JsonArray(res.result());
				// reply with elearning info
				msg.reply(quizzes);
			});
		};

	}

	/**
	 * Handle requests.
	 */
	private void handleRequests() {

		vertx.eventBus().<JsonObject>consumer(config().getString("url"), message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleRequests(): " + message.body().toString());

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
		System.out.println(logMessage + "performTransaction() \n " + transaction.toString());
		// get wallet document
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject walletInfo = res.result().get(0);

			int currentBalance = walletInfo.getInteger("balance");
			JsonObject profile = walletInfo.getJsonObject("profile");
			int transactionValue = transaction.getInteger("value");
			if (transaction.getString("source").equals("energy-saving")) {
				// TODO - update profile info (must be done before transaction or energy-saving
				// tokens will always be 0)
				profile.put(smartMeterEnabled, true);
				walletInfo.put("profile", profile);
			}

			// store transaction
			JsonArray transactions = walletInfo.getJsonArray("transactions");
			transactions.add(transaction);
			// update balance
			if (transactionValue > 0) {
				walletInfo.put("balance", currentBalance + transactionValue);
			} else {
				walletInfo.put("balance", currentBalance);
			}

			JsonObject document = new JsonObject(walletInfo.toString());

			JsonObject query = new JsonObject().put("address", walletAddress);
			mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
				System.out.println(logMessage + "Transaction added to wallet");

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
				System.out.println(logMessage + "publishing on " + toSendChanges);

				publish(toSendChanges, updateMessage);
			});
		});

	}

	JsonObject walletToReturn;

	private JsonObject getPublicWallet(String walletAddress) {
		System.out.println(logMessage + "getPublicWallet(): " + walletAddress);

		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", "public-wallets")));

		CountDownLatch readPublicWallet = new CountDownLatch(1);

		new Thread(() -> {
			// get wallets document
			mongoClient.find(walletsCollection, query, res -> {
				JsonObject result = res.result().get(0);
				JsonArray wallets = result.getJsonArray("wallets");
				System.out.println(logMessage + "getPublicWallet(): " + wallets);

				// create wallets
				for (Object pWallet : wallets) {
					// get wallet with that address
					JsonObject wallet = (JsonObject) pWallet;
					if (wallet.getString("address").equals(walletAddress)) {
						walletToReturn = wallet;
						readPublicWallet.countDown();
						return;
					}
				}

			});
		}).start();

		try {
			readPublicWallet.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return walletToReturn;
	}

	private void transferToPublicWallet(String walletAddress, JsonObject transaction) {
		System.out.println(logMessage + "updatePublicWalletBalance(): " + walletAddress);
		String source = transaction.getString("source");
		int transactionValue = transaction.getInteger("value");

		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", "public-wallets")));
		// get wallets document
		mongoClient.find(walletsCollection, query, res -> {
			JsonObject result = res.result().get(0);
			System.out.println(logMessage + "updatePublicWalletBalance(): result" + result);
			JsonArray wallets = result.getJsonArray("wallets");

			// create wallets
			for (Object pWallet : wallets) {
				// get wallet with that address
				JsonObject wallet = (JsonObject) pWallet;
				if (wallet.getString("address").equals(walletAddress)) {
					System.out.println(logMessage + "updatePublicWalletBalance(): wallet" + wallet);
					int currentBalance = wallet.getInteger("balance");
					if (transactionValue > 0) {
						wallet.put("balance", currentBalance + transactionValue);
						JsonArray transactions = wallet.getJsonArray("transactions");
						transactions.add(transaction);
					} else {
						wallet.put("balance", currentBalance);
					}

					// update counters
					JsonObject countersObj = wallet.getJsonObject(counters);
					countersObj.put(source, countersObj.getInteger(source) + transactionValue);
				}
			}

			mongoClient.findOneAndReplace(walletsCollection, query, result, id -> {
				System.out.println("[WalletManager] Transaction added to public wallet");
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
				JsonObject wallet = res.result().get(0);
				System.out.println("[WalletManager] Wallet exists: " + wallet.toString());

				performTransaction(walletAddress, transaction);

				String publicWalletAddress = wallet.getString(causeWalletAddress);
				if (publicWalletAddress != null) {
					// update wallet2bGranted balance
					transferToPublicWallet(publicWalletAddress, transaction);
				}

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
				new JsonObject().put("guid", body.getString("value")));

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

	String causeID;

	/**
	 * Create a new wallet.
	 * 
	 * @param msg
	 * @param message
	 */
	@Override
	public void handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("[WalletManager] handleCreationRequest: " + msg);
		// send message to Vertx P2P stub and wait for reply
		message.reply(msg, reply2 -> {

			System.out.println("Reply from P2P stub " + reply2.result().body().toString());

			JsonObject rep = new JsonObject(reply2.result().body().toString());

			System.out.println("rep " + rep.toString());
			// check if 200
			int code = rep.getJsonObject("body").getInteger("code");
			if (code == 200) {
				JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("guid",
						msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid")));

				mongoClient.find(walletsCollection, new JsonObject().put("identity", identity), res -> {

					if (res.result().size() == 0) {
						System.out.println("no wallet yet, creating");

						// build wallet document
						JsonObject newWallet = new JsonObject();

						String address = generateWalletAddress(msg.getJsonObject("identity"));
						newWallet.put("address", address);
						newWallet.put("identity", identity);
						newWallet.put("created", new Date().getTime());
						newWallet.put("balance", 0);
						newWallet.put("transactions", new JsonArray());
						newWallet.put("status", "active");
						// check if profile info
						JsonObject profileInfo = msg.getJsonObject("identity").getJsonObject("userProfile")
								.getJsonObject("info");
						System.out.println("[WalletManager] Profile info: " + profileInfo);
						if (profileInfo != null) {
							causeID = profileInfo.getString("cause");
							newWallet.put("profile", profileInfo);
							newWallet.put(causeWalletAddress, getPublicWalletAddress(causeID));
						} else {
							JsonObject response = new JsonObject().put("code", 400).put("reason",
									"you must provide user info (i.e. cause)");
							reply2.result().reply(response);
							return;
						}

						JsonObject document = new JsonObject(newWallet.toString());
						mongoClient.save(walletsCollection, document, id -> {
							System.out.println("[WalletManager] new wallet with ID:" + id);
							inviteObservers(address, requestsHandler(), readHandler());
						});
						JsonObject response = new JsonObject().put("code", 200).put("wallet", newWallet);
						System.out.println("wallet created, reply" + response.toString());
						reply2.result().reply(response);

					} else {
						System.out.println("[WalletManager] wallet already exists...");
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

	String causeAddress = "";

	private void getCauseSupporters(String causeID, Message<Object> message) {

		// get address for wallet with that cause
		CountDownLatch findCauseAddress = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find(walletsCollection, new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", causeID))), res -> {
						JsonObject causeWallet = res.result().get(0);
						causeAddress = causeWallet.getString("address");
						findCauseAddress.countDown();
						return;
					});
		}).start();

		try {
			findCauseAddress.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch findSupporters = new CountDownLatch(1);
		int[] causeSuporters = new int[2];

		new Thread(() -> {
			// cause supporters
			mongoClient.find(walletsCollection, new JsonObject().put(causeWalletAddress, causeAddress), res -> {
				causeSuporters[0] = res.result().size();
				for (JsonObject object : res.result()) {
					JsonObject profile = object.getJsonObject("profile");
					if (profile.containsKey(smartMeterEnabled))
						causeSuporters[1]++;

				}
				findSupporters.countDown();
			});
		}).start();

		try {
			findSupporters.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject reply = new JsonObject();
		reply.put(causeSupportersTotal, causeSuporters[0]);
		reply.put(causeSupportersWithSM, causeSuporters[1]);
		message.reply(reply);
	}

	private String getPublicWalletAddress(String causeID) {

		for (Object pWallet : config().getJsonArray("publicWallets")) {
			JsonObject wallet = (JsonObject) pWallet;
			System.out.println("getCauseAddress()" + wallet.toString());
			if (wallet.getString("identity").equals(causeID)) {
				return wallet.getString("address");
			}
		}
		return "";
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
				System.out.println("REQUESTS HANDLER reply");
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
