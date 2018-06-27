package tokenRating;

import java.util.Date;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
	public static String ratingPublic = "hyperty://sharing-cities-dsm/energy-saving-rating/public";
	public static String ratingPrivate = "hyperty://sharing-cities-dsm/energy-saving-rating/private";

	private String dataSource = "energy-saving";

	@Override
	public void start() {

		super.start();

	}

	@Override
	public void onNotification(JsonObject body) {
		System.out.println(logMessage + "onNotification(): " + body.toString());

		String from = body.getString("from");
		String address = from.split("/subscription")[0];
		String userID = getUserURL(address);
		/*
		 * Before the invitation is accepted, it checks there is no subscription yet for
		 * the User CGUID URL. If accepted, a listener is added to the address set in
		 * the from attribute.
		 */
		if (userID == null) {
			System.out.println(logMessage + "no sub yet, adding listener");
			String guid = body.getJsonObject("identity").getJsonObject("userProfile").getString("guid");
			subscribe(address, guid);
		}

	}

	@Override
	int rate(Object data) {
		// reset latch
		System.out.println(logMessage + "rate(): " + data.toString());

		int tokenAmount = -1;
		Long currentTimestamp = new Date().getTime();

		// data contains shopID, users's location
		JsonObject energyMessage = (JsonObject) data;
		String id = energyMessage.getString("id");
		String streamId = energyMessage.getString("streamId");
		String deviceId = energyMessage.getString("deviceId");
		String user = energyMessage.getString("guid");
		int energyConsumption = energyMessage.getInteger("data");

		persistData(dataSource, user, currentTimestamp, id, null, null);
		// TODO - rating algorithm
		tokenAmount = applyPublicRating();

		return tokenAmount;
	}

	/**
	 * Rate energy message according to the public algorithm.
	 * 
	 * @return
	 */
	private int applyPublicRating() {

		return 1;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		System.out.println(logMessage + "onChanges(): waiting for changes on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					JsonObject changes = new JsonObject();

					for (int i = 0; i < data.size(); i++) {
						final JsonObject obj = data.getJsonObject(i);
						final String name = obj.getString("name");
						switch (name) {
						case "iot":
							changes.put("data", obj.getInteger("data"));
							changes.put("id", obj.getString("id"));
							changes.put("streamId", obj.getString("streamId"));
							changes.put("deviceId", obj.getString("deviceId"));
							break;
						default:
							break;
						}
					}
					changes.put("guid", getUserURL(address));
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

}
