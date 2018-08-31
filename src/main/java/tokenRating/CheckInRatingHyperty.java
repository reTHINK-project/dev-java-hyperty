package tokenRating;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtilsHelper;
import util.InitialData;

/**
 * The Check-in Rating Hyperty observes user's check-in location and reward with
 * tokens the individual wallet in case it matches with some place. *
 */
public class CheckInRatingHyperty extends AbstractTokenRatingHyperty {

	private static final String logMessage = "[CheckIn] ";
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
	private String bonusCollection = "bonus";
	private String dataSource = "checkin";

	private CountDownLatch checkinLatch;
	private CountDownLatch findUserID;
	private CountDownLatch findRates;
	private String userIDToReturn = null;

	@Override
	public void start() {
		super.start();

		// read config
		checkInTokens = config().getInteger("tokens_per_checkin");
		checkin_radius = config().getInteger("checkin_radius");
		min_frequency = config().getInteger("min_frequency");

		createStreams();
	}

	private void createStreams() {
		JsonObject streams = config().getJsonObject("streams");

		// shops stream
		String shopsStreamAddress = streams.getString("shops");
		create(shopsStreamAddress, new JsonObject(), false, subscriptionHandler(), readHandler());

		// bonus stream
		String bonusStreamAddress = streams.getString("bonus");
		create(bonusStreamAddress, new JsonObject(), false, subscriptionHandlerBonus(), readHandlerBonus());
	}

