package tokenRating;

import hyperty.AbstractHyperty;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtilsHelper;

public class AbstractTokenRatingHyperty extends AbstractHyperty {

	private static final String logMessage = "[AbstractTokenRatingHyperty] ";

	/**
	 * hyperty address where to setup an handler to process invitations in case the
	 * data source is dynamic eg produced by the smart citizen
	 */
	private String hyperty;
	/**
	 * the stream address to setup the handler in case the address is static e.g.
	 * when the stream is produced via the Smart IoT.
	 */
	private String streamAddress;
	/**
	 * Wallet Manager Hyperty address.
	 */
	private String walletManagerAddress;

	@Override
	public void start() {
		super.start();
		logger.debug("Abstract started");

		// read config
		hyperty = config().getString("hyperty");
		streamAddress = config().getString("stream");
		walletManagerAddress = config().getString("wallet");

		addMyHandler();
	}

	/*
	 * An empty rating engine function (separate class?) when the data evaluation in
	 * tokens is implemented according to a certain algorithm.
	 */
	Future<Integer> rate(Object data) {
		Future<Integer> tokens = Future.future();
		tokens.complete(10);
		return tokens;
	}

	/*
	 * A Token miner function that generates numTokens as uint type as well as an
	 * associated transaction that is stored in a DB (or the transaction is only
	 * stored in the recipient wallet ?) (future in a blockchain?):
	 */
	void mine(int numTokens, JsonObject msgOriginal, String source) {
		logger.debug(logMessage + "mine(): Mining " + numTokens + " tokens...\nmsg: " + msgOriginal + "    ->source:  " + source);
		String userId = msgOriginal.getString("guid");

		// store transaction by sending it to wallet through wallet manager
/*		Future<String> walletAddress = getWalletAddress(userId);
		walletAddress.setHandler(asyncResult -> {
			
			if (asyncResult.succeeded()) {
		*/
				JsonObject msgToWallet = new JsonObject();
				msgToWallet.put("type", "create");
				msgToWallet.put("identity", this.identity);

				// create transaction object
				JsonObject transaction = new JsonObject();
				transaction.put("source", source);
				transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
				transaction.put("value", numTokens);

				// when source is bonus numTokens is always negative
				if (!source.equals("bonus")) {
					if (numTokens == -1) {
						transaction.put("description", "invalid-timestamp");
					} else if (numTokens == -2) {
						transaction.put("description", "invalid-location");
					} else if (numTokens == -3) {
						transaction.put("description", "invalid-short-distance");
						transaction.put("value", 0);
					} else if (numTokens == -4) {
						transaction.put("description", "invalid-daily-max-exceeded");
						transaction.put("value", 0);
					} else if (numTokens == -5) {
						transaction.put("description", "timeout-error");
						transaction.put("value", 0);
					} else {
						transaction.put("description", "valid");
					}
					transaction.put("bonus", false);
				} else {
					if (numTokens == 0) {
						transaction.put("description", "invalid-failed-constraints");
					} else if (numTokens == 1) {
						transaction.put("description", "invalid-not-available");
					}

					else {
						transaction.put("description", "valid");
					}
					transaction.put("bonus", true);
				}

				transaction.put("nonce", 1);
				if (source.equals("user-activity")) {
					// add data
					JsonObject data = new JsonObject();
					data.put("distance", msgOriginal.getInteger("distance"));
					data.put("activity", msgOriginal.getString("activity"));
					transaction.put("data", data);
				}
				if (source.equals("elearning")) {
					// add data
					JsonObject data = new JsonObject();
					data.put("quiz", msgOriginal.getString("id"));
					transaction.put("data", data);
				}
				if (source.equals("checkin")) {
					// add data
					JsonObject data = new JsonObject();
					data.put("shopID", msgOriginal.getString("shopID"));
					transaction.put("data", data);
				}
				if (source.equals("bonus")) {
					// add data
					JsonObject data = new JsonObject();
					data.put("shopID", msgOriginal.getString("shopID"));
					data.put("bonusID", msgOriginal.getString("bonusID"));
					transaction.put("data", data);
				}
				if (source.equals("energy-saving")) {
					// TODOadd data
					JsonObject data = new JsonObject();
					data.put("something", "something");
					transaction.put("data", data);
				}

				transaction.put("nonce", 1);
				JsonObject body = new JsonObject().put("resource", userId).put("value",
						transaction);

				msgToWallet.put("body", body);

				transfer(msgToWallet);
				
				
	/*		} else {
				// oh ! we have a problem...
			}
		});*/

	}

	/**
	 * Performs the transaction to Wallet Address.
	 *
	 * @param transaction
	 */
	private void transfer(JsonObject msg) {
		logger.debug(logMessage + "transfer(): " + msg.toString());

		vertx.eventBus().publish(walletManagerAddress, msg);
	}

	/**
	 * Send message to Wallet Manager address with callback to return the value
	 * returned in case it is found.
	 *
	 * @param userId
	 * @return
	 */
	Future<String> getWalletAddress(String userId) {
		logger.debug("Getting WalletAddress to:" + userId);
		// send message to Wallet Manager address
		/*
		 * type: read, from: <rating address>, body: { resource: 'user/<userId>'}
		 */
		// build message and convert to JSON string
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", hyperty);
		msg.put("body", new JsonObject().put("resource", "user").put("value", userId));
		msg.put("identity", new JsonObject());

		Future<String> walletAddress = Future.future();

		send(walletManagerAddress, msg, reply -> {

			logger.debug("sending reply from getwalletAddress" + reply.result().body().toString());
			walletAddress.complete(reply.result().body().getString("address"));
		});

		return walletAddress;

	}

