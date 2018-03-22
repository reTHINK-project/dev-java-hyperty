package token_rating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The Check-in Rating Hyperty observes user's check-in location and reward with
 * tokens the individual wallet in case it matches with some place. *
 */
public class CheckInRatingHyperty extends AbstractTokenRatingHyperty {

	/**
	 * Number of tokens awarded per checkin.
	 */
	private int checkInTokens;
	/**
	 * Max difference (in meters) between user and store location
	 */
	private int checkin_radius;
	/**
	 * Min difference (in hours) between current and last checkin times.
	 */
	private int min_frequency;

	private String shopsCollection = "shops";
	private String dataSource = "checkin";
	private JsonArray shops = null;
	private String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";

	private CountDownLatch checkinLatch;

	@Override
	public void start() {
		super.start();

		// read config
		checkInTokens = config().getInteger("tokens_per_checkin");
		checkin_radius = config().getInteger("checkin_radius");
		min_frequency = config().getInteger("min_frequency");

		// fetch shops from mongo and save them
		fetchStoreData();

		// reply with shops info
		vertx.eventBus().consumer(shopsInfoStreamAddress, message -> {
			message.reply(shops);
		});

	}

	/**
	 * Fetch store locations from MongoDB.
	 */
	private void fetchStoreData() {

		mongoClient.find(shopsCollection, new JsonObject(), res -> {
			shops = new JsonArray(res.result());
		});

	}

	int tokenAmount;

	@Override
	int rate(Object data) {
		// reset latch
		System.out.println("1 - Rating");

		tokenAmount = -1;
		Long currentTimestamp = new Date().getTime();

		// data contains shopID, users's location
		JsonObject checkInMessage = (JsonObject) data;
		String user = checkInMessage.getString("userID");
		String shopID = checkInMessage.getString("shopID");
		Double userLatitude = checkInMessage.getDouble("latitude");
		Double userLongitude = checkInMessage.getDouble("longitude");

		checkinLatch = new CountDownLatch(1);
		new Thread(() -> {
			System.out.println("2 - Started thread");
			// get shop with that ID
			mongoClient.find(shopsCollection, new JsonObject().put("id", shopID), shopForIdResult -> {
				System.out.println("2 - Received shop info");
				JsonObject shopInfo = shopForIdResult.result().get(0);
				boolean validPosition = validateUserPosition(user, userLatitude, userLongitude, shopInfo);
				if (!validPosition) {
					checkinLatch.countDown();
					return;
				}
				validateCheckinTimestamps(user, shopID, currentTimestamp);
				checkinLatch.countDown();
			});
		}).start();

		try {
			checkinLatch.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch");
			return tokenAmount;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return tokenAmount;

	}

	private void validateCheckinTimestamps(String user, String shopID, long currentTimestamp) {

		// get previous checkin from that user for that rating source
		mongoClient.find(collection, new JsonObject().put("user", user), result -> {

			// access checkins data source
			JsonObject userRates = result.result().get(0);
			JsonArray checkInRates = userRates.getJsonArray(dataSource);
			// check ins for that store
			ArrayList<JsonObject> a = (ArrayList<JsonObject>) checkInRates.getList();
			List<JsonObject> rrr = (List<JsonObject>) a.stream() // convert list to stream
					.filter(element -> shopID.equals(element.getString("id"))).collect(Collectors.toList());
			if (rrr.size() == 0) {
				System.out.println("User never went to this shop");
				persistData(dataSource, user, currentTimestamp, shopID, userRates);
			} else {
				// order by timestamp
				Collections.sort(rrr, new Comparator<JsonObject>() {
					@Override
					public int compare(final JsonObject lhs, JsonObject rhs) {
						if (lhs.getDouble("timestamp") > rhs.getDouble("timestamp")) {
							return -1;
						} else {
							return 1;
						}
					}
				});

				double lastVisitTimestamp = rrr.get(0).getDouble("timestamp");
				if (lastVisitTimestamp + (min_frequency * 60 * 60) <= currentTimestamp) {
					System.out.println("continue");
					persistData(dataSource, user, currentTimestamp, shopID, userRates);
				} else {
					System.out.println("invalid");
				}
			}

		});

	}

	/**
	 * Check if user is inside shop boundaries.
	 * 
	 * @param userLatitude
	 * @param userLongitude
	 * @param shopInfo
	 */
	private boolean validateUserPosition(String user, Double userLatitude, Double userLongitude, JsonObject shopInfo) {
		// access location
		JsonObject location = shopInfo.getJsonObject("location");
		Double latitude = location.getDouble("degrees-latitude");
		Double longitude = location.getDouble("degrees-longitude");

		// check if user in range
		if (getDifferenceBetweenGPSCoordinates(userLatitude, userLongitude, latitude, longitude) <= checkin_radius) {
			System.out.println("2 - User is close to store");

			// persist check in
			// persistData(dataSource, user, new Date().getTime(), shopID);
			tokenAmount = checkInTokens;
			return true;
		} else {
			System.out.println("2 - User is far from store");
			return false;
		}

	}

	/**
	 * Get difference (in meters) between two points (in GPS coordinates)
	 * 
	 * @param userLatitude
	 * @param userLongitude
	 * @param shopLatitude
	 * @param shopLongitude
	 * @return
	 */
	private double getDifferenceBetweenGPSCoordinates(double userLatitude, double userLongitude, double shopLatitude,
			double shopLongitude) {
		int earthRadiusKm = 6371;

		double dLat = degreesToRadians(shopLatitude - userLatitude);
		double dLon = degreesToRadians(shopLongitude - userLongitude);

		double lat1 = degreesToRadians(userLatitude);
		double lat2 = degreesToRadians(shopLatitude);

		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distanceInMeters = earthRadiusKm * c * 1000;
		System.out.println("Distance: " + distanceInMeters);
		return distanceInMeters;
	}

	/**
	 * Convert degrees to radians.
	 * 
	 * @param degrees
	 * @return
	 */
	private double degreesToRadians(double degrees) {
		return degrees * Math.PI / 180;
	}

}
