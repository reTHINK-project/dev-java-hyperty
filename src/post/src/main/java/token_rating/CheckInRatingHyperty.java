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

	// TODO define value
	private int checkInTokens = 10;
	// TODO max difference between user and store location
	private Double maxDifference = 0.01;
	// TODO min difference between current and last checkin times
	private Double minDifferenceTimestamps = 10.0;

	private String shopsCollection = "shops";
	private String dataSource = "checkin";
	private JsonArray shops = null;
	private String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";

	private final CountDownLatch checkinLatch = new CountDownLatch(1);

	@Override
	public void start() {
		super.start();

		// fetch locations
		fetchStoreData();

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

		tokenAmount = -1;
		Long currentTimestamp = new Date().getTime();

		// data contains shopID, users's location
		JsonObject checkInMessage = (JsonObject) data;
		String user = checkInMessage.getString("userID");
		String shopID = checkInMessage.getString("shopID");
		Double userLatitude = checkInMessage.getDouble("latitude");
		Double userLongitude = checkInMessage.getDouble("longitude");

		// get shop with that ID
		mongoClient.find(shopsCollection, new JsonObject().put("id", shopID), shopForIdResult -> {

			validateCheckinTimestamps(user, shopID, currentTimestamp);
			JsonObject shopInfo = shopForIdResult.result().get(0);
			validateUserPosition(user, userLatitude, userLongitude, shopInfo);

		});

		try {
			if (checkinLatch.await(6L, TimeUnit.SECONDS))
				return tokenAmount;
			else
				return tokenAmount;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} // Timeout exceeded
		return checkInTokens;

	}

	private void validateCheckinTimestamps(String user, String shopID, long currentTimestamp) {
		// get previous checkin from that user for that rating source
		JsonObject query = new JsonObject().put("user", user);

		mongoClient.find(ratesCollection, query, result -> {

			System.out.println("Found user profile: " + result.result().size());
			// access checkins data source
			JsonObject userRates = result.result().get(0);
			JsonArray checkInRates = userRates.getJsonArray(dataSource);
			// check ins for that store
			ArrayList<JsonObject> a = (ArrayList<JsonObject>) checkInRates.getList();
			List<JsonObject> rrr = (List<JsonObject>) a.stream() // convert list to stream
					.filter(element -> shopID.equals(element.getString("id"))).collect(Collectors.toList());
			if (rrr.size() == 0) {
				System.out.println("user never went to this shop");
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

				JsonObject latestCheckInForShop = rrr.get(0);
				// compare timestamps
				if (latestCheckInForShop.getDouble("timestamp") + minDifferenceTimestamps <= currentTimestamp) {
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
	private void validateUserPosition(String user, Double userLatitude, Double userLongitude, JsonObject shopInfo) {
		// access location
		JsonObject location = shopInfo.getJsonObject("location");
		Double latitude = location.getDouble("degrees-latitude");
		Double longitude = location.getDouble("degrees-longitude");

		// check if user in range
		if (Math.abs(userLatitude - latitude) <= maxDifference
				&& Math.abs(userLongitude - longitude) <= maxDifference) {
			System.out.println("User is close to store");

			// persist check in
//			persistData(dataSource, user, new Date().getTime(), shopID);
			tokenAmount = checkInTokens;
			checkinLatch.countDown();
		} else {
			checkinLatch.countDown();
		}

	}

}
