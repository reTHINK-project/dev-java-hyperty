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

	private static final String logMessage = "[UserActivityRating] ";

	/**
	 * Number of tokens awarded after walking 1 km.
	 */
	private int tokensWalkingKm;
	/**
	 * Number of tokens awarded after biking 1 km.
	 */
	private int tokensBikingKm;

	/**
	 * Number of tokens awarded after walking 1 km.
	 */
	private int tokensBikesharingKm;
	/**
	 * Number of tokens awarded after biking 1 km.
	 */
	private int tokensEvehicleKm;

	private CountDownLatch checkinLatch;

	private String dataSource = "user-activity";

	@Override
	public void start() {
		super.start();

		System.out.println(logMessage + "start()");

		// read config
		tokensWalkingKm = config().getInteger("tokens_per_walking_km");
		tokensBikingKm = config().getInteger("tokens_per_biking_km");
		tokensBikesharingKm = config().getInteger("tokens_per_bikesharing_km");
		tokensEvehicleKm = config().getInteger("tokens_per_evehicle_km");
	}

	int tokenAmount;

	/**
	 * Get unprocessed sessions.
	 * 
	 * @param user
	 * @param activity
	 * @return
	 */
	JsonArray getUnprocessedSessions(String user, String activity) {

		CountDownLatch setupLatch = new CountDownLatch(1);

		JsonArray unprocessed = new JsonArray();

		new Thread(() -> {
			JsonObject query = new JsonObject().put("user", user);
			System.out.println(logMessage + "SEARCHING in " + collection + " for " + query);
			mongoClient.find(collection, query, result -> {
				System.out.println(logMessage + "SEARCHING result " + result.result());
				JsonObject currentDocument = result.result().get(0);
				// get sessions
				JsonArray sessions = currentDocument.getJsonArray(dataSource);

				// filter unprocessed sessions
				for (int i = 0; i < sessions.size(); i++) {
					JsonObject currentSession = sessions.getJsonObject(i);
					if (!currentSession.getBoolean("processed")
							&& currentSession.getString("activity").equals(activity)) {
						unprocessed.add(currentSession);
					}
				}
				setupLatch.countDown();

			});
		}).start();

		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return unprocessed;
	}

	int sumSessionsDistance(int start, JsonArray sessions) {
		int count = start;
		for (int i = 0; i < sessions.size(); i++) {
			count += sessions.getJsonObject(i).getDouble("distance");
		}
		return count;
	}

	@Override
	int rate(Object data) {

		// reset latch
		tokenAmount = -3;
		Long currentTimestamp = new Date().getTime();

		// check unprocessed sessions
		JsonObject activityMessage = (JsonObject) data;
		System.out.println(logMessage + " message: " + activityMessage.toString());
		String user = activityMessage.getString("guid");
		String activity = activityMessage.getString("activity");
		int distance = activityMessage.getInteger("distance");

		JsonArray unprocessed = getUnprocessedSessions(user, activity);

		// persist in MongoDB
		activityMessage.remove("type");
		activityMessage.remove("from");
		activityMessage.remove("identity");
		activityMessage.put("processed", false);
		// min distance according to activity
		if (checkMinDistance(activity, distance)) {
			activityMessage.put("processed", true);
		}
		persistData(dataSource, user, currentTimestamp, "1", null, activityMessage);

		// check if there are unaccounted sessions
		int totalDistance = sumSessionsDistance(distance, unprocessed);
		if (checkMinDistance(activity, distance)) {
			processSessions(unprocessed.add(activityMessage), user);
		}

		checkinLatch = new CountDownLatch(1);
		new Thread(() -> {
			if (checkMinDistance(activity, totalDistance)) {
				tokenAmount = getTokensForDistance(activity, totalDistance);
			}
			checkinLatch.countDown();
		}).start();

		try {
			checkinLatch.await(5L, TimeUnit.SECONDS);
			return tokenAmount;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return tokenAmount;
	}

	private void processSessions(JsonArray sessionsToProcess, String user) {
		JsonObject query = new JsonObject().put("user", user);
		mongoClient.find(collection, query, result -> {
			JsonObject currentDocument = result.result().get(0);
			// get sessions
			JsonArray sessions = currentDocument.getJsonArray(dataSource);

			// filter unprocessed sessions
			for (int i = 0; i < sessions.size(); i++) {
				JsonObject currentSession = sessions.getJsonObject(i);
				if (!currentSession.getBoolean("processed") && sessionsToProcess.contains(currentSession)) {
					currentSession.put("processed", true);
				}
			}

			// update only corresponding data source
			mongoClient.findOneAndReplace(collection, query, currentDocument, id -> {
				System.out.println(logMessage + "Document with ID:" + id + " was updated");
			});

		});
	}

	/**
	 * Get amount of tokens for distance/activity.
	 * 
	 * @param activity
	 * @param distance
	 *            in meters
	 * @return
	 */
	private boolean checkMinDistance(String activity, int distance) {
		System.out.println(logMessage + "checkMinDistance: " + distance);
		switch (activity) {
		case "user_walking_context":
		case "user_biking_context":
			return distance >= 500;
		case "user_bikeSharing_context":
			return distance >= 1000;
		case "user_eVehicles_context":
			return distance >= 2000;
		default:
			return false;
		}
	}

	/**
	 * Get amount of tokens for distance/activity.
	 * 
	 * @param activity
	 *            walking/biking
	 * @param distance
	 *            in meters
	 * @return
	 */
	private int getTokensForDistance(String activity, int distance) {
		System.out.println("getTokensForDistance: " + distance);
		int tokens = 0;
		switch (activity) {
		case "user_walking_context":
			tokens = distance / 500 * (tokensWalkingKm / 2);
		case "user_biking_context":
			tokens = distance / 500 * (tokensBikingKm / 2);
			break;
		case "user_bikeSharing_context":
			tokens = distance / 1000 * tokensBikesharingKm;
		case "user_eVehicles_context":
			tokens = distance / 2000 * (tokensEvehicleKm * 2);
			break;
		default:
			break;
		}
		return tokens;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		System.out.println("waiting for changes to user activity on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			System.out.println("User activity on changes msg: " + message.body().toString());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					JsonObject changes = new JsonObject();

					for (int i = 0; i < data.size(); i++) {
						final JsonObject obj = data.getJsonObject(i);
						final String type = obj.getString("type");
						switch (type) {
						case "user_walking_context":
						case "user_biking_context":
						case "user_bikeSharing_context":
						case "user_eVehicles_context":
							changes.put("activity", type);
							changes.put("distance", obj.getDouble("value"));
							break;
						default:
							break;
						}
					}
					changes.put("guid", getUserURL(address));
					System.out.println("CHANGES" + changes.toString());

					int numTokens = rate(changes);
					mine(numTokens, changes, "user_activity");

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

}
