package tokenRating;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtilsHelper;
import walletManager.WalletManagerHyperty;

/**
 * The Energy Saving Rating Hyperty uses the Smart IoT stub to observe devices
 * energy data consumption and calculate the tokens.
 *
 * There are two types of ratings, each one having a different rating engine:
 * public devices (schools) + individual devices (private residences)
 */
public class EnergySavingRatingHyperty extends AbstractTokenRatingHyperty {

	private static final String logMessage = "[EnergySavingRatingHyperty] ";

	// rating types
	public static final String ratingPublic = "hyperty://sharing-cities-dsm/energy-saving-rating/public";
	public static final String ratingPrivate = "hyperty://sharing-cities-dsm/energy-saving-rating/private";

	private String dataSource = "energy-saving";

	@Override
	public void start() {

		super.start();

		ratingType = "energy-saving";

		this.eb.<JsonObject>consumer(ratingPublic, onMessage(ratingPublic));
		this.eb.<JsonObject>consumer(ratingPrivate, onMessage(ratingPrivate));

		resumeDataObjects(ratingType);

	}

	public Handler<Message<JsonObject>> onMessage(String streamType) {

		return message -> {

			logger.debug(logMessage + "new message -> " + message.body().toString());
			if (mandatoryFieldsValidator(message)) {
				final JsonObject identity = new JsonObject(message.body().toString()).getJsonObject("identity");
				final String type = new JsonObject(message.body().toString()).getString("type");
				final String from = new JsonObject(message.body().toString()).getString("from");
				final String guid = identity.getJsonObject("userProfile").getString("guid");
				switch (type) {

				case "create":
					if (from.contains("/subscription")) {
						/*
						 * String address = from.split("/subscription")[0]; String userID =
						 * getUserURL(address); // checks there is no subscription yet for the User
						 * CGUID URL. if (userID == null) { if (persistDataObjUserURL(address, guid,
						 * "reporter") && checkIfCanHandleData(guid)) { onChanges(address, streamType);
						 * } }
						 */
						onNotification(new JsonObject(message.body().toString()), streamType);

					}
					break;
				default:
					break;
				}

			}
		};
	}

	@Override
	Future<Integer> rate(Object data) {
		logger.debug(logMessage + "rate(): " + data.toString());
		Long currentTimestamp = new Date().getTime();

		Future<Integer> tokenAmount = Future.future();

		// data contains shopID, users's location
		String ratingType = ((JsonObject) data).getString("ratingType");
		String user = ((JsonObject) data).getString("guid");
		JsonObject energyMessage = ((JsonObject) data).getJsonObject("message");
		// parse values
		JsonArray values = energyMessage.getJsonArray("values");

		// rating algorithm
		switch (ratingType) {
		case ratingPublic:
			Future<Void> applyPublicRating = applyPublicRating(values);
			applyPublicRating.setHandler(asyncResult -> {
				if (asyncResult.succeeded()) {
					tokenAmount.complete(0);
				} else {
					// oh ! we have a problem...
				}
			});

			break;
		case ratingPrivate:
			tokenAmount.complete(applyPrivateRating(values));
			break;
		default:
			break;
		}
		persistData(dataSource, user, currentTimestamp, "1", null, null);

		return tokenAmount;
	}

	int biggestReductionPercentage;

