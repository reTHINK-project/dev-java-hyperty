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
	 * Number of tokens awarded after walking 1 km.
	 */
	private int tokensWalkingKm;
	/**
	 * Number of tokens awarded after biking 1 km.
	 */
	private int tokensBikingKm;

	private CountDownLatch checkinLatch;
	private CountDownLatch findUserID;
	private String userIDToReturn = null;

	private String dataSource = "user-activity";

	@Override
	public void start() {
		super.start();

		// read config
		tokensWalkingKm = config().getInteger("tokens_per_walking_km");
		tokensBikingKm = config().getInteger("tokens_per_biking_km");
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
			// TODO - latch
			JsonObject query = new JsonObject().put("user", user);
			mongoClient.find(collection, query, result -> {
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
		// TODO - timestamp from message?
		Long currentTimestamp = new Date().getTime();

		// check unprocessed sessions

		JsonObject activityMessage = (JsonObject) data;
		System.out.println("USER ACTIVITY MESSAGE " + activityMessage.toString());
		String user = activityMessage.getString("userID");
		String activity = activityMessage.getString("activity");
		int distance = activityMessage.getInteger("distance");
		// String sessionID = activityMessage.getString("session");

		JsonArray unprocessed = getUnprocessedSessions(user, activity);

		// persist in MongoDB
		activityMessage.remove("type");
		activityMessage.remove("from");
		activityMessage.remove("identity");
		activityMessage.put("processed", false);
		if (distance > 500) {
			activityMessage.put("processed", true);
		}
		// TODO - ID param
		persistData(dataSource, user, currentTimestamp, "1", null, activityMessage);

		// check if there are unaccounted sessions
		int totalDistance = sumSessionsDistance(distance, unprocessed);
		if (totalDistance > 500) {
			processSessions(unprocessed.add(activityMessage), user);
		}

		checkinLatch = new CountDownLatch(1);
		new Thread(() -> {
			if (totalDistance >= 500) {
				tokenAmount = getTokensForDistance(activity, totalDistance);
			}
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
				System.out.println("Document with ID:" + id + " was updated");
			});

		});
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
							changes.put("activity", type);
							changes.put("distance", obj.getDouble("value"));
							break;
						default:
							break;
						}
					}
					changes.put("userID", getUserURL(address));
					System.out.println("CHANGES" + changes.toString());

					int numTokens = rate(changes);
					mine(numTokens, changes, "user_activity");

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
			mongoClient.find(dataObjectsCollection, new JsonObject().put("url", address), userURLforAddress -> {		
				System.out.println("2 - find Dataobjects size->" + userURLforAddress.result().size());
				JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject("metadata");
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
