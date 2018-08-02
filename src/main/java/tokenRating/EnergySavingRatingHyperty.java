package tokenRating;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtils;
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

			System.out.println(logMessage + "new message -> " + message.body().toString());
			if (mandatoryFieldsValidator(message)) {
				final JsonObject identity = new JsonObject(message.body().toString()).getJsonObject("identity");
				final String type = new JsonObject(message.body().toString()).getString("type");
				final String from = new JsonObject(message.body().toString()).getString("from");
				final String guid = identity.getJsonObject("userProfile").getString("guid");
				switch (type) {

				case "create":
					if (from.contains("/subscription")) {
						/*String address = from.split("/subscription")[0];
						String userID = getUserURL(address);
						// checks there is no subscription yet for the User CGUID URL.
						if (userID == null) {
							if (persistDataObjUserURL(address, guid, "reporter") && checkIfCanHandleData(guid)) {
								onChanges(address, streamType);
							}
						}*/
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
	int rate(Object data) {
		// reset latch
		System.out.println(logMessage + "rate(): " + data.toString());
		Long currentTimestamp = new Date().getTime();

		int tokenAmount = -1;

		// data contains shopID, users's location
		String ratingType = ((JsonObject) data).getString("ratingType");
		String user = ((JsonObject) data).getString("guid");
		JsonObject energyMessage = ((JsonObject) data).getJsonObject("message");
		// parse values
		JsonArray values = energyMessage.getJsonArray("values");

		// rating algorithm
		switch (ratingType) {
		case ratingPublic:
			applyPublicRating(values);
			tokenAmount = 0;
			break;
		case ratingPrivate:
			tokenAmount = applyPrivateRating(values);
			break;
		default:
			break;
		}
		persistData(dataSource, user, currentTimestamp, "1", null, null);

		return tokenAmount;
	}

	JsonObject publicWallet;
	int biggestReductionIndex = 0;

	/**
	 * Rate energy message according to the public algorithm.
	 * 
	 * @return
	 */
	private int applyPublicRating(JsonArray values) {

		CountDownLatch applyPublicRating = new CountDownLatch(1);

		new Thread(() -> {

			System.out.println(logMessage + "applyPublicRating(): " + values);

			int reductionCausePercentage = -1;
			biggestReductionIndex = 0;

			// get values for every cause
			for (int i = 0; i < values.size(); i++) {
				final JsonObject value = values.getJsonObject(i);
				JsonObject valObject = value.getJsonObject("value");
				int aux = valObject.getInteger("value");
				String id = valObject.getString("id");

				CountDownLatch getPublicWallet = new CountDownLatch(1);

				// get public wallet address
				new Thread(() -> {
					JsonObject msg = new JsonObject();
					msg.put("type", "read");
					msg.put("from", "myself");
					JsonObject body = new JsonObject().put("resource", "wallet").put("value", id);
					JsonObject identity = new JsonObject();
					msg.put("body", body);
					msg.put("identity", identity);
					vertx.eventBus().send("wallet-cause-read", msg, res -> {
						JsonObject reply = (JsonObject) res.result().body();
						publicWallet = reply.getJsonObject("wallet");
						System.out.println(logMessage + "applyPublicRating() publicWallet: " + publicWallet);
						getPublicWallet.countDown();

					});
				}).start();

				try {
					getPublicWallet.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// transfer to public wallet
				JsonObject msg = new JsonObject();
				msg.put("address", publicWallet.getString("address"));
				JsonObject transaction = new JsonObject();
				transaction.put("source", "energy-saving");
				transaction.put("value", aux * 5);
				transaction.put("date", DateUtils.getCurrentDateAsISO8601());
				msg.put("transaction", transaction);
				vertx.eventBus().send("wallet-cause-transfer", msg);

				if (aux > reductionCausePercentage) {
					reductionCausePercentage = aux;
					biggestReductionIndex = i;
				}
			}

			// get tokens won for school with biggest reduction
			System.out.println(logMessage + "applyPublicRating() school biggest reduction: " + biggestReductionIndex);

			// get wallet from wallet manager
			CountDownLatch readPublicWallet = new CountDownLatch(1);

			new Thread(() -> {

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
					publicWallet = reply.getJsonObject("wallet");
					System.out.println(logMessage + "applyPublicRating() publicWallet: " + publicWallet);
					readPublicWallet.countDown();
				});
			}).start();

			try {
				readPublicWallet.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// access wallet counters
			JsonObject countersObj = publicWallet.getJsonObject(WalletManagerHyperty.counters);
			// sum checkin + elearning + activity points
			int monthlyPoints = countersObj.getInteger("user-activity") + countersObj.getInteger("elearning");
			monthlyPoints += countersObj.getInteger("checkin");
			monthlyPoints /= 10;

			// apply bonus
			System.out.println(logMessage + "applyPublicRating() bonus: " + monthlyPoints);

			JsonObject msg = new JsonObject();
			msg.put("address", publicWallet.getString("address"));
			JsonObject transaction = new JsonObject();
			transaction.put("source", "energy-saving");
			transaction.put("value", monthlyPoints);
			transaction.put("date", DateUtils.getCurrentDateAsISO8601());
			msg.put("transaction", transaction);
			vertx.eventBus().send("wallet-cause-transfer", msg);

			// reset counters
			vertx.eventBus().send("wallet-cause-reset", msg);
			applyPublicRating.countDown();

		}).start();

		try

		{
			applyPublicRating.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return 0;
	}

	int supportersTotal;
	int supportersSM;

	/**
	 * Rate energy message according to the private algorithm.
	 * 
	 * @return
	 */
	private int applyPrivateRating(JsonArray values) {
		System.out.println(logMessage + "applyPrivateRating(): " + values);

		int reductionUserPercentage = 0;

		final JsonObject valueObject = values.getJsonObject(0);
		
		final JsonObject dataValueObject = valueObject.getJsonObject("value");
		
		reductionUserPercentage = dataValueObject.getInteger("value");
		

		int totalReductionPercentage = reductionUserPercentage;

		return totalReductionPercentage * 10;
	}


	public void onChanges(String address, String ratingType) {

		final String address_changes = address + "/changes";
		System.out.println(logMessage + "onChanges(): waiting for changes on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			System.out.println(logMessage + "onChanges(): received message" + message.body());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					JsonObject changes = new JsonObject();

					changes.put("ratingType", ratingType);
					changes.put("message", data.getJsonObject(0));
					changes.put("guid", getUserGuid(address));

					System.out.println(logMessage + "onChanges(): change: " + changes.toString());

					int numTokens = rate(changes);
					System.out.println(logMessage + "rate(): numTokens=" + numTokens);
					if (numTokens > 0) {
						mine(numTokens, changes, dataSource);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}
	
	public String getUserGuid(String address) {

		CountDownLatch findUserID = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(dataObjectsCollection, new JsonObject().put("objURL", address), userURLforAddress -> {
				if (userURLforAddress.result().size() == 0) {
					findUserID.countDown();
					return;
				}
				JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject("metadata");
				userIDToReturn = dataObjectInfo.getString("guid");
				findUserID.countDown();
			});
		}).start();

		try {
			findUserID.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch");
			return userIDToReturn;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return userIDToReturn;
	}


	public void onNotification(JsonObject body, String streamType) {
		System.out.println("HANDLING" + body.toString());
		String from = body.getString("from");
		String guid = body.getJsonObject("identity").getJsonObject("userProfile").getString("guid");

		if (body.containsKey("external") && body.getBoolean("external")) {
			System.out.println("EXTERNAL INVITE");
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
