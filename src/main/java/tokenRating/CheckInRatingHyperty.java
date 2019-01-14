package tokenRating;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;
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

	@Override
	public void start() {
		super.start();

		ratingType = "checkin-rating";
		// read config
		checkInTokens = config().getInteger("tokens_per_checkin");
		checkin_radius = config().getInteger("checkin_radius");
		min_frequency = config().getInteger("min_frequency");

		createStreams();
		resumeDataObjects(ratingType);
		//cleanDuplicatedDataObjects();
	}

	private void createStreams() {
		JsonObject streams = config().getJsonObject("streams");

		// shops stream
		String shopsStreamAddress = streams.getString("shops");
		create(null, shopsStreamAddress, new JsonObject(), false, subscriptionHandler(), readHandler());

		// bonus stream
		String bonusStreamAddress = streams.getString("bonus");
		create(null, bonusStreamAddress, new JsonObject(), false, subscriptionHandlerBonus(), readHandlerBonus());
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
					logger.debug(res.result().size() + " <-value returned" + res.result().toString());

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
					logger.debug(res.result().size() + " <-value returned" + res.result().toString());

					response.put("data", new InitialData(new JsonArray(res.result().toString())).getJsonObject())
							.put("identity", this.identity);
					msg.reply(response);
				});
			}
		};

	}

	@Override
	Future<Integer> rate(Object data) {
		logger.debug("1 - Rating");
		Long currentTimestamp = new Date().getTime();

		Future<Integer> checkinTokens = Future.future();

		// check if checkin or pick up
		JsonObject changesMessage = (JsonObject) data;
		String shopID = changesMessage.getString("shopID");
		String bonusID = changesMessage.getString("bonusID");
		if (bonusID != null) {
			// COLLECT BONUS
			logger.debug(logMessage + "COLLECT MESSAGE " + changesMessage.toString());

			// get bonus from DB
			mongoClient.find(bonusCollection, new JsonObject().put("id", bonusID), bonusForIdResult -> {
				logger.debug(logMessage + "Received bonus info");
				JsonObject bonusInfo = bonusForIdResult.result().get(0);
				Future<Integer> collectBonusTokensFuture = validatePickUpItem(changesMessage.getString("guid"),
						bonusInfo, currentTimestamp);
				collectBonusTokensFuture.setHandler(asyncResult -> {
					checkinTokens.complete(asyncResult.result());
				});
			});
		} else {
			// CHECKIN
			logger.debug("CHECK IN MESSAGE " + changesMessage.toString());
			String user = changesMessage.getString("guid");
			Double userLatitude = changesMessage.getDouble("latitude");
			Double userLongitude = changesMessage.getDouble("longitude");

			logger.debug("2 - Started thread");
			// get shop with that ID
			mongoClient.find(shopsCollection, new JsonObject().put("id", shopID), shopForIdResult -> {
				logger.debug("2 - Received shop info");
				JsonObject shopInfo = shopForIdResult.result().get(0);
				boolean validPosition = validateUserPosition(user, userLatitude, userLongitude, shopInfo);
				if (!validPosition) {
					checkinTokens.complete(-2);
				}
				Future<Boolean> validTimestamp = validateCheckinTimestamps(user, shopID, currentTimestamp);
				validTimestamp.setHandler(asyncResult -> {
					if (asyncResult.succeeded()) {
						if (validTimestamp.result()) {
							checkinTokens.complete(checkInTokens);
						} else {
							checkinTokens.complete(-1);
						}
					} else {
						// oh ! we have a problem...
					}
				});
			});
		}

		return checkinTokens;
	}

	private Future<Boolean> validateCheckinTimestamps(String user, String shopID, long currentTimestamp) {

		Future<Boolean> validateCheckinTimestamps = Future.future();

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
				logger.debug("User never went to this shop");
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
				logger.debug("LAST VISIT TIMESTAMP->" + lastVisitTimestamp);
				logger.debug("Current TIMESTAMP->" + currentTimestamp);
				if (lastVisitTimestamp + (min_frequency * 60 * 1 * 1000) <= currentTimestamp) {
					logger.debug("continue");
					persistData(dataSource, user, currentTimestamp, shopID, userRates, null);

				} else {
					logger.debug("invalid");
					validateCheckinTimestamps.complete(false);
				}
			}
			validateCheckinTimestamps.complete(true);

		});

		return validateCheckinTimestamps;

	}

	private Future<Integer> validatePickUpItem(String user, JsonObject bonusInfo, long currentTimestamp) {
		logger.debug(logMessage + " - validatePickUpItem(): " + bonusInfo.toString());

		Future<Integer> findRates = Future.future();

		// get previous checkin from that user for that rating source
		mongoClient.find(collection, new JsonObject().put("user", user), result -> {
			boolean valid = true;
			JsonObject userRates = result.result().get(0);
			JsonArray checkInRates = userRates.getJsonArray(dataSource);
			logger.debug(logMessage + " - checkInRates: " + checkInRates.toString());
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
					logger.debug(logMessage + " - validatePickUpItem(): validating	 constraints");
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
						logger.debug(logMessage + " - forThisPeriod: " + forThisPeriod.size());
						logger.debug(logMessage + " - times: " + times);
						if (forThisPeriod.size() == times) {
							valid = false;
						}
					}
				}
				if (valid) {
					findRates.complete(bonusInfo.getInteger("cost") * -1);
				} else {
					// invalid-failed-constraints
					findRates.complete(0);
				}
			} else {
				// invalid-not-available
				findRates.complete(1);
			}
			persistData(dataSource, user, currentTimestamp, bonusInfo.getString("id"), userRates, null);
		});

		return findRates;

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
			logger.debug("2 - User is close to store");

			// persist check in
			// persistData(dataSource, user, new Date().getTime(), shopID);
			return true;
		} else {
			logger.debug("2 - User is far from store");
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
		logger.debug("Distance: " + distanceInMeters);
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
		logger.info("[CHECK-IN] waiting for changes->" + address_changes);
		eb.consumer(address_changes, message -> {
			logger.info("[Check-In] data");
			logger.debug("[Check-In] data msg ->" + message.body().toString());
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
					Future<String> userURL = getUserURL(address);
					userURL.setHandler(asyncResult -> {
						logger.debug("URL " + userURL.result());
						changes.put("guid", userURL.result());
						logger.debug("CHANGES" + changes.toString());

						Future<Integer> numTokens = rate(changes);
						numTokens.setHandler(res -> {
							if (res.succeeded()) {
								/*
								 * if (numTokens == -1) {
								 * //logger.debug("User is not inside any shop or already checkIn"); } else {
								 * //logger.debug("User is close"); mine(numTokens, changes, "checkin"); }
								 */
								if (numTokens.result() < 0) {
									logger.debug("User is not inside any shop or already checkIn");
								} else {
									logger.debug("User is close");
								}

								mine(res.result(), changes, "checkin");
							} else {
								// oh ! we have a problem...
							}
						});
					});

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
					Future<String> userURLFuture = getUserURL(address);
					userURLFuture.setHandler(asyncResult -> {
						String userURL = userURLFuture.result();

						changes.put("guid", userURL);
						logger.debug("CHANGES" + changes.toString());

						Future<Integer> pointsToWithdraw = rate(changes);
						pointsToWithdraw.setHandler(res -> {
							mine(pointsToWithdraw.result(), changes, "bonus");
						});
					});

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

}
