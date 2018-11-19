package tokenRating;

import java.util.Date;

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

		this.eb.<JsonObject>consumer(ratingPublic, onMessage(ratingPublic));
		this.eb.<JsonObject>consumer(ratingPrivate, onMessage(ratingPrivate));

	}

	public Handler<Message<JsonObject>> onMessage(String streamType) {

		return message -> {

			// System.out.println(logMessage + "new message -> " +
			// message.body().toString());
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
		// reset latch
		// System.out.println(logMessage + "rate(): " + data.toString());
		Long currentTimestamp = new Date().getTime();

		// TODO : -1
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

	int biggestReductionIndex = 0;

	int reductionCausePercentage = -1;

	/**
	 * Rate energy message according to the public algorithm.
	 *
	 * @return
	 */
	private Future<Void> applyPublicRating(JsonArray values) {

		Future<Void> applyPublicRating = Future.future();

		// System.out.println(logMessage + "applyPublicRating(): " + values);

		reductionCausePercentage = -1;
		biggestReductionIndex = 0;

		// get values for every cause
		for (int i = 0; i < values.size(); i++) {
			final JsonObject value = values.getJsonObject(i);
			JsonObject valObject = value.getJsonObject("value");
			int aux = valObject.getInteger("value");
			String id = valObject.getString("id");

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
				// System.out.println(logMessage + "applyPublicRating() publicWallet: " +
				// publicWallet);
				getPublicWallet.complete(reply.getJsonObject("wallet"));
			});

			getPublicWallet.setHandler(asyncResult -> {
				if (asyncResult.succeeded()) {
					// transfer to public wallet
					JsonObject msgToPublicWallet = new JsonObject();
					msgToPublicWallet.put("address", getPublicWallet.result().getString("address"));
					JsonObject transaction = new JsonObject();
					transaction.put("source", "energy-saving");
					transaction.put("value", aux * 5);
					transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
					msg.put("transaction", transaction);
					vertx.eventBus().send("wallet-cause-transfer", msgToPublicWallet);

					if (aux > reductionCausePercentage) {
						reductionCausePercentage = aux;
						biggestReductionIndex = i;
					}
				} else {
					// oh ! we have a problem...
				}
			});

		}

		// get tokens won for school with biggest reduction
		// System.out.println(logMessage + "applyPublicRating() school biggest
		// reduction: " + biggestReductionIndex);

		// get wallet from wallet manager
		Future<JsonObject> readPublicWallet = Future.future();

		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", "myself");
		JsonObject body = new JsonObject().put("resource", "wallet").put("value",
				Integer.toString(biggestReductionIndex));
		JsonObject identity = new JsonObject();
		msg.put("body", body);
		msg.put("identity", identity);
		vertx.eventBus().send("wallet-cause-read", msg, res -> {
			JsonObject reply = (JsonObject) res.result().body();
			// System.out.println(logMessage + "applyPublicRating() publicWallet: " +
			// publicWallet);
			readPublicWallet.complete(reply.getJsonObject("wallet"));
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
				// System.out.println(logMessage + "applyPublicRating() bonus: " +
				// monthlyPoints);

				JsonObject msgEnergySaving = new JsonObject();
				msgEnergySaving.put("address", readPublicWallet.result().getString("address"));
				JsonObject transaction = new JsonObject();
				transaction.put("source", "energy-saving");
				transaction.put("value", monthlyPoints);
				transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
				msg.put("transaction", transaction);
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

	int supportersTotal;
	int supportersSM;

	/**
	 * Rate energy message according to the private algorithm.
	 *
	 * @return
	 */
	private int applyPrivateRating(JsonArray values) {
		// System.out.println(logMessage + "applyPrivateRating(): " + values);

		int reductionUserPercentage = 0;

		final JsonObject valueObject = values.getJsonObject(0);

		final JsonObject dataValueObject = valueObject.getJsonObject("value");

		reductionUserPercentage = dataValueObject.getInteger("value");

		int totalReductionPercentage = reductionUserPercentage;

		return totalReductionPercentage * 10;
	}

	public void onChanges(String address, String ratingType) {

		final String address_changes = address + "/changes";
		// System.out.println(logMessage + "onChanges(): waiting for changes on ->" +
		// address_changes);
		eb.consumer(address_changes, message -> {
			System.out.println("[Energy]");
			// System.out.println(logMessage + "onChanges(): received message" +
			// message.body());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					JsonObject changes = new JsonObject();

					changes.put("ratingType", ratingType);
					changes.put("message", data.getJsonObject(0));
					changes.put("guid", getUserGuid(address));

					// System.out.println(logMessage + "onChanges(): change: " +
					// changes.toString());

					Future<Integer> numTokens = rate(changes);
					numTokens.setHandler(asyncResult -> {
						if (asyncResult.succeeded()) {
							if (numTokens.result() > 0) {
								mine(numTokens.result(), changes, dataSource);
							}
						} else {
							// oh ! we have a problem...
						}
					});
					// System.out.println(logMessage + "rate(): numTokens=" + numTokens);

				}
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
		// System.out.println("HANDLING" + body.toString());
		String from = body.getString("from");
		String guid = body.getJsonObject("identity").getJsonObject("userProfile").getString("guid");

		if (body.containsKey("external") && body.getBoolean("external")) {
			// System.out.println("EXTERNAL INVITE");
			String streamID = body.getString("streamID");
			String objURL = from.split("/subscription")[0];
			String CheckURL = findDataObjectStream(objURL, guid);
			if (CheckURL == null) {
				if (persistDataObjUserURL(streamID, guid, objURL, "reporter") && checkIfCanHandleData(guid)) {
					onChanges(objURL, streamType);
				}
			} else {
				onChanges(objURL, streamType);
			}

		}
	}

}