	/**
	 * Handler for subscription requests (bonus).
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> subscriptionHandlerBonus() {
		return msg -> {
			mongoClient.find(bonusCollection, new JsonObject(), res -> {
				JsonArray bonus = new JsonArray(res.result());
				msg.reply(bonus);
			});
		};

	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> subscriptionHandler() {
		return msg -> {
			mongoClient.find(shopsCollection, new JsonObject(), res -> {
				JsonArray shops = new JsonArray(res.result());
				// reply with shops info
				msg.reply(shops);
			});
		};

	}

	/**
	 * Handler for read requests (bonus).
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readHandlerBonus() {
		return msg -> {
			JsonObject response = new JsonObject();
			if (msg.body().getJsonObject("resource") != null) {

			} else {
				mongoClient.find(bonusCollection, new JsonObject(), res -> {
					System.out.println(res.result().size() + " <-value returned" + res.result().toString());

					response.put("data", new InitialData(new JsonArray(res.result().toString())).getJsonObject())
							.put("identity", this.identity);
					msg.reply(response);
				});
			}
		};

	}

	/**
	 * Handler for read requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readHandler() {
		return msg -> {
			JsonObject response = new JsonObject();
			if (msg.body().getJsonObject("resource") != null) {

			} else {
				mongoClient.find(shopsCollection, new JsonObject(), res -> {
					System.out.println(res.result().size() + " <-value returned" + res.result().toString());

					response.put("data", new InitialData(new JsonArray(res.result().toString())).getJsonObject())
							.put("identity", this.identity);
					msg.reply(response);
				});
			}
		};

	}

	int tokenAmount;

	@Override
	int rate(Object data) {
		System.out.println("1 - Rating");
		tokenAmount = -1;
		Long currentTimestamp = new Date().getTime();

		// check if checkin or pick up
		JsonObject changesMessage = (JsonObject) data;
		String shopID = changesMessage.getString("shopID");
		String bonusID = changesMessage.getString("bonusID");
		if (bonusID != null) {
			// COLLECT BONUS
			System.out.println(logMessage + "COLLECT MESSAGE " + changesMessage.toString());
			checkinLatch = new CountDownLatch(1);
			new Thread(() -> {
				// get bonus from DB
				mongoClient.find(bonusCollection, new JsonObject().put("id", bonusID), bonusForIdResult -> {
					System.out.println(logMessage + "Received bonus info");
					JsonObject bonusInfo = bonusForIdResult.result().get(0);
					validatePickUpItem(changesMessage.getString("guid"), bonusInfo, currentTimestamp);
					checkinLatch.countDown();
				});
			}).start();
		} else {
			// CHECKIN
			System.out.println("CHECK IN MESSAGE " + changesMessage.toString());
			String user = changesMessage.getString("guid");
			Double userLatitude = changesMessage.getDouble("latitude");
			Double userLongitude = changesMessage.getDouble("longitude");

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
						tokenAmount = -2;
						return;
					}
					validateCheckinTimestamps(user, shopID, currentTimestamp);
					checkinLatch.countDown();
				});
			}).start();
		}

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

		findRates = new CountDownLatch(1);

		new Thread(() -> {
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
					persistData(dataSource, user, currentTimestamp, shopID, userRates, null);
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
					System.out.println("LAST VISIT TIMESTAMP->" + lastVisitTimestamp);
					System.out.println("Current TIMESTAMP->" + currentTimestamp);
					if (lastVisitTimestamp + (min_frequency * 60 * 1 * 1000) <= currentTimestamp) {
						System.out.println("continue");
						persistData(dataSource, user, currentTimestamp, shopID, userRates, null);

					} else {
						System.out.println("invalid");
						tokenAmount = -1;
					}
				}
				findRates.countDown();

			});
		}).start();

		try {
			findRates.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch");
			return;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return;

	}

	private void validatePickUpItem(String user, JsonObject bonusInfo, long currentTimestamp) {
		System.out.println(logMessage + " - validatePickUpItem(): " + bonusInfo.toString());

		findRates = new CountDownLatch(1);

		// TODO - check wallet funds

		new Thread(() -> {
			// get previous checkin from that user for that rating source
			mongoClient.find(collection, new JsonObject().put("user", user), result -> {
				boolean valid = true;
				JsonObject userRates = result.result().get(0);
				JsonArray checkInRates = userRates.getJsonArray(dataSource);
				System.out.println(logMessage + " - checkInRates: " + checkInRates.toString());
				Long start = null;
				Long expires = null;
				try {
					start = new SimpleDateFormat("yyyy/MM/dd").parse(bonusInfo.getString("start")).getTime();
					expires = new SimpleDateFormat("yyyy/MM/dd").parse(bonusInfo.getString("expires")).getTime();
				} catch (ParseException e) {
					e.printStackTrace();
				}
				if (currentTimestamp > start && currentTimestamp < expires) {
					// check constraints
					JsonObject constraints = bonusInfo.getJsonObject("constraints");
					if (constraints != null) {
						String period = constraints.getString("period");
						int times = constraints.getInteger("times");
						JsonArray pickUps = new JsonArray();
						System.out.println(logMessage + " - validatePickUpItem(): validating constraints");
						for (int i = 0; i < checkInRates.size(); i++) {
							JsonObject rate = checkInRates.getJsonObject(i);
							// check id
							if (rate.getString("id").equals(bonusInfo.getString("id"))) {
								pickUps.add(rate);
							}
						}
						if (period.equals("day")) {
							// and get that as a Date
							JsonArray forThisPeriod = new JsonArray();
							// check how many for that day
							for (int i = 0; i < pickUps.size(); i++) {
								JsonObject rate = pickUps.getJsonObject(i);
								Date rateDate = new Date(rate.getLong("timestamp"));
								if (DateUtilsHelper.isSameDay(rateDate, new Date())) {
									forThisPeriod.add(rate);
								}
							}
							System.out.println(logMessage + " - forThisPeriod: " + forThisPeriod.size());
							System.out.println(logMessage + " - times: " + times);
							if (forThisPeriod.size() == times) {
								valid = false;
							}
						}
						// TODO - hour ?
					}
					if (valid) {
						// invalid-failed-constraints
						tokenAmount = bonusInfo.getInteger("cost") * -1;
					} else {
						tokenAmount = 0;
					}
				} else {
					// invalid-not-available
					tokenAmount = 1;
				}
				persistData(dataSource, user, currentTimestamp, bonusInfo.getString("id"), userRates, null);
				findRates.countDown();
			});
		}).start();

		try {
			findRates.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch");
			return;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return;

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
					changes.put("guid", getUserURL(address));
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
				if (data.size() == 2) {
					JsonObject changes = new JsonObject();

					for (int i = 0; i < data.size(); i++) {
						final JsonObject obj = data.getJsonObject(i);
						final String name = obj.getString("name");
						switch (name) {
						case "bonus":
							changes.put("bonusID", obj.getString("value"));
							break;
						case "checkin":
							changes.put("shopID", obj.getString("value"));
							break;
						default:
							break;
						}
					}
					changes.put("guid", getUserURL(address));
					System.out.println("CHANGES" + changes.toString());

					int pointsToWithdraw = rate(changes);
					mine(pointsToWithdraw, changes, "bonus");
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

}
