package tokenRating;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The UserActivityRatingHyperty observes user's activities and rewards with
 * tokens the individual wallet.
 */
public class UserActivityRatingHyperty extends AbstractTokenRatingHyperty {

	/**
	 * Number of tokens awarded per activity.
	 */
	private int userActivityTokens;

	private CountDownLatch checkinLatch;
	private CountDownLatch findUserID;
	private String userIDToReturn = null;

	@Override
	public void start() {
		super.start();

		// read config
		userActivityTokens = config().getInteger("tokens_per_checkin");
	}

	int tokenAmount;

	@Override
	int rate(Object data) {
		// reset latch

		tokenAmount = -1;
		Long currentTimestamp = new Date().getTime();

		JsonObject activityMessage = (JsonObject) data;
		System.out.println("USER ACTIVITY MESSAGE " + activityMessage.toString());
		String user = activityMessage.getString("userID");
		String activity = activityMessage.getString("activity");
		Double distance = activityMessage.getDouble("distance");
		String sessionID = activityMessage.getString("session");

		checkinLatch = new CountDownLatch(1);
		new Thread(() -> {
			tokenAmount = getTokensForDistance(activity, distance);
			checkinLatch.countDown();
		}).start();

		try {
			checkinLatch.await(5L, TimeUnit.SECONDS);
			return tokenAmount;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		return tokenAmount;
	}

	// TODO - implement algo
	private int getTokensForDistance(String activity, Double distance) {
		int tokens = -1;
		switch (activity) {
		case "walking":
			tokens = (int) (distance/10);
			break;
		case "running":
			tokens = (int) (distance/100);
			break;
		case "biking":
			tokens = (int) (distance/200);
			break;

		default:
			break;
		}
		return tokens;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		System.out.println("waiting for changes on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 3) {
					JsonObject changes = new JsonObject();

					for (int i = 0; i < data.size(); i++) {
						final JsonObject obj = data.getJsonObject(i);
						final String name = obj.getString("name");
						switch (name) {
						case "latitude":
						case "longitude":
							changes.put(name, obj.getFloat("value"));
							break;
						case "checkin":
							changes.put("shopID", obj.getString("value"));
							break;
						default:
							break;
						}
					}
					changes.put("userID", getUserURL(address));
					System.out.println("CHANGES" + changes.toString());

					int numTokens = rate(changes);

					/*
					 * if (numTokens == -1) {
					 * System.out.println("User is not inside any shop or already checkIn"); } else
					 * { System.out.println("User is close"); mine(numTokens, changes, "checkin"); }
					 */
					if (numTokens < 0) {
						System.out.println("User is not inside any shop or already checkIn");
					} else {
						System.out.println("User is close");
					}

					mine(numTokens, changes, "checkin");

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	public String getUserURL(String address) {

		userIDToReturn = null;
		findUserID = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find("dataobjects", new JsonObject().put(address, new JsonObject().put("$exists", true)),
					userURLforAddress -> {
						System.out.println("2 - Received shop info");
						JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject(address);
						userIDToReturn = dataObjectInfo.getString("userURL");
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

}