	/**
	 * Rate energy message according to the public algorithm.
	 *
	 * @return
	 */
	private Future<Void> applyPublicRating(JsonArray values) {

		Future<Void> applyPublicRating = Future.future();

		logger.debug(logMessage + "applyPublicRating(): " + values);

		biggestReductionPercentage = -1;
		int biggestReductionIndex = 0;

		// get values for every cause
		for (int i = 0; i < values.size(); i++) {
			final JsonObject valObject = values.getJsonObject(i).getJsonObject("value");
			System.out.println(logMessage + "val id(" + i + ")" + valObject.toString());
			int causeReductionPercentage = valObject.getInteger("value");
			String id = valObject.getString("id");
			String walletGuid = "user-guid://" + id;
			transferToPublicWallet(causeReductionPercentage, walletGuid);

			if (causeReductionPercentage > biggestReductionPercentage) {
				biggestReductionPercentage = causeReductionPercentage;
				biggestReductionIndex = i;
			}
		}

		// get tokens won for school with biggest reduction
		logger.debug(logMessage + "applyPublicRating() school biggest reduction: " + biggestReductionIndex);

		// get wallet from wallet manager
		Future<JsonObject> readPublicWallet = Future.future();
		String walletid = values.getJsonObject(biggestReductionIndex).getJsonObject("value").getString("id");
		String walletGuid = "user-guid://" + walletid;
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", "myself");
		JsonObject body = new JsonObject().put("resource", "wallet").put("value", walletGuid);
		JsonObject identity = new JsonObject();
		msg.put("body", body);
		msg.put("identity", identity);
		logger.debug(logMessage + "applyPublicRating() school biggest reduction sending: " + msg);
		vertx.eventBus().send("wallet-cause-read", msg, res -> {
			JsonObject reply = (JsonObject) res.result().body();
			JsonObject publicWallet = reply.getJsonObject("wallet");
			logger.debug(logMessage + "applyPublicRating() publicWallet: " + publicWallet);
			readPublicWallet.complete(publicWallet);
		});

		readPublicWallet.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				// access wallet counters
				JsonObject countersObj = readPublicWallet.result().getJsonObject(WalletManagerHyperty.counters);
				// sum checkin + elearning + activity points
				int monthlyPoints = countersObj.getInteger("user-activity") + countersObj.getInteger("elearning");
				monthlyPoints += countersObj.getInteger("checkin");
				monthlyPoints /= 10;

				// apply bonus
				logger.debug(logMessage + "applyPublicRating() bonus: " + monthlyPoints);

				JsonObject msgEnergySaving = new JsonObject();
				msgEnergySaving.put("address", readPublicWallet.result().getString("address"));
				JsonObject transaction = new JsonObject();
				transaction.put("source", "energy-saving");
				transaction.put("value", monthlyPoints);
				transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
				transaction.put("description", "valid");
				transaction.put("nonce", 1);
				transaction.put("wallet2bGranted", walletid.replace("-", "") + "-wallet");
				JsonObject data = new JsonObject();
				data.put("value", biggestReductionPercentage);
				transaction.put("data", data);

				msgEnergySaving.put("transaction", transaction);
				vertx.eventBus().send("wallet-cause-transfer", msgEnergySaving);

				// reset counters
				vertx.eventBus().send("wallet-cause-reset", msgEnergySaving);
				applyPublicRating.complete();
			} else {
				// oh ! we have a problem...
			}
		});

		return applyPublicRating;
	}

	private void transferToPublicWallet(int causeReductionPercentage, String id) {

//		logger.debug("transferToPublicWallet(): " + id + "/" + causeReductionPercentage);
		Future<JsonObject> getPublicWallet = Future.future();

		// get public wallet address
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", "myself");
		JsonObject body = new JsonObject().put("resource", "wallet").put("value", id);
		JsonObject identity = new JsonObject();
		msg.put("body", body);
		msg.put("identity", identity);
		vertx.eventBus().send("wallet-cause-read", msg, res -> {
			JsonObject reply = (JsonObject) res.result().body();
//			logger.debug(logMessage + "applyPublicRating() publicWallet: " + publicWallet);
			getPublicWallet.complete(reply.getJsonObject("wallet"));
		});

		getPublicWallet.setHandler(asyncResult -> {
			// transfer to public wallet
			JsonObject msgToPublicWallet = new JsonObject();
			msgToPublicWallet.put("address", getPublicWallet.result().getString("address"));
			JsonObject transaction = new JsonObject();
			transaction.put("source", "energy-saving");
			transaction.put("value", causeReductionPercentage * 5);
			transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
			transaction.put("bonus", true);
			transaction.put("wallet2bGranted", getPublicWallet.result().getString("address"));
			JsonObject data = new JsonObject();
			data.put("value", causeReductionPercentage);
			transaction.put("data", data);
			msgToPublicWallet.put("transaction", transaction);
			vertx.eventBus().send("wallet-cause-transfer", msgToPublicWallet);
		});

	}

	int supportersTotal;
	int supportersSM;

	/**
	 * Rate energy message according to the private algorithm.
	 *
	 * @return
	 */
	private int applyPrivateRating(JsonArray values) {
		logger.debug(logMessage + "applyPrivateRating(): " + values);

		int reductionUserPercentage = 0;

		final JsonObject valueObject = values.getJsonObject(0);

		final JsonObject dataValueObject = valueObject.getJsonObject("value");

		reductionUserPercentage = dataValueObject.getInteger("value");

		int totalReductionPercentage = reductionUserPercentage;

		return totalReductionPercentage * 10;
	}

	public void onChanges(String address, String ratingType) {

		final String address_changes = address + "/changes";
		logger.info(logMessage + "onChanges-energy: waiting for changes on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			logger.info("[Energy]");
			logger.debug(logMessage + "onChanges(): received message" + message.body());
			try {
				JsonObject data = new JsonObject(message.body().toString());
				JsonObject changes = new JsonObject();

				changes.put("ratingType", ratingType);
				changes.put("message", data);
				Future<String> userGuid = getUserGuid(address);

				userGuid.setHandler(asyncResult -> {
					changes.put("guid", userGuid.result());

					logger.debug(logMessage + "onChanges(): change: " + changes.toString());

					Future<Integer> numTokens = rate(changes);
					numTokens.setHandler(res -> {
						if (numTokens.result() > 0) {
							logger.debug(logMessage + "rate(): numTokens=" + numTokens);
							mine(numTokens.result(), changes, dataSource);
						}

					});
				});

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	public Future<String> getUserGuid(String address) {

		Future<String> userID = Future.future();
		mongoClient.find(dataObjectsCollection, new JsonObject().put("objURL", address), userURLforAddress -> {
			if (userURLforAddress.result().size() == 0) {
				userID.complete("");
			}
			JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject("metadata");
			userID.complete(dataObjectInfo.getString("guid"));
		});

		return userID;
	}

	public void onNotification(JsonObject body, String streamType) {
		logger.debug("onNotification()" + body.toString());
		String from = body.getString("from");
		String guid = body.getJsonObject("identity").getJsonObject("userProfile").getString("guid");

		if (body.containsKey("external") && body.getBoolean("external")) {
			logger.debug("EXTERNAL INVITE");
			String streamID = body.getString("streamID");
			String objURL = from.split("/subscription")[0];
			Future<String> CheckURL = findDataObjectStream(objURL, guid);
			CheckURL.setHandler(asyncResult -> {
				if (CheckURL.result() == null) {

					Future<Boolean> canHandleData = checkIfCanHandleData(guid);
					Future<Boolean> persisted = persistDataObjUserURL(streamID, guid, objURL, "reporter");
					List<Future> futures = new ArrayList<>();
					futures.add(canHandleData);
					futures.add(persisted);
					CompositeFuture.all(futures).setHandler(done -> {
						if (done.succeeded()) {
							boolean res1 = done.result().resultAt(0);
							boolean res2 = done.result().resultAt(1);
							if (res1 && res2) {
								onChanges(objURL, streamType);
							}
						} else {
							onChanges(objURL, streamType);
						}
					});

				}
			});

		}
	}

	@Override
	public void resumeDataObjects(String ratingType) {

		JsonObject tofind = new JsonObject().put("ratingType", ratingType);
		logger.debug("Resuming dataobjects ratingType-> " + ratingType);
		mongoClient.find(dataObjectsCollection, tofind, allDataObjects -> {
			logger.debug("GetAllDataObjects complete for energy - " + allDataObjects.result().size());
			for (int i = 0; i < allDataObjects.result().size(); i++) {
				String dataObjectUrl = allDataObjects.result().get(i).getString("objURL");
				if (dataObjectUrl.contains("school")) {
					onChanges(dataObjectUrl, ratingPublic);
				} else {
					onChanges(dataObjectUrl, ratingPrivate);
				}

			}
		});
	}

}
