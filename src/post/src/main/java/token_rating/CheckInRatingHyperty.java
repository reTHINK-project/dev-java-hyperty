package token_rating;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * The Check-in Rating Hyperty observes user's check-in location and reward with
 * tokens the individual wallet in case it matches with some place.
 * 
 * Observed Streams: Citizen Location; Produced Stream : Check-in map
 * 
 */
public class CheckInRatingHyperty extends AbstractTokenRatingHyperty {

	// TODO define value
	private int checkInTokens = 10;
	// TODO max difference between user and store location
	private Double maxDifference = 0.01;
	private MongoClient mongoClient = null;
	private JsonArray locations = null;
	private String locationStreamAddress = "ctxt://sharing-cities-dsm/shops-location";

	@Override
	public void start() {
		super.start();

		// make connection with MongoDB
		String uri = "mongodb://localhost:27017";

		String db = "test";

		JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", db);

		mongoClient = MongoClient.createShared(vertx, mongoconfig);

		// fetch locations
		fetchLocationsFromDB();

		vertx.eventBus().consumer(locationStreamAddress, message -> {
			message.reply(locations);
		});

	}

	/**
	 * Fetch store locations from MongoDB.
	 */
	private void fetchLocationsFromDB() {
		mongoClient.find("location_data", new JsonObject(), res -> {
			locations = new JsonArray(res.result());
		});

	}

	@Override
	int rate(Object data) {
		JsonObject userLocation = (JsonObject) data;
		Double userLatitude = userLocation.getDouble("latitude");
		Double userLongitude = userLocation.getDouble("longitude");
		// data contains (latitude-longitude) pair
		for (int i = 0; i < locations.size(); i++) {
			JsonObject storeLocation = locations.getJsonObject(i);
			Double latitude = storeLocation.getDouble("latitude");
			Double longitude = storeLocation.getDouble("longitude");

			// check if user in range
			if (Math.abs(userLatitude - latitude) <= maxDifference) {
				if (Math.abs(userLongitude - longitude) <= maxDifference) {
					System.out.println("User is close to store");
					return checkInTokens;
				}
			}
		}

		return -1;
	}

}
