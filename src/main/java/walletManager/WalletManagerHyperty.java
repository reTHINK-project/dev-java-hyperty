package walletManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

import data_objects.DataObjectReporter;
import hyperty.AbstractHyperty;
import hyperty.CRMHyperty;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import util.DateUtilsHelper;

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

	// public wallets
	public static final String publicWalletsOnChangesAddress = "wallet://public-wallets/changes";

	public static final String publicWalletGuid = "user-guid://public-wallets";

	@Override
	public void start() {

		super.start();

		handleRequests();

		// check public wallets to be created
		JsonArray publicWallets = config().getJsonArray("publicWallets");
		int rankingTimer = config().getInteger("rankingTimer");

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
			// System.out.println(logMessage + "wallet-cause-read():" + received);
			String walletID = received.getJsonObject("body").getString("value");
			String publicWalletAddress = getPublicWalletAddress(walletID);
			message.reply(new JsonObject().put("wallet", getPublicWallet(publicWalletAddress)));
		});

		eb.consumer("wallet-cause-transfer", message -> {
			JsonObject received = (JsonObject) message.body();
			transferToPublicWallet(received.getString("address"), received.getJsonObject("transaction"));
		});

		eb.consumer("wallet-cause-reset", message -> {
			resetPublicWalletCounters();
		});

		// calc rankings every x miliseconds
		Timer timer = new Timer();
		timer.schedule(new RankingsTimer(), 0, rankingTimer);

	}

	class RankingsTimer extends TimerTask {
		public void run() {
			generateRankings();
		}
	}

	private void resetPublicWalletCounters() {
		// System.out.println(logMessage + "resetPublicWalletCounters()");

		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));
		// get wallets document
		mongoClient.find(walletsCollection, query, res -> {
			JsonObject result = res.result().get(0);
			JsonArray wallets = result.getJsonArray("wallets");

			// create wallets
			for (Object pWallet : wallets) {
				JsonObject wallet = (JsonObject) pWallet;

				// update counters
				JsonObject countersObj = wallet.getJsonObject(counters);
				String[] sources = new String[] { "user-activity", "elearning", "checkin", "energy-saving" };
				for (String source : sources) {
					countersObj.put(source, 0);
				}
			}

			mongoClient.findOneAndReplace(walletsCollection, query, result, id -> {
				// System.out.println("[WalletManager] counters reset");
			});
		});

	}

	// TODO: wait for this
	public void createPublicWallets(JsonArray publicWallets) {

		Future<Boolean> walletExists = Future.future();

		// get wallets document
		// check if public wallets already exist
		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));
		System.out.println("TESTING MONGO - 1");
		mongoClient.find(walletsCollection, query, res -> {
			System.out.println("TESTING MONGO - 2");
			JsonArray wallets = new JsonArray(res.result());
			walletExists.complete(wallets.size() != 0);
		});

		walletExists.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				JsonObject walletMain = new JsonObject();
				JsonArray wallets = new JsonArray();

				// create wallets
				for (Object pWallet : publicWallets) {
					JsonObject wallet = (JsonObject) pWallet;
					String address = wallet.getString("address");
					String identity = wallet.getString("identity");
					JsonArray externalFeeds = wallet.getJsonArray("externalFeeds");
					// TODO - For each public wallet, a new device and a new sensor is created at
					// the Smart IoT Stub
					JsonObject walletIdentity = new JsonObject().put("userProfile",
							new JsonObject().put("guid", identity));

					// System.out.println("WalletManager - create new Device " + this.siotStubUrl);

					JsonObject messageDevice = new JsonObject();
					messageDevice.put("identity", walletIdentity);
					messageDevice.put("from", this.url);
					messageDevice.put("type", "create");
					messageDevice.put("to", this.siotStubUrl);
					JsonObject bodyDevice = new JsonObject().put("resource", "device").put("name", "device Name")
							.put("description", "device description");
					messageDevice.put("body", bodyDevice);

					Future<Integer> codeDevice = smartIOTIntegration(messageDevice, this.siotStubUrl);
					codeDevice.setHandler(res -> {
						if (asyncResult.succeeded()) {
							// System.out.println("WalletManager result code " + codeDevice);
							if (codeDevice.result() == 200) {
								for (int i = 0; i < externalFeeds.size(); i++) {
									JsonObject feed = externalFeeds.getJsonObject(i);
									String platformID = feed.getString("platformID");
									String platformUID = feed.getString("platformUID");

									// System.out.println("WalletManager - create new Stream");
									JsonObject messageStream = new JsonObject();

									messageStream.put("identity", walletIdentity);
									messageStream.put("from", this.url);
									messageStream.put("type", "create");
									messageStream.put("to", this.siotStubUrl);

									JsonObject body = new JsonObject().put("resource", "stream")
											.put("name", "device Name").put("description", "device description")
											.put("platformID", platformID).put("platformUID", platformUID)
											.put("ratingType", "public");
									messageStream.put("body", body);

									Future<Integer> codeStream = smartIOTIntegration(messageStream, this.siotStubUrl);
									// System.out.println("WalletManager result code " + codeStream);

								}
							}
						} else {
							// oh ! we have a problem...
						}
					});

					JsonObject newWallet = new JsonObject();
					newWallet.put("address", address);
					newWallet.put("identity", walletIdentity);
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

				if (walletExists.result() == true) {
					return;
				}

				// set identity
				JsonObject identity = new JsonObject().put("userProfile",
						new JsonObject().put("guid", publicWalletGuid));
				walletMain.put("wallets", wallets);
				walletMain.put("identity", identity);
				walletMain.put("status", "active");
				walletMain.put("address", "public-wallets");
				JsonObject document = new JsonObject(walletMain.toString());
				mongoClient.save(walletsCollection, document, id -> {
					// System.out.println(logMessage + "createPublicWallets(): " + document);
				});
			} else {
				// oh ! we have a problem...
			}
		});

	}

	private Future<Integer> smartIOTIntegration(JsonObject message, String siotUrl) {

		Future<Integer> createDevice = Future.future();

		send(siotUrl, message, reply -> {
			// System.out.println("REP: " + reply.result().body().toString());
			int result = new JsonObject(reply.result().body().toString()).getJsonObject("body").getInteger("code");
			createDevice.complete(result);
		});

		return createDevice;

	}

	/**
	 * Generate rankings and persist them
	 */
	private void generateRankings() {

//		//System.out.println(logMessage + "generateRankings()");

		FindOptions findOptions = new FindOptions();
		findOptions.setSort(new JsonObject().put("balance", -1));
		mongoClient.findWithOptions("wallets", new JsonObject(), findOptions, res -> {
			int ranking = 0;
			// iterate through all
			for (JsonObject wallet : res.result()) {
				// discard public wallets
				if (wallet.getJsonObject("identity").getJsonObject("userProfile").getString("guid")
						.equals(publicWalletGuid)) {
					continue;
				}
				ranking++;
				int previousRanking = -1;
				if (wallet.containsKey("ranking") && wallet.getInteger("ranking") != null) {
					previousRanking = wallet.getInteger("ranking");
				}

				wallet.put("ranking", ranking);

				// publish update if ranking changed
				if (previousRanking != ranking) {
					// send wallet update
					String walletAddress = wallet.getString("address");
					JsonObject updateMessage = new JsonObject();
					updateMessage.put("type", "update");
					updateMessage.put("from", url);
					updateMessage.put("to", walletAddress + "/changes");
					JsonObject updateBody = new JsonObject();
					updateBody.put("ranking", ranking);
					updateMessage.put("body", updateBody);

					// update in Mongo
					JsonObject query = new JsonObject();
					query.put("identity", wallet.getJsonObject("identity"));
					mongoClient.findOneAndReplace(collection, query, wallet, id -> {
//						System.out.println(logMessage + "generateRankings() document updated: " + wallet);
					});

					// publish transaction in the event bus using the wallet address.
					String toSendChanges = walletAddress + "/changes";
					// System.out.println(logMessage + "publishing on " + toSendChanges);

					publish(toSendChanges, updateMessage);
				}

			}
		});
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
			// System.out.println(logMessage + "handleRequests(): " +
			// message.body().toString());

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
				// System.out.println("Resource is " + resource);
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
			case "update":
				walletUpdateResource(msg, message);
				break;
			default:
				// System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * It checks there is wallet for the address and deletes from the storage.
	 *
	 * @param msg
	 */
	private void walletDelete(JsonObject msg) {
		// System.out.println(logMessage + "walletDelete(): " + msg.toString());

		String walletAddress = msg.getJsonObject("body").getString("value");
		// System.out.println(logMessage + "removing wallet for address " +
		// walletAddress);

		JsonObject query = new JsonObject();
//		query.put("address", walletAddress);

		mongoClient.removeDocument(walletsCollection, query, res -> {
			System.out.println("Wallets removed from DB");
		});

	}

	private void changeWalletStatus(JsonObject wallet, String status) {
		wallet.put("status", status);
		JsonObject document = new JsonObject(wallet.toString());

		JsonObject query = new JsonObject().put("identity", wallet.getString("identity"));
		mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
//			System.out.println("Document with ID:" + id + " was updated");
		});
	}

	/**
	 * Add new transfer to a wallet.
	 *
	 * @param msg
	 */
	@Override
	public void handleTransfer(JsonObject msg) {

		// System.out.println(logMessage + "handleTransfer()");
		JsonObject body = msg.getJsonObject("body");
		String walletAddress = body.getString("resource").split("wallet/")[1];
		JsonObject transaction = body.getJsonObject("value");

		validateTransaction(transaction, walletAddress);
	}

	public void inviteObservers(JsonObject identity, String dataObjectUrl,
			Handler<Message<JsonObject>> subscriptionHandler, Handler<Message<JsonObject>> readHandler) {
		// An invitation is sent to config.observers
		DataObjectReporter reporter = create(identity, dataObjectUrl, new JsonObject(), true, subscriptionHandler,
				readHandler);
		reporter.setMongoClient(mongoClient);
		// pass handler function that will handle subscription events
		// reporter.setSubscriptionHandler(requestsHandler);
		// reporter.setReadHandler(readHandler);
	}

	private void performTransaction(String walletAddress, JsonObject transaction) {
		// System.out.println(logMessage + "performTransaction() \n " +
		// transaction.toString());
		// get wallet document
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject walletInfo = res.result().get(0);

			int currentBalance = walletInfo.getInteger("balance");
			int bonusCredit = walletInfo.getInteger("bonus-credit");
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
			// update bonus-credit
			walletInfo.put("bonus-credit", bonusCredit + transactionValue);
			if (!transaction.getString("source").equals("bonus") && transactionValue > 0) {
				walletInfo.put("balance", currentBalance + transactionValue);
			}

			JsonObject document = new JsonObject(walletInfo.toString());

			JsonObject query = new JsonObject().put("address", walletAddress);
			mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
				// System.out.println(logMessage + "Transaction added to wallet");

				// send wallet update
				JsonObject updateMessage = new JsonObject();
				updateMessage.put("type", "update");
				updateMessage.put("from", url);
				updateMessage.put("to", walletAddress + "/changes");
				JsonArray updateBody = new JsonArray();
				// balance
				JsonObject balance = new JsonObject();
				balance.put("value", walletInfo.getInteger("balance"));
				balance.put("attribute", "balance");
				updateBody.add(balance);
				// transaction
				JsonArray currentTransactions = walletInfo.getJsonArray("transactions");
				JsonObject transactionMsg = new JsonObject();
				transactionMsg.put("value", currentTransactions.getJsonObject(currentTransactions.size() - 1));
				transactionMsg.put("attribute", "transaction");
				updateBody.add(transactionMsg);
				// ranking
				JsonObject ranking = new JsonObject();
				ranking.put("value", walletInfo.getInteger("ranking"));
				ranking.put("attribute", "rankings");
				updateBody.add(ranking);
				// bonus-credit
				JsonObject bonusCreditMsg = new JsonObject();
				bonusCreditMsg.put("value", walletInfo.getInteger("bonus-credit"));
				bonusCreditMsg.put("attribute", "bonus-credit");
				updateBody.add(bonusCreditMsg);
				updateMessage.put("body", updateBody);

				// publish transaction in the event bus using the wallet address.
				String toSendChanges = walletAddress + "/changes";
				// System.out.println(logMessage + "publishing on " + toSendChanges);

				publish(toSendChanges, updateMessage);

				// get rankings
				// generateRankings();
			});
		});

	}

	private Future<JsonObject> getPublicWallet(String walletAddress) {
		// System.out.println(logMessage + "getPublicWallet(): " + walletAddress);

		Future<JsonObject> walletToReturn = Future.future();

		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));

		// get wallets document
		mongoClient.find(walletsCollection, query, res -> {
			JsonObject result = res.result().get(0);
			JsonArray wallets = result.getJsonArray("wallets");
			// System.out.println(logMessage + "getPublicWallet(): " + wallets);

			// create wallets
			for (Object pWallet : wallets) {
				// get wallet with that address
				JsonObject wallet = (JsonObject) pWallet;
				if (wallet.getString("address").equals(walletAddress)) {
					walletToReturn.complete(wallet);
				}
			}
		});

		return walletToReturn;
	}

	JsonObject updatedWallet;

	private void transferToPublicWallet(String walletAddress, JsonObject transaction) {
		// System.out.println(logMessage + "transferToPublicWallet(): " + walletAddress
		// + "\n" + transaction);
		String source = transaction.getString("source");
		int transactionValue = transaction.getInteger("value");

		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));
		// get wallets document
		mongoClient.find(walletsCollection, query, res -> {
			JsonObject result = res.result().get(0);
			JsonArray wallets = result.getJsonArray("wallets");

			updatedWallet = new JsonObject();
			// update wallets
			for (Object pWallet : wallets) {
				// get wallet with that address
				JsonObject wallet = (JsonObject) pWallet;
				if (wallet.getString("address").equals(walletAddress)) {

					// System.out.println(logMessage + "updatePublicWalletBalance(): wallet" +
					// wallet);
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
					if (!source.equals("created")) {
						countersObj.put(source, countersObj.getInteger(source) + transactionValue);
					}

					updatedWallet = wallet;
				}
			}

			mongoClient.findOneAndReplace(walletsCollection, query, result, id -> {
				// System.out.println("[WalletManager] Transaction added to public wallet");

				// send wallets update
				JsonObject updateMessage = new JsonObject();
				updateMessage.put("type", "update");
				updateMessage.put("from", url);
				updateMessage.put("to", walletAddress + "/changes");
				JsonArray updateBody = new JsonArray();
				JsonObject walletID = new JsonObject();
				walletID.put("value", updatedWallet.getJsonObject("identity"));
				walletID.put("attribute", "school-id");
				updateBody.add(walletID);
				JsonObject transactions = new JsonObject();
				transactions.put("value", transaction);
				transactions.put("attributeType", "array");
				transactions.put("attribute", "transactions");
				updateBody.add(transactions);
				JsonObject value = new JsonObject();
				value.put("value", updatedWallet.getInteger("balance"));
				value.put("attribute", "value");
				updateBody.add(value);
				updateMessage.put("body", updateBody);

				// publish transaction in the event bus using the wallet address.
				String toSendChanges = walletAddress + "/changes";
				// System.out.println(logMessage + "publishing on " + toSendChanges);

				publish(toSendChanges, updateMessage);
				publish(publicWalletsOnChangesAddress, updateMessage);
			});
		});

	}

	CompletableFuture<Boolean> result = new CompletableFuture<>();

	private void validateTransaction(JsonObject transaction, String walletAddress) {

		// System.out.println(logMessage + "validateTransaction() " +
		// transaction.toString());

		// check the fields themselves
		if (!transaction.containsKey("recipient") || !transaction.containsKey("source")
				|| !transaction.containsKey("date") || !transaction.containsKey("value")
				|| !transaction.containsKey("nonce")) {
			// System.out.println("Invalid");
		}

		// check date validity
		if (!DateUtilsHelper.validateDate(transaction.getString("date"))) {
			// System.out.println("Invalid date format");
		}

		// check tokens amount
		if (!transaction.getString("source").equals("bonus") && transaction.getInteger("value") <= 0) {
			// System.out.println("Transaction value must be greater than 0");
		}

		// check if wallet address exists
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			if (!res.succeeded()) {
				// System.out.println("Wallet does not exist");
				// return false
			} else {
				JsonObject wallet = res.result().get(0);
				// System.out.println(logMessage + "Wallet exists: " + wallet.toString());

				if (transaction.getString("source").equals("bonus")) {
					// check with bonus-credit field
					int walletBalance = wallet.getInteger("bonus-credit");
					int cost = transaction.getInteger("value");
					if (cost > 0) {
						transaction.put("value", 0);
					} else {
						if (walletBalance + cost < 0) {
							// System.out.println(logMessage + "insufficient funds for pick up");
							transaction.put("description", "invalid-insufficient-credits");
							transaction.put("value", 0);
						}
					}

				}

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
		// System.out.println("Getting wallet address msg:" + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("guid", body.getString("value")));

		JsonObject toSearch = new JsonObject().put("identity", identity);

		// System.out.println("Search on " + this.collection + " with data" +
		// toSearch.toString());

		mongoClient.find(this.collection, toSearch, res -> {
			if (res.result().size() != 0) {
				JsonObject walletInfo = res.result().get(0);
				// reply with address
				// System.out.println("Returned wallet: " + walletInfo.toString());
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
		JsonObject body = msg.getJsonObject("body");
		String walletAddress = body.getString("value");
		// System.out.println(logMessage + "walletRead(): getting wallet for address " +
		// walletAddress);

		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject wallet = res.result().get(0);
			// System.out.println(logMessage + "walletRead(): " + wallet);
			message.reply(wallet.toString());
		});

	}

	String causeID;

	/**
	 * let updateMessage = { type: 'forward', to:
	 * 'hyperty://sharing-cities-dsm/wallet-manager', from: _this.hypertyURL,
	 * identity: _this.identity, body: { type: 'update', from: _this.hypertyURL,
	 * resource: source, value: value } };
	 */

	private void walletUpdateResource(JsonObject msg, Message<JsonObject> message) {
		JsonObject body = msg.getJsonObject("body");
		JsonObject toUpdate = new JsonObject();
		JsonObject identity = new JsonObject().put("identity", new JsonObject().put("userProfile", new JsonObject()
				.put("guid", msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid"))));
		toUpdate.put(body.getString("resource"), body.getString("value"));
		// System.out.println("DATA TO UPDATE " + "(msg)" + msg);

		JsonObject update = new JsonObject().put("$set", toUpdate);

		// System.out.println(logMessage + " update it" + update.toString() + "\n on
		// wallet with " + identity.toString());

		mongoClient.updateCollection(this.collection, identity, update, res -> {
			// System.out.println(logMessage + " result update" + res.succeeded());
			message.reply(res.succeeded());
		});

	}

	/**
	 * Create a new wallet.
	 *
	 * @param msg
	 * @param message
	 */
	@Override
	public Future<Void> handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("[WalletManager] handleCreationRequest");
		// System.out.println("[WalletManager] handleCreationRequest: " + msg);
		// send message to Vertx P2P stub and wait for reply

		Future<Void> result = Future.future();

		message.reply(msg, reply2 -> {

			// System.out.println("Reply from P2P stub " +
			// reply2.result().body().toString());

			JsonObject rep = new JsonObject(reply2.result().body().toString());

			// System.out.println("rep " + rep.toString());
			// check if 200
			int code = rep.getJsonObject("body").getInteger("code");
			if (code == 200) {

				JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("guid",
						msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid")));

				mongoClient.find(walletsCollection, new JsonObject().put("identity", identity), res -> {

					if (res.result().size() == 0) {
						// System.out.println("no wallet yet, creating");

						String address = generateWalletAddressv2(msg.getJsonObject("identity"));
						int bal = 0;
						JsonArray transactions = new JsonArray();
						JsonObject newTransaction = new JsonObject();
						JsonObject info = msg.getJsonObject("identity").getJsonObject("userProfile")
								.getJsonObject("info");
						if (info.containsKey("balance")) {
							bal = info.getInteger("balance");

							newTransaction.put("recipient", address);
							newTransaction.put("source", "created");
							newTransaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
							newTransaction.put("value", bal);
							newTransaction.put("description", "valid");
							newTransaction.put("nonce", 1);
							JsonObject data = new JsonObject();
							data.put("created", "true");
							newTransaction.put("data", data);
							transactions.add(newTransaction);
						}
						// build wallet document
						JsonObject newWallet = new JsonObject();

						newWallet.put("address", address);
						newWallet.put("identity", identity);
						newWallet.put("created", new Date().getTime());
						newWallet.put("balance", bal);
						newWallet.put("bonus-credit", bal);
						newWallet.put("transactions", transactions);
						newWallet.put("status", "active");
						newWallet.put("ranking", 0);

						// check if profile info
						JsonObject profileInfo = msg.getJsonObject("identity").getJsonObject("userProfile")
								.getJsonObject("info");
						// System.out.println("[WalletManager] Profile info: " + profileInfo);
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
							// System.out.println("[WalletManager] new wallet with ID:" + id);
							inviteObservers(msg.getJsonObject("identity"), address, requestsHandler(), readHandler());
						});
						JsonObject response = new JsonObject().put("code", 200).put("wallet", newWallet);
						// System.out.println("wallet created, reply" + response.toString());

						if (bal > 0) {
							transferToPublicWallet(newWallet.getString(causeWalletAddress), newTransaction);
						}

						String roleCode = profileInfo.getString("code");
						if (roleCode != null) {
							// System.out.println(logMessage + "resolving role for code " + roleCode);
							Future<Integer> validateCause = Future.future();

							JsonObject validationMessage = new JsonObject();
							validationMessage.put("from", url);
							validationMessage.put("identity", identity);
							validationMessage.put("type", "forward");
							validationMessage.put("code", roleCode);
							// TODO - replace with publish
							send("resolve-role", validationMessage, reply -> {
								// System.out.println(
								// logMessage + "role validation result: " + reply.result().body().toString());
								String role = new JsonObject(reply.result().body().toString()).getString("role");
								response.put("role", role);
								validateCause.complete();
							});

							validateCause.setHandler(asyncResult -> {
								if (asyncResult.succeeded()) {
									reply2.result().reply(response);
								} else {
									// oh ! we have a problem...
								}
							});

						}

						result.complete();

					} else {
						// System.out.println("[WalletManager] wallet already exists...");
						JsonObject wallet = res.result().get(0);
						JsonObject response = new JsonObject().put("code", 200).put("wallet", wallet);
						// check its status
						switch (wallet.getString("status")) {
						case "active":
							// System.out.println("... and is active.");
							break;
						case "deleted":
							// System.out.println("... and was deleted, activating");
							changeWalletStatus(wallet, "active");
							// TODO send error back
							break;

						default:
							break;
						}
						reply2.result().reply(response);
						result.complete();

					}
				});
			}
		});

		return result;

	}

	private void getCauseSupporters(String causeID, Message<Object> message) {

		// get address for wallet with that cause
		Future<String> causeAddress = Future.future();

		new Thread(() -> {
			mongoClient.find(walletsCollection, new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", causeID))), res -> {
						JsonObject causeWallet = res.result().get(0);
						causeAddress.complete(causeWallet.getString("address"));
						return;
					});
		}).start();
		causeAddress.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				Future<Void> findSupporters = Future.future();
				int[] causeSuporters = new int[2];

				// cause supporters
				mongoClient.find(walletsCollection, new JsonObject().put(causeWalletAddress, causeAddress.result()),
						res -> {
							causeSuporters[0] = res.result().size();
							for (JsonObject object : res.result()) {
								JsonObject profile = object.getJsonObject("profile");
								if (profile.containsKey(smartMeterEnabled))
									causeSuporters[1]++;
							}
							findSupporters.complete();
						});

				findSupporters.setHandler(res -> {
					if (res.succeeded()) {
						JsonObject reply = new JsonObject();
						reply.put(causeSupportersTotal, causeSuporters[0]);
						reply.put(causeSupportersWithSM, causeSuporters[1]);
						message.reply(reply);
					} else {
						// oh ! we have a problem...
					}
				});

			} else {
				// oh ! we have a problem...
			}
		});

	}

	private String getPublicWalletAddress(String causeID) {

		for (Object pWallet : config().getJsonArray("publicWallets")) {
			JsonObject wallet = (JsonObject) pWallet;
			// System.out.println("getCauseAddress()" + wallet.toString());
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
			// System.out.println("REQUESTS HANDLER: " + msg.body().toString());
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
				// System.out.println("REQUESTS HANDLER reply");
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
			// System.out.println("READ HANDLER: " + msg.body().toString());
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
				// System.out.println(wallet);

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
		// System.out.println("JSON is " + jsonObject);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed1 = digest.digest(jsonObject.toString().getBytes());
			return new String(hashed1);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	private String generateWalletAddressv2(JsonObject jsonObject) {
		// System.out.println("JSON is " + jsonObject);
		/**
		 * { "userProfile": {"userURL":"user://google.com/lduarte.suil@gmail.com",
		 * "guid":"user-guid://aa8ac1ae0c8d8d502f9d1bbabf569fe63ab4ae5969bab33d4af6cbe3e0ca8e0e",
		 * }}
		 */
		if (jsonObject.containsKey("userProfile")) {
			JsonObject userProfile = jsonObject.getJsonObject("userProfile");
			if (userProfile.containsKey("guid")) {
				return userProfile.getString("guid").split("user-guid://")[1];
			}
		}

		return "";
	}

}