	/**
	 * Add an handler in the Rating Hyperty address specified in the config file and
	 * calls addStreamHandler() for valid received invitations (create messages) or
	 * removeStreamHandler for valid received delete messages.
	 */
	private void addMyHandler() {
		logger.debug(logMessage + "addMyHandler: " + streamAddress);
		vertx.eventBus().<JsonObject>consumer(streamAddress, message -> {
			logger.debug(logMessage + "new message: " + message.body().toString());
			mandatoryFieldsValidator(message);
			JsonObject body = new JsonObject(message.body().toString());
			String type = body.getString("type");
			String handleCheckInUserURL = body.getJsonObject("identity").getJsonObject("userProfile")
					.getString("userURL");
			JsonObject response = new JsonObject();

			// check message type
			switch (type) {
			case "create":
				// valid received invitations (create messages)
				logger.debug("Abstract ADD STREAM");
				Future<Boolean> canHandleData = checkIfCanHandleData(handleCheckInUserURL);
				canHandleData.setHandler(asyncResult -> {
					if (asyncResult.succeeded()) {
						if (canHandleData.result()) {
							addStreamHandler(handleCheckInUserURL);
							response.put("body", new JsonObject().put("code", 200));
							message.reply(response);
							logger.debug("Replied with" + response.toString());
						} else {
							response.put("body", new JsonObject().put("code", 406));
							message.reply(response);
						}
					} else {
						// oh ! we have a problem...
					}
				});

				break;
			case "delete":
				removeStreamHandler(handleCheckInUserURL);
				break;

			default:
				logger.debug("Incorrect message type: " + type);
				break;
			}
		});
	}

	public Future<String> getUserURL(String address) {

		Future<String> userID = Future.future();
		mongoClient.find(dataObjectsCollection, new JsonObject().put("url", address), userURLforAddress -> {
			logger.debug("2 - find Dataobjects size->" + userURLforAddress.result().size());
			if (userURLforAddress.result().size() == 0) {
				userID.complete("");
			}
			JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject("metadata");
			userID.complete(dataObjectInfo.getString("guid"));
		});

		return userID;
	}

	public Future<Boolean> checkIfCanHandleData(String userURL) {
		logger.debug(logMessage + "checkIfCanHandleData():" + userURL);
		Future<Boolean> canHandleData = Future.future();

		JsonObject query = new JsonObject().put("user", userURL);
		mongoClient.find(collection, query, res -> {
			if (res.result().size() != 0) {
				canHandleData.complete(true);
				logger.debug("User exists");
			} else {
				JsonObject document = new JsonObject();
				document.put("user", userURL);
				// setup rating sources
				document.put("checkin", new JsonArray());
				document.put("user-activity", new JsonArray());
				document.put("elearning", new JsonArray());
				document.put("energy-saving", new JsonArray());
				logger.debug("User exists false");
				mongoClient.insert(collection, document, res2 -> {
					logger.debug("Setup complete - rates");
					canHandleData.complete(true);
				});
			}

		});

		logger.debug("3 - return other");
		return canHandleData;

	}

	/**
	 * Add stream handlers and forwards it to rate() if rate returns a valid uint it
	 * calls mine() and transfers it to associated address
	 *
	 *
	 */
	private void addStreamHandler(String from) {
		// add a stream handler
		logger.debug("Adding stream handler from " + from);
		vertx.eventBus().<JsonObject>consumer(from, message -> {
			mandatoryFieldsValidator(message);

			logger.debug("Received message " + message.body() + " from " + from);

			Future<Integer> numTokens = rate(message.body());
			numTokens.setHandler(asyncResult -> {
				if (asyncResult.succeeded()) {
					mine(numTokens.result(), message.body(), message.body().getString("source"));
				} else {
					// oh ! we have a problem...
				}
			});

		});
	}

	private void removeStreamHandler(String from) {
		logger.debug("Removing stream handler from " + from);
	}

	JsonArray entryArray = null;

	/**
	 * Save data to MongoDB.
	 *
	 * @param user      user ID
	 * @param timestamp time in millis since epoch
	 * @param entryID   entryID
	 */
	Future<Void> persistData(String dataSource, String user, long timestamp, String entryID, JsonObject userRates,
			JsonObject data) {
		logger.debug(logMessage + "persistData()");

		Future<Void> persist = Future.future();

		new Thread(() -> {
			if (userRates != null)
				entryArray = userRates.getJsonArray(dataSource);
			else
				entryArray = new JsonArray();

			JsonObject query = new JsonObject().put("user", user);

			mongoClient.find(collection, query, result -> {
				JsonObject currentDocument = result.result().get(0);
				logger.debug("");
				entryArray = currentDocument.getJsonArray(dataSource);
				if (data != null) {
					data.put("timestamp", timestamp);
					data.put("id", entryID);
					entryArray.add(data);
				} else {
					JsonObject entry = new JsonObject();
					entry.put("timestamp", timestamp);
					entry.put("id", entryID);
					entryArray.add(entry);
				}
				currentDocument.put(dataSource, entryArray);

				// update only corresponding data source
				mongoClient.findOneAndReplace(collection, query, currentDocument, id -> {
					logger.debug(logMessage + "persistData -document updated: " + currentDocument);
					persist.complete();
				});
			});
		}).start();

		return persist;

	}

}
