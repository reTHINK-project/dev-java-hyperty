package tokenRating;

import java.util.Date;

import io.vertx.core.Future;
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

	/**
	 * Daily walking limit (meters).
	 */
	private int mtWalkPerDay;
	/**
	 * Daily biking limit (meters).
	 */
	private int mtBikePerDay;

	private String dataSource = "user-activity";

	@Override
	public void start() {
		super.start();

		logger.debug(logMessage + "start()");

		// read config
		tokensWalkingKm = config().getInteger("tokens_per_walking_km");
		tokensBikingKm = config().getInteger("tokens_per_biking_km");
		tokensBikesharingKm = config().getInteger("tokens_per_bikesharing_km");
		tokensEvehicleKm = config().getInteger("tokens_per_evehicle_km");
		mtWalkPerDay = config().getInteger("mtWalkPerDay");
		mtBikePerDay = config().getInteger("mtBikePerDay");
		
		ratingType = "user-activity";
		resumeDataObjects(ratingType);
		
	}

	/**
	 * Get unprocessed sessions.
	 *
	 * @param user
	 * @param activity
	 * @return
	 */
	Future<JsonArray> getUnprocessedSessions(String user, String activity) {

		Future<JsonArray> resultFuture = Future.future();

		JsonArray unprocessed = new JsonArray();

		JsonObject query = new JsonObject().put("user", user);
		mongoClient.find(collection, query, result -> {
			JsonObject currentDocument = result.result().get(0);
			JsonArray sessions = currentDocument.getJsonArray(dataSource);

			// filter unprocessed sessions
			for (int i = 0; i < sessions.size(); i++) {
				JsonObject currentSession = sessions.getJsonObject(i);
				if (!currentSession.getBoolean("processed") && currentSession.getString("activity").equals(activity)) {
					unprocessed.add(currentSession);
				}
			}
			resultFuture.complete(unprocessed);

		});

		logger.debug(logMessage + "user unprocessed sessions: " + unprocessed.toString());
		return resultFuture;
	}

	int sumSessionsDistanceTruncate(String activity, int start, JsonArray sessions) {
		int totalDistanceMeters = start;
		for (int i = 0; i < sessions.size(); i++) {
			totalDistanceMeters += sessions.getJsonObject(i).getDouble("distance");
		}
		logger.debug(logMessage + "sumSessionsDistance(): " + totalDistanceMeters);
		switch (activity) {
		case "user_walking_context":
			if (totalDistanceMeters > mtWalkPerDay)
				return mtWalkPerDay;
		case "user_biking_context":
			if (totalDistanceMeters > mtBikePerDay)
				return mtBikePerDay;
		}
		return totalDistanceMeters;
	}

	@Override
	Future<Integer> rate(Object data) {

		// invalid-short-distance
		int tokenAmount = -3;
		Long currentTimestamp = new Date().getTime();
		JsonObject activityMessage = (JsonObject) data;
		logger.debug(logMessage + " message: " + activityMessage.toString());
		String user = activityMessage.getString("guid");
		String activity = activityMessage.getString("activity");
		int currentSessionDistance = activityMessage.getInteger("distance");

		Future<Integer> result = Future.future();
		Future<JsonArray> unprocessed = getUnprocessedSessions(user, activity);
		unprocessed.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				// persist in MongoDB
				activityMessage.remove("type");
				activityMessage.remove("from");
				activityMessage.remove("identity");
				activityMessage.put("processed", false);
				// min distance according to activity
				if (checkMinDistance(activity, currentSessionDistance)) {
					activityMessage.put("processed", true);
				}
				persistData(dataSource, user, currentTimestamp, "1", null, activityMessage);
				/*
				 * if ((activity.equals("user_walking_context") ||
				 * activity.equals("user_biking_context")) && currentSessionDistance < 300) {
				 * //logger.debug(logMessage + "distance < 300!"); return tokenAmount; }
				 */

				// check if distance is invalid
				// get total distance (unprocessed sessions)
				int totalDistance = sumSessionsDistanceTruncate(activity, currentSessionDistance, asyncResult.result());

				if (checkMinDistance(activity, totalDistance)) {
					processSessions(asyncResult.result().add(activityMessage), user);
					result.complete(getTokensForDistance(activity, totalDistance));
				} else {
					result.complete(tokenAmount);
				}
			} else {
				// oh ! we have a problem...
			}
		});

		return result;

	}

	/**
	 * Check if the current session doesn't exceed the max distance.
	 *
	 * @param activity
	 * @param currentSessionDistance
	 * @return
	 */
	private boolean checkValidDistance(String activity, int currentSessionDistance) {
		switch (activity) {
		case "user_walking_context":
			return currentSessionDistance <= mtWalkPerDay;
		case "user_biking_context":
			return currentSessionDistance <= mtBikePerDay;
		default:
			return false;
		}

	}

	/**
	 * Turn unprocessed sessions into processed ones.
	 *
	 * @param sessionsToProcess
	 * @param user
	 */
	private Future<Void> processSessions(JsonArray sessionsToProcess, String user) {

		Future<Void> resultFuture = Future.future();

		JsonObject query = new JsonObject().put("user", user);
		mongoClient.find(collection, query, result -> {
			JsonObject currentDocument = result.result().get(0);
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
				logger.debug(logMessage + "processSessions: document with ID " + id + "was updated");
				resultFuture.complete();
			});

		});

		return resultFuture;

	}

	/**
	 * Get amount of tokens for distance/activity.
	 *
	 * @param activity
	 * @param distance in meters
	 * @return
	 */
	private boolean checkMinDistance(String activity, int distance) {
		logger.debug(logMessage + "checkMinDistance: " + distance);
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
	 * @param activity walking/biking
	 * @param distance in meters
	 * @return
	 */
	private int getTokensForDistance(String activity, int distance) {

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
		logger.debug(logMessage + "getTokensForDistance(): " + activity + "/" + distance + " - " + tokens);
		return tokens;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		logger.info("[USER-ACTIVITY] waiting for changes on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			logger.info("[UserActivity] data");
			logger.debug("[User activity] data msg-> " + message.body().toString());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					JsonObject changes = new JsonObject();


					final JsonObject obj = data.getJsonObject(0);
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


					if (type.equals("user_eVehicles_context")) {
						Future<String> userURL = getUserURL(address, "objURL");
						userURL.setHandler(asyncResult -> {
							changes.put("guid", userURL.result());
							logger.debug(logMessage + "changes: " + changes.toString());

							Future<Integer> numTokens = rate(changes);
							numTokens.setHandler(res -> {
								if (numTokens.succeeded()) {
									mine(numTokens.result(), changes, "e-driving");
								} else {
									// oh ! we have a problem...
								}
							});
						});
					} else {
						Future<String> userURL = getUserURL(address, "url");
						userURL.setHandler(asyncResult -> {
							changes.put("guid", userURL.result());
							logger.debug(logMessage + "changes: " + changes.toString());

							Future<Integer> numTokens = rate(changes);
							numTokens.setHandler(res -> {
								if (numTokens.succeeded()) {
									mine(numTokens.result(), changes, "user-activity");
								} else {
									// oh ! we have a problem...
								}
							});
						});
					}
					


				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

}
