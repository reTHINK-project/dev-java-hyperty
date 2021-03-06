package walletManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.Calendar;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import data_objects.DataObjectReporter;
import hyperty.AbstractHyperty;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.mongo.FindOptions;
import runHyperties.Account;
import util.DateUtilsHelper;

public class WalletManagerHyperty extends AbstractHyperty {

	private String walletsCollection = "wallets";
	private String causeWalletAddress = "wallet2bGranted";
	/**
	 * Max number of transactions returned
	 */
	private Integer onReadMaxTransactions;
	private final JsonArray accountsDefault = new JsonArray().add(new Account("elearning", "quizzes").toJsonObject())
			.add(new Account("walking", "km").toJsonObject()).add(new Account("biking", "km").toJsonObject())
			.add(new Account("checkin", "checkin").toJsonObject()).add(new Account("energy-saving", "%").toJsonObject())
			.add(new Account("e-driving", "kw/h").toJsonObject()).add(new Account("created", "wallet").toJsonObject())
			.add(new Account("feedback", "questionnaire").toJsonObject())
			.add(new Account("engagedUsers", "users").toJsonObject());

	private static final String logMessage = "[WalletManager] ";
	private static final String smartMeterEnabled = "smartMeterEnabled";

	// supporters
	public static final String causeSupportersTotal = "causeSupportersTotal";
	public static final String causeSupportersWithSM = "causeSupportersWithSM";

	// counters
	public static final String counters = "counters";

	// public wallets
	public static final String publicWalletsOnChangesAddress = "wallet://public-wallets/changes";
	public static final String publicWalletsAddress = "public-wallets";

	public static final String publicWalletGuid = "user-guid://public-wallets";

	private static final Integer MaxNumLastTransactions = 100;

	private static final String transactionsCollection = "transactions";

	private static final int initialBalance = 50;

	private static int engageRating;
	private static long challengeExpire;

	@Override
	public void start() {

		super.start();

		handleRequests();

		// check public wallets to be created
		JsonArray publicWallets = config().getJsonArray("publicWallets");
		int rankingTimer = config().getInteger("rankingTimer");
		onReadMaxTransactions = config().getInteger("onReadMaxTransactions");
		engageRating = config().getInteger("engageRating");
		challengeExpire = config().getLong("challengeExpire");

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
			logger.debug(logMessage + "wallet-cause-read():" + received);
			String walletID = received.getJsonObject("body").getString("value");
			String publicWalletAddress = getPublicWalletAddress(walletID);
			Future<JsonObject> publicWallet = getPublicWallet(publicWalletAddress);
			publicWallet.setHandler(asyncResult -> {
				// message.reply(new JsonObject().put("wallet",
				// limitTransactions(publicWallet.result())));
				message.reply(new JsonObject().put("wallet", publicWallet.result()));

			});
		});

		eb.consumer("wallet-cause-transfer", message -> {
			JsonObject received = (JsonObject) message.body();
			JsonObject transaction = received.getJsonObject("transaction");

			logger.debug(logMessage + "wallet-cause-transfer():" + received);
			Future<Void> persistFuture = persistTransaction(transaction);
			persistFuture.setHandler(asyncResult -> {		
				Long currentTime = new Date().getTime();
				logger.debug(" current:" + currentTime + "\n challengeExpire:"
						+ challengeExpire);
				if (currentTime < challengeExpire) {
					transferToPublicWallet(received.getString("address"), transaction);
				} else {
					logger.debug("challengeExpire - wallet cause transfer");
				}		
			});

		});

		eb.consumer("wallet-cause-reset", message -> {
			resetPublicWalletCounters();
		});

		// calc rankings every x miliseconds
		Timer timer = new Timer();
		timer.schedule(new RankingsTimer(), 0, rankingTimer);

		// TODO - re-create DOs for wallets
		reCreateDOs();
		updateAccountsScheduler();

	}

	private void updateAccountsScheduler() {
		Calendar calendar = Calendar.getInstance();

		// Set time of execution. Here, we have to run every day 4:20 PM; so,
		// setting all parameters.
		int updateAccountsMinutes = 0;
		int updateAccountsHour = 0;
		boolean testingSchedule = true;

		String envUpdateHour = System.getenv("SCHEDULE_HOUR");
		String envUpdateMinute = System.getenv("SCHEDULE_MINUTE");

		if (envUpdateHour != null && envUpdateMinute != null) {
			updateAccountsMinutes = Integer.parseInt(System.getenv("SCHEDULE_MINUTE"));
			updateAccountsHour = Integer.parseInt(System.getenv("SCHEDULE_HOUR"));
			System.out.println("Schedule start at:" + updateAccountsHour + ":" + updateAccountsMinutes);
			testingSchedule = false;
		}

		calendar.set(Calendar.HOUR, updateAccountsHour);
		calendar.set(Calendar.MINUTE, updateAccountsMinutes);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.AM_PM, Calendar.AM);

		Long currentTime = new Date().getTime();

		// Check if current time is greater than our calendar's time. If So,
		// then change date to one day plus. As the time already pass for
		// execution.
		if (calendar.getTime().getTime() < currentTime) {
			calendar.add(Calendar.DATE, 1);
		}

		// Calendar is scheduled for future; so, it's time is higher than
		// current time.
		long startScheduler;
		long timeBetweenEach;
		if (testingSchedule) {
			startScheduler = 5 * 60 * 1000;
			timeBetweenEach = 15 * 60 * 1000;
		} else {
			startScheduler = calendar.getTime().getTime() - currentTime;
			timeBetweenEach = 24 * 60 * 60 * 1000;
		}

		// Get an instance of scheduler
		Timer timer = new Timer();

		timer.schedule(new TimerTask() {
			public void run() {
				logger.info("updateAccountsScheduler started ..." + new Date());

				// get wallets documents
				mongoClient.find(walletsCollection, new JsonObject(), res -> {
					JsonObject result = res.result().get(0);
					List<JsonObject> wallets = res.result();

					for (Object wallet : wallets) {
						JsonObject currentWallet = (JsonObject) wallet;
						String walletAddress = currentWallet.getString("address");
						if (!(walletAddress.equals(publicWalletsAddress))) {
							JsonArray accounts = currentWallet.getJsonArray("accounts");
							for (Object account : accounts) {
								JsonObject currentAccount = (JsonObject) account;
								updateAccountLastTransactions(currentAccount, currentWallet);
							}

						} else {
							JsonObject pubDocWallet = (JsonObject) wallet;
							JsonArray pWallets = pubDocWallet.getJsonArray("wallets");
							for (Object pWallet : pWallets) {
								JsonArray accounts = ((JsonObject) pWallet).getJsonArray("accounts");
								for (Object account : accounts) {
									JsonObject currentAccount = (JsonObject) account;
									updateAccountLastTransactions(currentAccount, pubDocWallet);
								}

							}

						}

					}
					logger.info("[updateAccountsScheduler] finished ..." + new Date());

				});

			}
		}, startScheduler, timeBetweenEach);
	}

	private void reCreateDOs() {
		JsonObject query = new JsonObject();
		// get wallets document
		mongoClient.find(walletsCollection, query, res -> {
			List<JsonObject> wallets = res.result();

			for (Object pWallet : wallets) {
				JsonObject wallet = (JsonObject) pWallet;
				if (!wallet.getJsonObject("identity").getJsonObject("userProfile").getString("guid")
						.equals(publicWalletGuid)) {
					inviteObservers(wallet.getJsonObject("identity"), wallet.getString("address"), requestsHandler(),
							readHandler());
				}

			}

		});

	}

	class RankingsTimer extends TimerTask {
		public void run() {
			generateRankings();
		}
	}

	private void resetPublicWalletCounters() {
		SharedData sd = vertx.sharedData();
		logger.debug(logMessage + "resetPublicWalletCounters()");

		sd.getLockWithTimeout("mongoLock", 10000, r -> {

			if (!r.succeeded()) {
				logger.error("Error when accessing lock");
				return;
			}
			// Got the lock!
			Lock lock = r.result();

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
					logger.debug("[WalletManager] counters reset");
					lock.release();
				});
			});
		});

	}

	public void createPublicWallets(JsonArray publicWallets) {

		Future<Boolean> walletExists = Future.future();

		// get wallets document
		// check if public wallets already exist
		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));
		logger.debug("TESTING MONGO - 1");
		mongoClient.find(walletsCollection, query, res -> {
			logger.debug("TESTING MONGO - 2");
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
					JsonObject walletIdentity = new JsonObject().put("userProfile",
							new JsonObject().put("guid", identity));

					logger.debug("WalletManager - create new Device " + this.siotStubUrl);

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
							logger.debug("WalletManager result code " + codeDevice);
							if (codeDevice.result() == 200) {
								for (int i = 0; i < externalFeeds.size(); i++) {
									JsonObject feed = externalFeeds.getJsonObject(i);
									String platformID = feed.getString("platformID");
									String platformUID = feed.getString("platformUID");

									logger.debug("WalletManager - create new Stream");
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
									logger.debug("WalletManager result code " + codeStream);

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
					// TODO - removed transactions from priv wallet
					newWallet.put("transactions", new JsonArray());
					newWallet.put("status", "active");
					newWallet.put("accounts", accountsDefault.copy());

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
					logger.debug(logMessage + "createPublicWallets(): " + document);
				});
			} else {
				// oh ! we have a problem...
			}
		});

	}

	private Future<Integer> smartIOTIntegration(JsonObject message, String siotUrl) {

		Future<Integer> createDevice = Future.future();

		if (siotUrl.equals("")) {
			createDevice.complete(0);
		} else {
			send(siotUrl, message, reply -> {
				logger.debug("REP: " + reply.result().body().toString());
				int result = new JsonObject(reply.result().body().toString()).getJsonObject("body").getInteger("code");
				createDevice.complete(result);
			});
		}

		return createDevice;

	}

	/**
	 * Generate rankings and persist them
	 */
	private void generateRankings() {

		logger.debug(logMessage + "generateRankings()");

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
						logger.debug(logMessage + "generateRankings() document updated");
					});

					// publish transaction in the event bus using the wallet address.
					String toSendChanges = walletAddress + "/changes";
					logger.debug(logMessage + "publishing on " + toSendChanges);

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
			logger.debug(logMessage + "handleRequests(): " + message.body().toString());

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
				logger.debug("Resource is " + resource);
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
				logger.debug("Incorrect message type: " + msg.getString("type"));
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
		logger.debug(logMessage + "walletDelete(): " + msg.toString());

		String walletAddress = msg.getJsonObject("body").getString("value");
		if (walletAddress.equals("public-wallets")) {
			return;
		}
		logger.debug(logMessage + "removing wallet for address " + walletAddress);

		/*
		 * JsonObject query = new JsonObject(); JsonObject userProfile = new
		 * JsonObject().put("guid", "user-guid://" + walletAddress); JsonObject identity
		 * = new JsonObject().put("userProfile", userProfile); query.put("identity",
		 * identity);
		 * 
		 * 
		 * mongoClient.removeDocument(walletsCollection, query, res -> {
		 * logger.debug("Wallets removed from DB"); });
		 */

	}

	private void changeWalletStatus(JsonObject wallet, String status) {
		wallet.put("status", status);
		JsonObject document = new JsonObject(wallet.toString());

		JsonObject query = new JsonObject().put("identity", wallet.getString("identity"));
		mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
			logger.debug("Document with ID:" + id + " was updated");
		});
	}

	/**
	 * Add new transfer to a wallet.
	 *
	 * @param msg
	 */
	@Override
	public void handleTransfer(JsonObject msg) {

		logger.debug(logMessage + "handleTransfer()");
		JsonObject body = msg.getJsonObject("body");
		String userID = body.getString("resource");
		Future<JsonObject> walletAddressFut = getWalletInfo(userID);
		walletAddressFut.setHandler(asyncResult -> {

			if (asyncResult.succeeded()) {
				JsonObject walletInfo = asyncResult.result();
				JsonObject transaction = body.getJsonObject("value");
				transaction.put("recipient", walletInfo.getString("_id"));
				transaction.put("wallet2bGranted", walletInfo.getString("wallet2bGranted"));
				validateTransaction(transaction, walletInfo.getString("address"));
			}
		});

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

	Future<Void> transferToPrivateWallet(String walletAddress, JsonObject transaction) {
		logger.debug(logMessage + "transferToPrivateWallet() \n " + transaction.toString());
		// get wallet document
		Future<Void> walletToReturn = Future.future();

		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject walletInfo = res.result().get(0);

			// int currentBalance = walletInfo.getInteger("balance");
			int bonusCredit = walletInfo.getInteger("bonus-credit");
			JsonObject profile = walletInfo.getJsonObject("profile");
			int transactionValue = transaction.getInteger("value");
			if (transaction.getString("source").equals("energy-saving")) {
				// TODO - update profile info (must be done before transaction or energy-saving
				// tokens will always be 0)
				profile.put(smartMeterEnabled, true);
				walletInfo.put("profile", profile);
			}

			// insert transaction to transactionsCollection
			persistTransaction(transaction);
			Account account = null;

			logger.debug(logMessage + "transferToPrivateWallet - 1");

			String source = getSource(transaction);
			if (!source.equals("bonus") && transactionValue > 0) {

				logger.debug(logMessage + "transferToPrivateWallet - 1.1");
				walletInfo = checkEDriving(walletInfo);
				walletInfo = checkEngageRating(walletInfo);
				walletInfo = checkFeedback(walletInfo);
				account = getAccount(source, walletInfo);

				/*
				 * if (!transaction.getString("source").equals("bonus") && transactionValue > 0)
				 * {
				 * 
				 * logger.debug(logMessage + "transferToPrivateWallet - 1.1"); walletInfo =
				 * checkEDriving(walletInfo);
				 * 
				 * account = getAccount(transaction.getString("source"), walletInfo);
				 */

				logger.debug(logMessage + "transferToPrivateWallet - 2");
				account = updateAccount(account, transaction);
				logger.debug(logMessage + "transferToPrivateWallet - 4");
			}

			// update wallet
			walletInfo = updateLastTransactions(walletInfo, transaction);
			logger.debug(logMessage + "transferToPrivateWallet - 5");
			// update bonus-credit
			walletInfo.put("bonus-credit", bonusCredit + transactionValue);
			if (!transaction.getString("source").equals("bonus") && transactionValue > 0) {
				logger.debug(logMessage + "transferToPrivateWallet - 5.1");
				walletInfo = updateWalletAccounts(walletInfo, account);
				logger.debug(logMessage + "transferToPrivateWallet - 5.2");
				walletInfo = sumAccounts(walletInfo, true);
				logger.debug(logMessage + "transferToPrivateWallet - 5.3");
			}
			logger.debug(logMessage + "transferToPrivateWallet - 6");
			// update accounts
			// updateAccounts(walletInfo, false);
			final JsonObject walletResult = walletInfo;

			JsonObject document = new JsonObject(walletResult.toString());

			JsonObject query = new JsonObject().put("address", walletAddress);
			mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
				logger.debug(logMessage + "Transaction added to wallet");

				// send wallet update
				JsonObject updateMessage = new JsonObject();
				updateMessage.put("type", "update");
				updateMessage.put("from", url);
				updateMessage.put("to", walletAddress + "/changes");
				JsonArray updateBody = new JsonArray();
				// balance
				JsonObject balance = new JsonObject();
				balance.put("value", walletResult.getInteger("balance"));
				balance.put("attribute", "balance");
				updateBody.add(balance);
				// TODO - get last transaction
				JsonArray currentTransactions = walletResult.getJsonArray("transactions");
				JsonObject transactionMsg = new JsonObject();
				transactionMsg.put("value", currentTransactions.getJsonObject(currentTransactions.size() - 1));
				transactionMsg.put("attribute", "transaction");
				updateBody.add(transactionMsg);
				// accounts
				JsonArray accounts = walletResult.getJsonArray("accounts");
				JsonObject accountsMsg = new JsonObject();
				accountsMsg.put("value", accounts);
				accountsMsg.put("attribute", "accounts");
				updateBody.add(accountsMsg);
				// ranking
				JsonObject ranking = new JsonObject();
				ranking.put("value", walletResult.getInteger("ranking"));
				ranking.put("attribute", "rankings");
				updateBody.add(ranking);
				// bonus-credit
				JsonObject bonusCreditMsg = new JsonObject();
				bonusCreditMsg.put("value", walletResult.getInteger("bonus-credit"));
				bonusCreditMsg.put("attribute", "bonus-credit");
				updateBody.add(bonusCreditMsg);
				updateMessage.put("body", updateBody);

				// publish transaction in the event bus using the wallet address.
				String toSendChanges = walletAddress + "/changes";
				logger.debug(logMessage + "publishing on " + toSendChanges);

				publish(toSendChanges, updateMessage);

				walletToReturn.complete();

				// get rankings
				// generateRankings();
			});
		});

		return walletToReturn;

	}

	private Future<Void> persistTransaction(JsonObject transaction) {
		Future<Void> persistFuture = Future.future();
		mongoClient.insert(transactionsCollection, transaction, insertionResult -> {
			if (insertionResult.succeeded()) {
				logger.debug(logMessage + "new transaction added to collection");
			} else {
				logger.debug(logMessage + "error on new transaction");
			}
			persistFuture.complete();
		});
		return persistFuture;
	}

	private String getSource(JsonObject lastTransaction) {
		String source = lastTransaction.getString("source");
		if (source.equals("user-activity")) {
			String activity = lastTransaction.getJsonObject("data").getString("activity");
			if (activity.equals("user_biking_context")) {
				source = "biking";
			} else if (activity.equals("user_walking_context")) {
				source = "walking";
			} else if (activity.equals("user_e-driving_context")) {
				source = "e-driving";
			}
		} else if (source.equals("elearning")) {
			if (lastTransaction.getJsonObject("data").containsKey("activity") && lastTransaction.getJsonObject("data")
					.getString("activity").equals("user_giving_feedback_context")) {
				source = "feedback";
			} else {
				return source;
			}
		}
		return source;
	}

	private Account getAccount(String source, JsonObject wallet) {
		JsonArray accounts = wallet.getJsonArray("accounts");

		// get account for source
		List<Object> res = accounts.stream().filter(account -> ((JsonObject) account).getString("name").equals(source))
				.collect(Collectors.toList());
		System.out.println("SOurce:" + source);
		JsonObject accountJson = (JsonObject) res.get(0);
		return Account.toAccount(accountJson);

	}

	// Update total values of Account with new transaction

	private Account updateAccount(Account account, JsonObject transaction) {
		logger.debug("UpdateACCOUNT acc:" + account.toJsonObject().toString());
		logger.debug("UpdateACCOUNT tran:" + transaction.toString());
		int value = transaction.getInteger("value");

		account.totalBalance += value;
		account.lastBalance += value;
		account.lastTransactions.add(transaction.getString("_id"));

		// TODO: update to support electric cars charging
		if (transaction.getString("source").equals("energy-saving")) {
			++account.totalData;
			account.lastData = transaction.getJsonObject("data").getInteger("value");
		} else if (transaction.getString("source").equals("user-activity")) {
			if (transaction.getJsonObject("data").getString("activity").equals("user_giving_feedback_context")) {
				++account.totalData;
				++account.lastData;
			} else {
				account.totalData += transaction.getJsonObject("data").getInteger("distance");
				account.lastData += transaction.getJsonObject("data").getInteger("distance");
			}
		} else {
			++account.totalData;
			++account.lastData;
		}

		logger.debug("UpdateACCOUNT:" + account.toString());
		return account;
	}

	// Update total values of Account with new transaction

	private JsonObject updateLastTransactions(JsonObject wallet, JsonObject transaction) {
		JsonArray lastTransactions = wallet.getJsonArray("transactions");

		Boolean maxTransactions = lastTransactions.size() >= MaxNumLastTransactions ? true : false;

		// TODO: update to support energy savings and electric cars charging
		if (maxTransactions) {
			JsonArray trAux = new JsonArray();
			for (int i = 1; i < MaxNumLastTransactions; i++) {
				trAux.add(lastTransactions.getJsonObject(i));
			}
			trAux.add(transaction);
			wallet.remove("transactions");
			wallet.put("transactions", trAux);
		} else {
			lastTransactions.add(transaction);

			wallet.put("transactions", lastTransactions);
		}
		return wallet;
	}

	private JsonObject updateWalletAccounts(JsonObject wallet, Account newAccount) {

		JsonArray accounts = wallet.getJsonArray("accounts");
		JsonArray accAux = new JsonArray();

		for (Object account : accounts) {
			if (((JsonObject) account).getString("name").equals(newAccount.name)) {
				accAux.add(newAccount.toJsonObject());
			} else {
				accAux.add((JsonObject) account);
			}
		}

		wallet.put("accounts", accAux);

		return wallet;
	}

	/*
	 * private JsonObject updateAccounts(JsonObject wallet, boolean publicWallet) {
	 * JsonArray accounts = wallet.getJsonArray("accounts"); // TODO - get from
	 * transactions collection JsonArray transactions =
	 * wallet.getJsonArray("transactions"); if (transactions.size() == 1) { return
	 * wallet; } JsonObject lastTransaction =
	 * transactions.getJsonObject(transactions.size() - 1); String source =
	 * getSource(lastTransaction); if (source.equals("bonus")) { return wallet; }
	 *
	 * // transactions for this source List<Object> transactionsForSource =
	 * getTransactionsForSource(transactions, source, publicWallet); // get account
	 * for source List<Object> res = accounts.stream().filter(account ->
	 * ((JsonObject) account).getString("name").equals(source))
	 * .collect(Collectors.toList()); System.out.println("SOurce:" + source);
	 * JsonObject accountJson = (JsonObject) res.get(0); Account account =
	 * Account.toAccount(accountJson); int value =
	 * lastTransaction.getInteger("value"); account.totalBalance += value; if
	 * (!lastTransaction.getString("source").equals("user-activity")) {
	 * account.totalData += 1; } else { account.totalData +=
	 * lastTransaction.getJsonObject("data").getInteger("distance"); }
	 *
	 * JsonArray lastTransactions = (account.lastPeriod.equals("month")) ?
	 * lastMonthTransactions(transactionsForSource) :
	 * lastWeekTransactions(transactionsForSource); int lastBalance = 0; for (Object
	 * transaction : lastTransactions) { lastBalance += ((JsonObject)
	 * transaction).getInteger("value"); } account.lastBalance = lastBalance; if
	 * (!lastTransaction.getString("source").equals("user-activity")) {
	 * account.lastData = lastTransactions.size(); } else { int lastData = 0; for
	 * (Object transaction : lastTransactions) { lastData += ((JsonObject)
	 * transaction).getJsonObject("data").getInteger("distance"); } account.lastData
	 * = lastData; } accountJson = account.toJsonObject(); for (Object entry :
	 * accounts) { JsonObject js = (JsonObject) entry; if
	 * (js.getString("name").equals(source)) { accounts.remove(js);
	 * accounts.add(accountJson); break; } }
	 *
	 * return wallet; }
	 */

	private List<Object> getTransactionsForSource(JsonArray transactions, String source, boolean fromLast) {
		if (fromLast) {
			int num = 100;
			JsonArray trAux = new JsonArray();
			for (int i = transactions.size() - 1; i > transactions.size() - 1 - num && i >= 0; i--) {
				trAux.add(transactions.getJsonObject(i));
			}
			transactions = trAux;
		}
		if (source.equals("walking") || source.equals("biking") || source.equals("e-driving")) {
			String newSource = "user_" + source + "_context";
			return transactions.stream()
					.filter(transaction -> isUserActivityTransaction((JsonObject) transaction, newSource))
					.collect(Collectors.toList());
		} else {
			return transactions.stream()
					.filter(transaction -> ((JsonObject) transaction).getString("source").equals(source))
					.collect(Collectors.toList());
		}
	}

	private boolean isUserActivityTransaction(JsonObject transaction, String source) {
		if (!transaction.getString("source").equals("user-activity")) {
			return false;
		} else
			return transaction.getJsonObject("data").getString("activity").equals(source);
	}

	private void updateAccountLastTransactions(JsonObject account, JsonObject wallet) {
		JsonArray transactions = account.getJsonArray("lastTransactions");

		JsonObject query = new JsonObject().put("_id", new JsonObject().put("$in", transactions));
		Future<JsonArray> transactionsFuture = getTransactions(query);
		transactionsFuture.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				JsonArray lastMonth = new JsonArray();
				JsonArray allTransactions = asyncResult.result();
				String walletID = wallet.getString("_id");

				int lastData = 0;
				int lastBalance = 0;
				for (Object transaction : allTransactions) {
					JsonObject current = (JsonObject) transaction;

					boolean onLast = false;

					// Week or month update?!
					String lastPeriod = account.getString("lastPeriod");
					if (lastPeriod.equals("month")) {
						onLast = DateUtilsHelper
								.isDateInCurrentMonth(DateUtilsHelper.stringToDate(current.getString("date")));
					} else {
						onLast = DateUtilsHelper
								.isDateInCurrentWeek(DateUtilsHelper.stringToDate(current.getString("date")));
					}

					if (onLast) {
						lastMonth.add(current.getString("_id"));
						int value = current.getInteger("value");
						lastBalance += value;

						if (current.getString("source").equals("user-activity")) {
							lastData += current.getJsonObject("data").getInteger("distance");
						} else if (current.getString("source").equals("energy-saving")) {
							lastData = current.getJsonObject("data").getInteger("value");
						} else {
							lastData++;
						}
					}

				}
				// e33b9462071ee871f40440465c04ed53cd8e38bb89972e62ede87bde042f1693
				// only update when lastTransactions exist
				if (lastMonth.size() > 0 || lastMonth.size() != allTransactions.size()) {

					account.remove("lastBalance");
					account.put("lastBalance", lastBalance);

					account.remove("lastData");
					account.put("lastData", lastData);

					account.remove("lastTransactions");
					account.put("lastTransactions", lastMonth);

					JsonObject queryWallet = new JsonObject().put("_id", walletID);

					mongoClient.findOneAndReplace(walletsCollection, queryWallet, wallet, id -> {

						logger.info("[updateAccountsScheduler] updated for " + walletID);

					});
				}
			}
		});

	}

	private Future<JsonObject> getPublicWallets() {
		Future<JsonObject> walletsToReturn = Future.future();
		JsonObject query = new JsonObject().put("identity",
				new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));

		mongoClient.find(walletsCollection, query, res -> {
			JsonObject result = res.result().get(0);

			walletsToReturn.complete(result);
		});

		return walletsToReturn;
	}

	private Future<JsonArray> getTransactions(JsonObject query) {
		Future<JsonArray> transactionsToReturn = Future.future();

		mongoClient.find(transactionsCollection, query, res -> {
			JsonArray result = new JsonArray(res.result().toString());

			transactionsToReturn.complete(result);
		});

		return transactionsToReturn;
	}

	private Future<JsonObject> getPublicWallet(String walletAddress) {
		logger.debug(logMessage + "getPublicWallet(): " + walletAddress);

		Future<JsonObject> walletToReturn = Future.future();
		Future<JsonObject> publicWalletsFuture = getPublicWallets();
		publicWalletsFuture.setHandler(asyncResult -> {
			// create wallets
			JsonArray wallets = asyncResult.result().getJsonArray("wallets");
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

	private Future<Void> transferToPublicWallet(String walletAddress, JsonObject transaction) {
		SharedData sd = vertx.sharedData();
		Future<Void> transferFuture = Future.future();

		sd.getLockWithTimeout("mongoLock", 10000, r -> {

			long startTime = System.currentTimeMillis();

			if (!r.succeeded()) {
				transferFuture.complete();
				logger.error("Error when accessing lock");
				return;
			}
			// Got the lock!
			Lock lock = r.result();

			logger.debug(logMessage + "transferToPublicWallet(): " + walletAddress + "\n" + transaction);
			String source = getSource(transaction);
			String sourceOriginal = transaction.getString("source");
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

						int currentBalance = wallet.getInteger("balance");

						logger.debug(logMessage + "transferToPublicWallet(): current balance " + currentBalance);
						if (transactionValue > 0) {
							logger.debug(logMessage + "transferToPublicWallet - 1");
							// update accounts

							wallet = checkEDriving(wallet);
							wallet = checkFeedback(wallet);
							wallet = checkEngageRating(wallet);
							logger.debug(logMessage + "transferToPublicWallet - 2");

							Account account = getAccount(source, wallet);
							logger.debug(logMessage + "transferToPublicWallet - 3" + account.toJsonObject().toString());
							account = updateAccount(account, transaction);
							logger.debug(logMessage + "transferToPublicWallet - 5" + account.toJsonObject().toString());

							// update wallet
							wallet = updateLastTransactions(wallet, transaction);

							wallet = updateWalletAccounts(wallet, account);
							logger.debug(logMessage + "transferToPublicWallet - 6" + wallet.toString());
							wallet = sumAccounts(wallet, false);
							logger.debug(logMessage + "transferToPublicWallet - 7" + wallet.toString());
						} else {
							wallet.put("balance", currentBalance);
						}

						// update counters
						JsonObject countersObj = wallet.getJsonObject(counters);

						System.out.println("counter obj" + countersObj.toString());
						System.out.println("source" + source);
						if (!source.equals("feedback")) {
							if (transaction.containsKey("bonus") && !transaction.getBoolean("bonus")) {
								if (!sourceOriginal.equals("created") && !sourceOriginal.equals("bonus")) {
									if (sourceOriginal.equals("user-activity")) {
										countersObj.put("user-activity",
												countersObj.getInteger("user-activity") + transactionValue);
									} else {
										countersObj.put(source, countersObj.getInteger(source) + transactionValue);
									}

								}
							}
						}

						updatedWallet = wallet;
					}
				}

				logger.debug(logMessage + "updating with: wallet  pubwallet toolong");

				mongoClient.findOneAndReplace(walletsCollection, query, result, id -> {
					logger.debug("[WalletManager] Transaction added to public wallet");

					long endTime = System.currentTimeMillis();
					long timeElapsed = endTime - startTime;
					logger.debug("Lock time: " + timeElapsed + " ms");
					lock.release();

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
					JsonObject accounts = new JsonObject();
					accounts.put("value", updatedWallet.getJsonArray("accounts"));
					accounts.put("attributeType", "array");
					accounts.put("attribute", "accounts");
					updateBody.add(accounts);
					JsonObject value = new JsonObject();
					value.put("value", updatedWallet.getInteger("balance"));
					value.put("attribute", "value");
					updateBody.add(value);
					updateMessage.put("body", updateBody);

					// publish transaction in the event bus using the wallet address.
					String toSendChanges = walletAddress + "/changes";
					logger.debug(logMessage + "publishing on " + toSendChanges);

					publish(toSendChanges, updateMessage);
					publish(publicWalletsOnChangesAddress, updateMessage);

					transferFuture.complete();
				});
			});

		});

		return transferFuture;

	}

	private JsonObject checkEDriving(JsonObject wallet) {
		boolean evehicleExists = false;
		JsonArray accounts = wallet.getJsonArray("accounts");
		for (Object object : accounts) {
			JsonObject account = (JsonObject) object;
			if (account.getString("name").equals("e-driving"))
				evehicleExists = true;
		}

		if (!evehicleExists) {
			System.out.println("UPDATING WITH EVEHICLES");
			// build "e-driving" account
			Account eDriving = new Account("e-driving", "kw/h");
			accounts.add(eDriving.toJsonObject());
		}
		return wallet;

	}

	private JsonObject checkFeedback(JsonObject wallet) {
		boolean feedbackExists = false;
		JsonArray accounts = wallet.getJsonArray("accounts");
		for (Object object : accounts) {
			JsonObject account = (JsonObject) object;
			if (account.getString("name").equals("feedback"))
				feedbackExists = true;
		}

		if (!feedbackExists) {
			System.out.println("UPDATING WITH FEEDBACK");
			// build "feedback" account
			Account feedback = new Account("feedback", "questionnaire");
			accounts.add(feedback.toJsonObject());
		}
		return wallet;

	}

	private JsonObject checkEngageRating(JsonObject wallet) {
		boolean engageExists = false;
		JsonArray accounts = wallet.getJsonArray("accounts");
		for (Object object : accounts) {
			JsonObject account = (JsonObject) object;
			if (account.getString("name").equals("engagedUsers"))
				engageExists = true;
		}

		if (!engageExists) {
			System.out.println("UPDATING WITH ENGAGEDUSERS");
			// build "engagedUsers" account
			Account engagedUsers = new Account("engagedUsers", "users");
			accounts.add(engagedUsers.toJsonObject());
		}
		return wallet;

	}

	private JsonObject sumAccounts(JsonObject wallet, boolean isPrivate) {

		JsonArray accounts = wallet.getJsonArray("accounts");
		int sum = 0;
		for (Object object : accounts) {
			JsonObject account = (JsonObject) object;
			sum += account.getInteger("totalBalance");

		}
		if (isPrivate) {
			sum += initialBalance;
		}
		wallet.put("balance", sum);
		return wallet;
	}

	CompletableFuture<Boolean> result = new CompletableFuture<>();

	private void validateTransaction(JsonObject transaction, String walletAddress) {

		logger.debug(logMessage + "validateTransaction() " + transaction.toString());

		// check the fields themselves
		if (!transaction.containsKey("recipient") || !transaction.containsKey("source")
				|| !transaction.containsKey("date") || !transaction.containsKey("value")
				|| !transaction.containsKey("nonce")) {
			logger.debug("Invalid");
		}

		// check date validity
		if (!DateUtilsHelper.validateDate(transaction.getString("date"))) {
			logger.debug("Invalid date format");
		}

		// check tokens amount
		if (!transaction.getString("source").equals("bonus") && transaction.getInteger("value") <= 0) {
			logger.debug("Transaction value must be greater than 0");
		}

		// check if wallet address exists
		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			if (!res.succeeded()) {
				logger.debug("Wallet does not exist");
				// return false
			} else {
				JsonObject wallet = res.result().get(0);
				logger.debug(logMessage + "Wallet exists: " + wallet.toString());

				if (transaction.getString("source").equals("bonus")) {
					// check with bonus-credit field
					int walletBalance = wallet.getInteger("bonus-credit");
					int cost = transaction.getInteger("value");
					if (cost > 0) {
						transaction.put("value", 0);
					} else {
						if (walletBalance + cost < 0) {
							logger.debug(logMessage + "insufficient funds for pick up");
							transaction.put("description", "invalid-insufficient-credits");
							transaction.put("value", 0);
						}
					}

				}
				Future<Void> updatedTransaction = transferToPrivateWallet(walletAddress, transaction);

				updatedTransaction.setHandler(asyncResult -> {

					Long currentTime = new Date().getTime();
					logger.debug(" current:" + currentTime + "\n challengeExpire:" + challengeExpire);
					if (currentTime < challengeExpire) {
						String publicWalletAddress = wallet.getString(causeWalletAddress);
						if (publicWalletAddress != null && !publicWalletAddress.equals("")) {
							// update wallet2bGranted balance
							transferToPublicWallet(publicWalletAddress, transaction);
						}
					} else {
						logger.debug("challengeExpire");
					}

				});

				// check if nonce is repeated
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
				// nonce is repeated
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
		logger.debug("Getting wallet address msg:" + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("guid", body.getString("value")));

		JsonObject toSearch = new JsonObject().put("identity", identity);

		logger.debug("Search on " + this.collection + " with data" + toSearch.toString());

		mongoClient.find(this.collection, toSearch, res -> {
			if (res.result().size() != 0) {
				JsonObject walletInfo = res.result().get(0);
				// reply with address
				logger.debug("Returned wallet: " + walletInfo.toString());
				message.reply(walletInfo);
			} else {
				message.reply(new JsonObject().put("error", "no wallet for this guid"));
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
		logger.debug(logMessage + "walletRead(): getting wallet for address " + walletAddress);

		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject wallet = res.result().get(0);
			logger.debug(logMessage + "walletRead(): " + wallet);
			message.reply(wallet);
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
		logger.debug("DATA TO UPDATE " + "(msg)" + msg);

		JsonObject update = new JsonObject().put("$set", toUpdate);

		logger.debug(logMessage + " update it" + update.toString() + "\n on wallet with " + identity.toString());

		mongoClient.updateCollection(this.collection, identity, update, res -> {
			logger.debug(logMessage + " result update" + res.succeeded());
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
		logger.info("[WalletManager] handleCreationRequest");
		logger.debug("[WalletManager] handleCreationRequest: " + msg);
		// send message to Vertx P2P stub and wait for reply

		Future<Void> result = Future.future();

		message.reply(msg, reply2 -> {

			logger.debug("Reply from P2P stub " + reply2.succeeded());

			JsonObject rep = new JsonObject(reply2.result().body().toString());

			// check if 200
			int code = rep.getJsonObject("body").getInteger("code");
			if (code == 200) {

				JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("guid",
						msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid")));

				mongoClient.find(walletsCollection, new JsonObject().put("identity", identity), res -> {

					if (res.result().size() == 0) {
						logger.debug("no wallet yet, creating");

						String address = generateWalletAddressv2(msg.getJsonObject("identity"));
						Future<Integer> balFuture = getInitialBalance(initialBalance);
						balFuture.setHandler(balanceRes -> {
							int bal = balanceRes.result();

							JsonArray transactions = new JsonArray();
							JsonObject newTransaction = new JsonObject();
							JsonObject info = msg.getJsonObject("identity").getJsonObject("userProfile")
									.getJsonObject("info");
							if (info.containsKey("balance")) {
								newTransaction.put("recipient", address);
								newTransaction.put("source", "created");
								newTransaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
								newTransaction.put("value", bal);
								newTransaction.put("description", "valid");
								newTransaction.put("nonce", 1);
								JsonObject data = new JsonObject();
								data.put("created", "true");
								newTransaction.put("data", data);
								// transactions.add(newTransaction);
							}
							// build wallet document
							JsonObject newWallet = new JsonObject();

							newWallet.put("address", address);
							newWallet.put("identity", identity);
							newWallet.put("created", new Date().getTime());
							newWallet.put("balance", bal);
							newWallet.put("bonus-credit", bal);
							newWallet.put("status", "active");
							newWallet.put("ranking", 0);
							newWallet.put("accounts", accountsDefault.copy());

							// check if profile info
							JsonObject profileInfo = msg.getJsonObject("identity").getJsonObject("userProfile")
									.getJsonObject("info");
							logger.debug("[WalletManager] Profile info: " + profileInfo);
							JsonObject response = new JsonObject().put("code", 200).put("wallet", newWallet);
							if (profileInfo != null) {
								causeID = profileInfo.getString("cause");
								newWallet.put("profile", profileInfo);
								String wallet2bGranted = getPublicWalletAddress(causeID);
								newWallet.put(causeWalletAddress, wallet2bGranted);
								newTransaction.put(causeWalletAddress, wallet2bGranted);

								String roleCode = profileInfo.getString("code");
								if (roleCode != null) {

									logger.debug(logMessage + "resolving role for code " + roleCode);
									if (roleCode.startsWith("user-guid://")) {
										logger.debug(logMessage + "checking guid...");
										// check if wallet with this guid
										Future<JsonObject> isValidGuidFuture = getWalletInfo(roleCode);

										isValidGuidFuture.setHandler(guidRes -> {
											logger.debug(logMessage + "getWalletInfo returned!");
											if (guidRes.succeeded()) {
												JsonObject walletInfo = guidRes.result();
												if (walletInfo.getJsonObject("error") == null) {
													String walletAddress = walletInfo.getString("address");
													JsonObject engagedUserTransaction = new JsonObject();
													engagedUserTransaction.put("recipient",
															walletInfo.getString("_id"));
													engagedUserTransaction.put("source", "engagedUsers");
													engagedUserTransaction.put("date",
															DateUtilsHelper.getCurrentDateAsISO8601());
													engagedUserTransaction.put("value", engageRating);
													engagedUserTransaction.put("description", "valid");
													engagedUserTransaction.put("nonce", 1);
													engagedUserTransaction.put(causeWalletAddress, wallet2bGranted);
													// transfer to other wallet
													transferToPrivateWallet(walletAddress, engagedUserTransaction);

													Long currentTime = new Date().getTime();
													logger.debug(" current:" + currentTime + "\n challengeExpire:"
															+ challengeExpire);
													if (currentTime < challengeExpire) {
														transferToPublicWallet(newWallet.getString(causeWalletAddress),
																engagedUserTransaction);
													} else {
														logger.debug("challengeExpire handle creation request - 1!");
													}
												}
											}
										});
									} else {

										Future<Void> validateCause = Future.future();

										JsonObject validationMessage = new JsonObject();
										validationMessage.put("from", url);
										validationMessage.put("identity", identity);
										validationMessage.put("type", "forward");
										validationMessage.put("code", roleCode);
										send("resolve-role", validationMessage, reply -> {
											logger.debug(logMessage + "role validation result: "
													+ reply.result().body().toString());
											String role = new JsonObject(reply.result().body().toString())
													.getString("role");
											response.put("role", role);

											validateCause.complete();
										});

										validateCause.setHandler(asyncResult2 -> {
											if (asyncResult2.succeeded()) {
												// reply2.result().reply(response);
												// result.complete();
											} else {
												// oh ! we have a problem...
											}
										});
									}

								}
							} else {
								response.put("code", 400).put("reason", "you must provide user info (i.e. cause)");
								reply2.result().reply(response);
								return;
							}

							JsonObject document = new JsonObject(newWallet.toString());
							mongoClient.save(walletsCollection, document, id -> {
								logger.debug("[WalletManager] new wallet with ID:" + id);
								if (id.succeeded()) {
									newTransaction.put("recipient", id.result());
									transactions.add(newTransaction);
									newWallet.put("transactions", transactions);

									mongoClient.findOneAndReplace(walletsCollection,
											new JsonObject().put("_id", id.result()),
											new JsonObject(newWallet.toString()), resultHandler -> {
												if (resultHandler.succeeded()) {

													// transfer to public after persisting
													Future<Void> persistFuture = persistTransaction(newTransaction);
													persistFuture.setHandler(r -> {
														response.put("code", 200).put("wallet", newWallet);
														logger.debug("wallet created, reply" + response.toString());

														Long currentTime = new Date().getTime();
														logger.debug(" current:" + currentTime + "\n challengeExpire:"
																+ challengeExpire);
														if (currentTime < challengeExpire) {
															Future<Void> transferPublic;
															if (bal > 0) {
																transferPublic = transferToPublicWallet(
																		newWallet.getString(causeWalletAddress),
																		newTransaction);
															} else {
																transferPublic = Future.future();
																transferPublic.complete();
															}

															transferPublic.setHandler(asyncResult -> {
																reply2.result().reply(response);
																result.complete();

															});
														} else {
															logger.debug("challengeExpire handle creation request - 2!");
															reply2.result().reply(response);
															result.complete();
														}
													});

												}
											});

								}
								inviteObservers(msg.getJsonObject("identity"), address, requestsHandler(),
										readHandler());
							});

						});

					} else {
						JsonObject wallet = res.result().get(0);
						logger.debug("[WalletManager] wallet already exists...");
						/*
						 * JsonArray accounts = wallet.getJsonArray("accounts"); if (accounts == null &&
						 * wallet.getJsonArray("wallets") == null) { JsonArray newAccounts =
						 * buildAccountWallet(wallet); wallet.put("accounts", newAccounts); // update
						 * private wallet JsonObject query = new JsonObject().put("identity",
						 * wallet.getJsonObject("identity"));
						 * mongoClient.findOneAndReplace(walletsCollection, query, wallet, id -> {
						 * logger.debug("[WalletManager] accounts added to wallet"); }); // update
						 * public wallet updatePublicWalletAccountsNewUser(wallet, newAccounts); }
						 */

						// JsonObject response = new JsonObject().put("code", 200).put("wallet",
						// limitTransactions(wallet));
						JsonObject response = new JsonObject().put("code", 200).put("wallet", wallet);

						// check its status
						switch (wallet.getString("status")) {
						case "active":
							logger.debug("... and is active.");
							break;
						case "deleted":
							logger.debug("... and was deleted, activating");
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
			} else {
				logger.debug("code != 200");
			}
		});

		return result;

	}

	/**
	 * Check initial balance, if 1000th user (i.e. there are already 999 private + 1
	 * public wallet) then initial wallet balance is 1000 points.
	 * 
	 * @param initialbalance
	 * @return
	 */
	private Future<Integer> getInitialBalance(int initialbalance) {

		// check num wallets
		Future<Integer> balance = Future.future();
		mongoClient.find(walletsCollection, new JsonObject(), res -> {

			int numWallets = res.result().size();

			logger.debug(logMessage + "getInitialBalance(): user number " + numWallets);

			if (numWallets == 1000) {
				balance.complete(1000);
			} else {
				balance.complete(initialbalance);
			}

		});
		return balance;
	}

	private void updatePublicWalletAccountsNewUser(JsonObject privateWallet, JsonArray newAccounts) {

		logger.debug("[WalletManager] updatePublicWalletAccountsNewUser");
		Future<JsonObject> publicWalletFuture = getPublicWallet(privateWallet.getString("wallet2bGranted"));
		publicWalletFuture.setHandler(asyncResult -> {
			JsonObject publicWallet = publicWalletFuture.result();

			// default value
			JsonArray accountsPublic = publicWallet.getJsonArray("accounts");
			JsonArray accountsPrivate = privateWallet.getJsonArray("accounts");
			/*
			 * final boolean updateOtherPublicWallets = (accountsPublic == null);
			 *
			 * if (updateOtherPublicWallets) { publicWallet.put("accounts",
			 * buildAccountWallet(publicWallet)); } else {
			 */
			List<String> activities = new ArrayList<>();
			activities.add("walking");
			activities.add("biking");
			activities.add("elearning");
			activities.add("checkin");
			activities.add("energy-saving");
			for (String source : activities) {
				Account accountPublic = getAccount(accountsPublic, source);
				Account accountPrivate = getAccount(accountsPrivate, source);
				accountPublic.totalBalance += accountPrivate.totalBalance;
				accountPublic.totalData += accountPrivate.totalData;
				accountPublic.lastBalance += accountPrivate.lastBalance;
				accountPublic.lastData += accountPrivate.lastData;
				JsonObject accountJson = accountPublic.toJsonObject();
				for (Object entry : accountsPublic) {
					JsonObject js = (JsonObject) entry;
					if (js.getString("name").equals(source)) {
						accountsPublic.remove(js);
						accountsPublic.add(accountJson);
						break;
					}
				}
			}
			// }

			// update mongo
			Future<JsonObject> publicWalletsFuture = getPublicWallets();
			publicWalletsFuture.setHandler(res -> {
				JsonObject publicWallets = publicWalletsFuture.result();
				JsonArray wallets = publicWallets.getJsonArray("wallets");
				JsonArray newWallets = new JsonArray();
				for (Object pWallet : wallets) {
					// get wallet with that address
					JsonObject wallet = (JsonObject) pWallet;
					if (wallet.getString("address").equals(privateWallet.getString("wallet2bGranted"))) {
						logger.debug("[WalletManager] updatePublicWalletAccountsNewUser found: " + wallet);
						newWallets.add(publicWallet);
					} else {
						/*
						 * if (updateOtherPublicWallets) { wallet.put("accounts",
						 * buildAccountWallet(wallet)); newWallets.add(wallet); } else {
						 */
						newWallets.add(wallet);
						// }
					}
				}

				publicWallets.put("wallets", newWallets);
				JsonObject query = new JsonObject().put("identity",
						new JsonObject().put("userProfile", new JsonObject().put("guid", publicWalletGuid)));
				mongoClient.findOneAndReplace(walletsCollection, query, publicWallets, id -> {
					logger.debug("[WalletManager] updatePublicWalletAccountsNewUser updated: " + publicWallets);
				});

			});

		});
	}

	private Account getAccount(JsonArray accounts, String source) {
		List<Object> res = accounts.stream().filter(account -> ((JsonObject) account).getString("name").equals(source))
				.collect(Collectors.toList());
		JsonObject accountJson = (JsonObject) res.get(0);
		return Account.toAccount(accountJson);
	}

	/**
	 * Add accounts to an already existing wallet (when migrating).
	 *
	 * @param wallet
	 * @return
	 */
	/*
	 * private JsonArray buildAccountWallet(JsonObject wallet) { // default value
	 * logger.debug("[WalletManager] buildAccounts"); // for each activity // TODO -
	 * get from transactions collection JsonArray transactions =
	 * wallet.getJsonArray("transactions"); wallet.put("accounts",
	 * accountsDefault.copy()); JsonArray accounts =
	 * wallet.getJsonArray("accounts");
	 *
	 * List<String> activities = new ArrayList<>(); activities.add("walking");
	 * activities.add("biking"); activities.add("elearning");
	 * activities.add("checkin"); activities.add("energy-saving"); for (String
	 * source : activities) { // get transactions of that source (watch out for
	 * user-activity!) List<Object> transactionsForSource =
	 * getTransactionsForSource(transactions, source, false); // get account for
	 * source JsonArray accountsDefCopy = accountsDefault.copy();
	 * logger.debug("[WalletManager] buildAccounts copy: " + accountsDefCopy);
	 * List<Object> res = accountsDefCopy.stream() .filter(account -> ((JsonObject)
	 * account).getString("name").equals(source)) .collect(Collectors.toList());
	 * JsonObject accountJson = (JsonObject) res.get(0); Account account =
	 * Account.toAccount(accountJson); account.totalBalance = 0; // sum all
	 * transactions value account.totalBalance =
	 * sumTransactionsField(transactionsForSource, "value"); if
	 * (!source.equals("walking") && !source.equals("biking")) { account.totalData =
	 * transactionsForSource.size(); } else { account.totalData =
	 * sumTransactionsField(transactionsForSource, "distance"); }
	 *
	 * JsonArray lastTransactions = (account.lastPeriod.equals("month")) ?
	 * lastMonthTransactions(transactionsForSource) :
	 * lastWeekTransactions(transactionsForSource); account.lastBalance =
	 * sumTransactionsField(lastTransactions.getList(), "value"); if
	 * (!source.equals("walking") && !source.equals("biking")) { account.lastData =
	 * lastTransactions.size(); } else { account.lastData =
	 * sumTransactionsField(lastTransactions.getList(), "distance"); } accountJson =
	 * account.toJsonObject(); for (Object entry : accounts) { JsonObject js =
	 * (JsonObject) entry; if (js.getString("name").equals(source)) {
	 * accounts.remove(js); accounts.add(accountJson); break; } }
	 *
	 * }
	 *
	 * logger.debug("[WalletManager] buildAccounts result:" + accounts); return
	 * accounts; }
	 */
	private int sumTransactionsField(List<Object> transactions, String field) {
		int sum = 0;
		for (Object transaction : transactions) {
			if (field.equals("distance")) {
				sum += ((JsonObject) transaction).getJsonObject("data").getInteger("distance");
			} else
				sum += ((JsonObject) transaction).getInteger(field);
		}
		return sum;
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
			logger.debug("getCauseAddress()" + wallet.toString());
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
			logger.debug("REQUESTS HANDLER: " + msg.body().toString());
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
				logger.debug("REQUESTS HANDLER reply");
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
			logger.debug("READ HANDLER: " + msg.body().toString());
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

			JsonObject walletIdentity = msg.body().getJsonObject("identity");
			mongoClient.find(walletsCollection, new JsonObject().put("identity", walletIdentity), res -> {
				JsonObject wallet = res.result().get(0);
				logger.debug(wallet);

				sendMsgBody.put("code", 200).put("wallet", wallet);
				response.put("body", sendMsgBody);
				msg.reply(response);
			});

		};

	}

	private void limitAux(JsonObject wallet) {
		// TODO - get from transactions collection
		JsonArray transactions = wallet.getJsonArray("transactions");
		if (transactions.size() > onReadMaxTransactions) {
			final int size = transactions.size();
			for (int i = 0; i < size - onReadMaxTransactions; i++) {
				transactions.remove(0);
			}
		}
	}

	private JsonObject limitTransactions(JsonObject wallet) {
		// check if public or private
		boolean isPublic = wallet.getJsonArray("wallets") != null;
		if (isPublic) {
			JsonArray wallets = wallet.getJsonArray("wallets");
			for (Object object : wallets) {
				limitAux((JsonObject) object);
			}
		} else {
			limitAux(wallet);
		}
		return wallet;
	}

	/**
	 * The Wallet Address is generated by using some crypto function that uses the
	 * identity GUID as seed and returned.
	 *
	 * @param jsonObject
	 * @return wallet address
	 */
	private String generateWalletAddress(JsonObject jsonObject) {
		logger.debug("JSON is " + jsonObject);
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
		logger.debug("JSON is " + jsonObject);
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

	Future<JsonObject> getWalletInfo(String userId) {
		logger.debug("Getting WalletAddress Info:" + userId);
		// send message to Wallet Manager address
		/*
		 * type: read, from: <rating address>, body: { resource: 'user/<userId>'}
		 */
		// build message and convert to JSON string
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", this.url);
		msg.put("body", new JsonObject().put("resource", "user").put("value", userId));
		msg.put("identity", new JsonObject());

		Future<JsonObject> walletInfo = Future.future();

		send(this.url, msg, reply -> {

			JsonObject rep = reply.result().body();

			if (rep.getJsonObject("error") != null) {
				// no wallet for this guid
				walletInfo.complete(rep);
			} else {

				JsonObject walletresult = new JsonObject().put("address", reply.result().body().getString("address"))
						.put("_id", (String) reply.result().body().getValue("_id"))
						.put("wallet2bGranted", reply.result().body().getString("wallet2bGranted"));
				logger.debug("sending reply from getwalletInfo" + walletresult.toString());
				walletInfo.complete(walletresult);
			}
		});

		return walletInfo;

	}

}
