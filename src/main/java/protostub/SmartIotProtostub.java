package protostub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.core.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import runHyperties.LoggerFactory;

public class SmartIotProtostub extends AbstractVerticle {

	protected String url;
	protected String collection;
	protected String database;
	protected String mongoHost;
	protected EventBus eb;
	protected String smartIotUrl;
	protected MongoClient mongoClient = null;
	protected String currentToken;
	private String appID;
	private String appSecret;
	private String pointOfContact;
	protected Logger logger;

	@Override
	public void start() {
		// logger.debug("{{SmartIOTProtostub}} Starting SmartIotProtostub");
		this.url = config().getString("url");
		this.collection = config().getString("collection");
		this.database = config().getString("db_name");
		this.mongoHost = config().getString("mongoHost");
		this.smartIotUrl = config().getString("smart_iot_url");
		this.pointOfContact = config().getString("point_of_contact");
		this.logger =  LoggerFactory.getInstance().getLogger(); 

		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());

		if (mongoHost != null) {
			// logger.debug("{{SmartIOTProtostub}} Setting up Mongo to:" + this.url);
			final String uri = "mongodb://" + mongoHost + ":27017";

			final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", this.database);

			mongoClient = MongoClient.createShared(vertx, mongoconfig);
		}

		this.currentToken = Authentication();
		// logger.debug("{{SmartIOTProtostub}} token ->" + this.currentToken);

		RegisterApp();
		// logger.debug("{{SmartIOTProtostub}} app -> " + appID + ":" +
		// appSecret);

		/**
		 * Just for test
		 */
		/*
		 * //new device JsonObject newDevice = registerNewDevice("device name",
		 * "device description");
		 *
		 * //register stream String streamName =
		 * "userguidddd-dddddd-dddasdas-idddd-dddddd-dsdas";
		 * registerNewStream(newDevice.getString("id"), streamName);
		 *
		 * //new subscription JsonObject subscription =
		 * createSubscription("subcriptionName", "subscriptionDescription",
		 * newDevice.getString("id"), streamName);
		 * //logger.debug("subscription result" + subscription.toString());
		 */

	}

	private void RegisterApp() {

		JsonObject appData = registerNewDevice("private App", "Description of private APP");
		if (appData != null) {
			appID = appData.getString("id");
			appSecret = appData.getString("secret");
		}

	}

	private Handler<Message<JsonObject>> onMessage() {

		return message -> {

			// logger.debug("{{SmartIOTProtostub}} New message -> " +
			// message.body().toString());
			if (mandatoryFieldsValidator(message)) {

				// logger.debug("{{SmartIOTProtostub}} -> [Worker]-" +
				// Thread.currentThread().getName() + "\n[Data] "
				// + message.body());

				final JsonObject body = new JsonObject(message.body().toString()).getJsonObject("body");
				final String type = message.body().getString("type");

				switch (type) {
				case "read":

					// logger.debug("{{SmartIOTProtostub}} read message ");

					break;
				case "create":
					// logger.debug("{{SmartIOTProtostub}} create message ");
					// message.reply(new JsonObject());

					if (body.getString("resource").equals("device")) {
						handleDeviceCreationRequest(message);
					} else if (body.getString("resource").equals("stream")) {
						handleStreamCreationRequest(message);
					}

					break;
				case "delete":
					if (body.getString("resource").equals("stream")) {
						handleStreamDeleteRequest(message);
					} else if (body.getString("resource").equals("device")) {
						handleDeviceDeleteRequest(message);
					}
				default:
					break;
				}

			}
		};
	}

	private void handleStreamDeleteRequest(Message<JsonObject> message) {

		final JsonObject messageToDelete = new JsonObject(message.body().toString());

		final JsonObject userProfile = messageToDelete.getJsonObject("identity").getJsonObject("userProfile");

		final String guid = userProfile.containsKey("guid") ? userProfile.getString("guid") : null;
		JsonObject responseDenied = new JsonObject().put("body", new JsonObject().put("code", 406).put("value", false));
		JsonObject responseBodyOK = new JsonObject().put("code", 200);

		if (guid != null) {
			// logger.debug("{{SmartIOTProtostub}} search Device of guid->" + guid);
			final JsonObject body = messageToDelete.getJsonObject("body");

			final String streamName = body.containsKey("value") ? body.getString("value") : null;
			Future<JsonObject> deviceFuture = findDeviceObject(guid);
			deviceFuture.setHandler(asyncResult -> {
				JsonObject device = deviceFuture.result();
				// logger.debug(" ->streamName:" + streamName);
				if (device != null && streamName != null) {
					final String deviceID = device.getString("id");
					// logger.debug("{{SmartIOTProtostub}} DeviceID returned->" + deviceID);

					JsonArray streamList = device.getJsonArray("stream_list");
					boolean streamExist = false;
					String platformID = null;
					for (int i = 0; i < streamList.size(); i++) {
						JsonObject currentStream = streamList.getJsonObject(i);
						if (currentStream.getString("name").equals(streamName)) {
							streamExist = true;
							platformID = currentStream.getString("platform");
						}
					}
					if (streamExist) {

						boolean streamDeleted1st = deleteStream(deviceID, streamName);
						boolean streamDeleted2nd = false;
						if (!streamDeleted1st) {
							streamDeleted2nd = deleteStream(deviceID, streamName);
						}

						if (streamDeleted1st || streamDeleted2nd) {

							JsonObject update = new JsonObject().put("$pull", new JsonObject().put("device.stream_list",
									new JsonObject().put("name", streamName)));

							// logger.debug("{{SmartIOTProtostub}} delete" + update.toString());

							mongoClient.updateCollection(this.collection, new JsonObject().put("guid", guid), update,
									res -> {
										// logger.debug("{{SmartIOTProtostub}} result>" + res.succeeded());
									});

							String dataObjectURL = "context://sharing-cities-dsm/" + platformID + "/" + streamName;

							mongoClient.findOneAndDelete("dataobjects", new JsonObject().put("objURL", dataObjectURL),
									res -> {
										logger.debug(
												"{{SmartIOTProtostub}} result delete from dataobjects collection>"
														+ res.succeeded());
									});

							responseBodyOK.put("result", true);
							JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
							message.reply(responseOK);
						}
						responseBodyOK.put("result", false);
						JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
						message.reply(responseOK);

					}

				}

			});

			message.reply(responseDenied);

			return;
		}

		message.reply(responseDenied);
		return;

		/*
		 *
		 * { "type": "delete", "to": "runtime://sharing-cities-dsm/protostub/smart-iot",
		 * "from": "hyperty://localhost/f7ce7531-1926-4b7d-94e0-2e42312bc562",
		 * "identity": { "userProfile": { "userURL":
		 * "user://google.com/lduarte.suil@gmail.com", "guid":
		 * "user-guid://0da2eee3820e141b90fff1d4614d755f1252ec2aacf9d7b1515d7c5ac9ad2dfc"
		 * } }, "body": { "resource": "stream", "value": "luisuserID" } }
		 */

	}

	private void handleDeviceDeleteRequest(Message<JsonObject> message) {

		final JsonObject messageToDelete = new JsonObject(message.body().toString());

		final JsonObject userProfile = messageToDelete.getJsonObject("identity").getJsonObject("userProfile");

		final String guid = userProfile.containsKey("guid") ? userProfile.getString("guid") : null;
		JsonObject responseDenied = new JsonObject().put("body", new JsonObject().put("code", 406).put("value", false));
		JsonObject responseBodyOK = new JsonObject().put("code", 200);

		if (guid != null) {
			// logger.debug("{{SmartIOTProtostub}} search Device of guid->" + guid);
			final JsonObject body = messageToDelete.getJsonObject("body");

			final String deviceID = body.containsKey("value") ? body.getString("value") : null;
			Future<JsonObject> deviceFuture = findDeviceObject(guid);
			deviceFuture.setHandler(asyncResult -> {
				JsonObject device = deviceFuture.result();
				if (device.getString("id").equals(deviceID)) {
					boolean deviceDeleted1st = deleteDevice(deviceID);
					boolean deviceDeleted2nd = false;
					if (!deviceDeleted1st) {
						deviceDeleted2nd = deleteDevice(deviceID);
					}

					if (deviceDeleted1st || deviceDeleted2nd) {
						// logger.debug("{{SmartIOTProtostub}} TO REMOVE on mongo (device) ->" +
						// device.toString());
						JsonArray streamList = device.getJsonArray("stream_list");

						for (int i = 0; i < streamList.size(); i++) {
							JsonObject currentStream = streamList.getJsonObject(i);
							String dataObjectURL = "context://sharing-cities-dsm/" + currentStream.getString("platform")
									+ "/" + currentStream.getString("name");
							// logger.debug("{{SmartIOTProtostub}} to remove (obj)->" +
							// dataObjectURL);

							mongoClient.findOneAndDelete("dataobjects", new JsonObject().put("objURL", dataObjectURL),
									res -> {
										logger.debug("{{SmartIOTProtostub}} result delete on dataobject ("
												+ dataObjectURL + ")>" + res.succeeded());
									});
						}
						mongoClient.findOneAndDelete(this.collection, new JsonObject().put("guid", guid), res -> {
							logger.debug("{{SmartIOTProtostub}} result delete on siotdevices (" + guid + ")>"
									+ res.succeeded());
						});

						responseBodyOK.put("result", true);
						JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
						message.reply(responseOK);

					}

					responseBodyOK.put("result", false);
					JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
					message.reply(responseOK);

				}
			});

		}

		/*
		 *
		 * { "type": "delete", "to": "runtime://sharing-cities-dsm/protostub/smart-iot",
		 * "from": "hyperty://localhost/f7ce7531-1926-4b7d-94e0-2e42312bc562",
		 * "identity": { "userProfile": { "userURL":
		 * "user://google.com/lduarte.suil@gmail.com", "guid":
		 * "user-guid://0da2eee3820e141b90fff1d4614d755f1252ec2aacf9d7b1515d7c5ac9ad2dfc"
		 * } }, "body": { "resource": "device", "value": "luisuserID" } }
		 */

	}

	private Future<Void> handleDeviceCreationRequest(Message<JsonObject> message) {
		final JsonObject messageToCreate = new JsonObject(message.body().toString());

		final JsonObject userProfile = messageToCreate.getJsonObject("identity").getJsonObject("userProfile");

		final String guid = userProfile.containsKey("guid") ? userProfile.getString("guid") : null;
		JsonObject responseDenied = new JsonObject().put("body", new JsonObject().put("code", 406));
		JsonObject responseBodyOK = new JsonObject().put("code", 200);

		Future<Void> createDevice = Future.future();
		if (guid != null) {

			final JsonObject body = messageToCreate.getJsonObject("body");
			final String description = body.containsKey("description") ? body.getString("description") : null;
			final String name = guid.split("//")[1];

			mongoClient.find(this.collection, new JsonObject().put("guid", guid), res -> {
				if (res.result().size() == 0) {

					// logger.debug("{{SmartIOTProtostub}} no device yet, creating");

					JsonObject newDevice = registerNewDevice(name, description);

					// logger.debug(
					// "{{SmartIOTProtostub}} no device yet, creating response1st->" +
					// newDevice.toString());
					if (newDevice.containsKey("unauthorised") && newDevice.getBoolean("unauthorised")) {

						newDevice = registerNewDevice(name, description);
					}
					final JsonObject deviceCreated = newDevice;

					if (newDevice != null) {
						// logger.debug("{{SmartIOTProtostub}} Device for " + name + " -> |||
						// create with id->"
						// + newDevice.getString("id"));

						JsonObject document = new JsonObject().put("guid", guid).put("device", newDevice);
						mongoClient.save(this.collection, document, id -> {
							// logger.debug("{{SmartIOTProtostub}} New device added to mongo" + id);
							responseBodyOK.put("description", "new device created");
							responseBodyOK.put("device", deviceCreated);
							JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
							message.reply(responseOK);
							createDevice.complete();
						});
					} else {
						// logger.debug("{{SmartIOTProtostub}} Creation request failed");
						message.reply(responseDenied);
						createDevice.complete();
					}
				} else {
					// logger.debug("{{SmartIOTProtostub}} Device already created for this
					// user"
					// + res.result().get(0).toString());
					responseBodyOK.put("device", res.result().get(0).getJsonObject("device"));
					responseBodyOK.put("description", "device already exist");
					JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
					message.reply(responseOK);
					createDevice.complete();
				}
			});

		}
		else{
			createDevice.complete();
		}

		return createDevice;
		/*
		 *
		 * { "type": "create", "to": "runtime://sharing-cities-dsm/protostub/smart-iot",
		 * "from": "hyperty://localhost/15b36f88-51d1-4137-9f29-92651558bcbc",
		 * "identity": { "userProfile": { "userURL":
		 * "user://google.com/lduarte.suil@gmail.com", "guid":
		 * "user-guid://825bf1a190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"
		 * } }, "body": { "resource": "device", "description": "device description" } }
		 *
		 *
		 */

	}

	private void handleStreamCreationRequest(Message<JsonObject> message) {
		// TODO Auto-generated method stub
		final JsonObject messageToCreate = new JsonObject(message.body().toString());

		final JsonObject userProfile = messageToCreate.getJsonObject("identity").getJsonObject("userProfile");

		final String guid = userProfile.containsKey("guid") ? userProfile.getString("guid") : null;
		JsonObject responseDenied = new JsonObject().put("body", new JsonObject().put("code", 406));
		JsonObject responseBodyOK = new JsonObject().put("code", 200);

		if (guid != null) {
			// logger.debug("{{SmartIOTProtostub}}creationRequest search Device of
			// guid->" + guid);
			final JsonObject body = messageToCreate.getJsonObject("body");
			final String thirdPtyUserId = body.containsKey("platformUID") ? body.getString("platformUID") : null;
			final String thirdPtyPlatformId = body.containsKey("platformID") ? body.getString("platformID") : null;
			final String ratingType = body.containsKey("ratingType") ? body.getString("ratingType") : null;
			Future<String> deviceIDFuture = findDevice(guid);
			deviceIDFuture.setHandler(asyncResult -> {
				String deviceID = deviceIDFuture.result();

				// logger.debug(
				// "{{SmartIOTProtostub}} DeviceID returned->" + deviceID + " ->streamName:" +
				// thirdPtyUserId);
				if (deviceID != null && thirdPtyUserId != null && thirdPtyPlatformId != null && ratingType != null) {
					String objURL = "context://sharing-cities-dsm/" + thirdPtyPlatformId + "/" + thirdPtyUserId;
					Future<String> checkStreamIDFuture = findStream(objURL, guid);
					checkStreamIDFuture.setHandler(res -> {
						String checkStreamID = checkStreamIDFuture.result();
						// logger.debug("{{SmartIOTProtostub}} stream ID exist?" + checkStreamID);
						if (checkStreamID != null) {
							// logger.debug("{{SmartIOTProtostub}} stream already created->" +
							// objURL);
							inviteHyperty(thirdPtyUserId, thirdPtyPlatformId, messageToCreate.getJsonObject("identity"),
									checkStreamID, ratingType);

							responseBodyOK.put("description", "stream already exist");
							JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
							message.reply(responseOK);
						} else {
							boolean streamCreated1st = registerNewStream(deviceID, thirdPtyUserId);
							boolean streamCreated2nd = false;
							if (!streamCreated1st) {
								streamCreated2nd = registerNewStream(deviceID, thirdPtyUserId);
							}

							if (streamCreated1st || streamCreated2nd) {

								JsonArray streamList = new JsonObject(getStreamsList(deviceID)).getJsonArray("streams");
								// logger.debug("{{SmartIOTProtostub}} - stream list" +
								// streamList.toString());

								int x;
								for (x = 0; x < streamList.size(); x++) {
									JsonObject currentStream = streamList.getJsonObject(x);
									if (currentStream.getString("name").equals(thirdPtyUserId)) {
										logger.debug(
												"{{SmartIOTProtostub}} stream created was" + currentStream.toString());
										JsonObject ctx = new JsonObject().put("contextValue", "");
										currentStream.put("context", ctx);
										currentStream.put("platform", thirdPtyPlatformId);

										JsonObject update = new JsonObject().put("$push",
												new JsonObject().put("device.stream_list", currentStream));

										// logger.debug("{{SmartIOTProtostub}} push" + update.toString());

										mongoClient.updateCollection(this.collection,
												new JsonObject().put("guid", guid), update, res2 -> {
													logger.debug(
															"{{SmartIOTProtostub}} result>" + res2.succeeded());
												});
										inviteHyperty(thirdPtyUserId, thirdPtyPlatformId,
												messageToCreate.getJsonObject("identity"),
												currentStream.getString("id"), ratingType);
										responseBodyOK.put("description", "new stream created");
										responseBodyOK.put("stream", currentStream);
										JsonObject responseOK = new JsonObject().put("body", responseBodyOK);
										message.reply(responseOK);

										// logger.debug("{{SmartIOTProtostub}} subscription result " +
										// createSubscription(
										// "subscription from Vertx SmartIOTStub", null, deviceID, thirdPtyUserId));
									}
								}

							}
						}
					});

					fillRates(guid);

				}
			});

			message.reply(responseDenied);

			return;
		}

		/*
		 *
		 * { "type": "create", "to": "runtime://sharing-cities-dsm/protostub/smart-iot",
		 * "from": "hyperty://localhost/15b36f88-51d1-4137-9f29-92651558bcbc",
		 * "identity": { "userProfile": { "userURL":
		 * "user://google.com/lduarte.suil@gmail.com", "guid":
		 * "user-guid://825bf1a190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"
		 * } }, "body": { "resource": "stream", "platformID": "edp", "platformUserId":
		 * "luisuserID" } }
		 *
		 *
		 */

	}

	private Future<Void> fillRates(String guid) {
		Future<Void> fillRatesFuture = Future.future();

		mongoClient.find("rates", new JsonObject().put("user", guid), resultRates -> {
			if (resultRates.result().size() == 1) {
				JsonObject rates = resultRates.result().get(0);

				JsonArray energyArray = rates.getJsonArray("energy-saving");
				// logger.debug("energy rates size->" + energyArray.size());
				if (energyArray.size() == 0) {
					JsonObject transaction = new JsonObject();
					transaction.put("recipient", guid.split("//")[1]);
					transaction.put("source", "energy-saving");
					transaction.put("date", "2018-09-30T00:00Z");
					transaction.put("value", 0);
					transaction.put("description", "valid");
					transaction.put("bonus", false);
					transaction.put("nonce", 1);
					transaction.put("data", new JsonObject().put("percentage", "0"));

					String walletAddress = guid.split("//")[1].split("-")[0] + guid.split("//")[1].split("-")[1]
							+ "-wallet";
					JsonObject query = new JsonObject().put("identity", new JsonObject().put("userProfile",
							new JsonObject().put("guid", "user-guid://public-wallets")));
					// get wallets document
					mongoClient.find("wallets", query, res -> {
						JsonObject result = res.result().get(0);
						JsonArray wallets = result.getJsonArray("wallets");
						// logger.debug("updatePublicWalletBalance(): gofind" + walletAddress);
						// create wallets
						for (Object pWallet : wallets) {
							// get wallet with that address
							JsonObject wallet = (JsonObject) pWallet;

							if (wallet.getString("address").equals(walletAddress)) {
								// logger.debug("updatePublicWalletBalance(): wallet" + wallet);
								JsonArray transactions = wallet.getJsonArray("transactions");

								transactions.add(transaction);

								// logger.debug("transaction->" + transaction.toString());

							}
						}

						mongoClient.findOneAndReplace("wallets", query, result, id -> {
							// logger.debug("[siot] Transaction added to public wallet");

						});
					});

					JsonArray energyrates = rates.getJsonArray("energy-saving");
					energyrates.add(transaction);

					mongoClient.findOneAndReplace("rates", new JsonObject().put("user", guid), rates, id -> {
						// logger.debug("[siot] Transaction added to rates");

					});

				} else {
					// logger.debug("[siot] Transaction already exist on rates");
				}

				fillRatesFuture.complete();

			} else {
				fillRatesFuture.complete();
			}

		});

		return fillRatesFuture;

	}

	private void inviteHyperty(String thirdPtyUserId, String thirdPtyPlatformId, JsonObject identity, String streamID,
			String ratingType) {

		// case edp: hyperty://sharing-cities-dsm/energy-saving-rating
		// case gira/mobi-e: hyperty://sharing-cities-dsm/user-activity
		String toInviteHypertyUrl;
		String fromUrl = "context://sharing-cities-dsm/" + thirdPtyPlatformId + "/" + thirdPtyUserId + "/subscription";

		if (thirdPtyPlatformId.equals("edp")) {
			toInviteHypertyUrl = "hyperty://sharing-cities-dsm/energy-saving-rating/" + ratingType;
		} else {
			toInviteHypertyUrl = "hyperty://sharing-cities-dsm/user-activity";
		}

		JsonObject inviteMsg = new JsonObject();
		inviteMsg.put("type", "create");
		inviteMsg.put("to", toInviteHypertyUrl);
		inviteMsg.put("from", fromUrl);
		inviteMsg.put("identity", identity);
		inviteMsg.put("external", true);
		inviteMsg.put("streamID", streamID);

		this.eb.publish(toInviteHypertyUrl, inviteMsg);

	}

	private Future<String> findDevice(String guid) {

		Future<String> findDevice = Future.future();

		mongoClient.find(this.collection, new JsonObject().put("guid", guid), res -> {
			if (res.result().size() != 0) {
				JsonObject deviceFound = res.result().get(0).getJsonObject("device");
				// logger.debug("3,5" + deviceFound.getString("id"));
				findDevice.complete(deviceFound.getString("id"));
				// logger.debug("3,9" + device[0]);
			} else {
				findDevice.complete(null);
			}
		});

		return findDevice;
	}

	private Future<JsonObject> findDeviceObject(String guid) {

		Future<JsonObject> findDevice = Future.future();

		mongoClient.find(this.collection, new JsonObject().put("guid", guid), res -> {
			if (res.result().size() != 0) {
				JsonObject deviceFound = res.result().get(0).getJsonObject("device");
				// logger.debug("3,5" + deviceFound.toString());
				findDevice.complete(deviceFound);
				// logger.debug("3,9" + device[0]);
			} else {
				findDevice.complete(null);
			}
		});

		return findDevice;
	}

	private Future<String> findStream(String objURL, String guid) {
		// logger.debug("{{SmartIOTProtostub}} find stream:" + objURL);
		Future<String> findDevice = Future.future();

		mongoClient.find("dataobjects", new JsonObject().put("objURL", objURL), res -> {

			int x;
			for (x = 0; x < res.result().size(); x++) {
				String currentGuid = res.result().get(x).getJsonObject("metadata").getString("guid");
				if (currentGuid.equals(guid)) {

					String streamID = res.result().get(0).getString("url");

					findDevice.complete(streamID);
					break;
				}
			}
			findDevice.complete(null);

		});

		return findDevice;
	}

	public boolean mandatoryFieldsValidator(Message<JsonObject> message) {
		// header validation...
		final JsonObject json = new JsonObject(message.body().toString());
		JsonObject response = new JsonObject();
		response.put("code", 406);

		final String type = json.getString("type");
		if (type == null) {
			response.put("description", "No mandatory field 'type'");
			message.reply(response);
			return false;
		}

		final String from = json.getString("from");
		if (from == null) {
			response.put("description", "No mandatory field 'from'");
			message.reply(response);
			return false;
		}

		final JsonObject identity = json.getJsonObject("identity");
		if (identity == null) {
			response.put("description", "No mandatory field 'identity'");
			message.reply(response);
			return false;
		}

		return true;

	}

	private String Authentication() {

		String newToken = null;

		try {
			String user = "luis";
			String password = "vr6hamqs1tgb2fe0dfmj7r4l1fv4bf2v1rrjcbi3uv7ve5imv506";

			// String user = "5b1e2f6a-81e6-475b-b494-64a30908f4c7";
			// String password = "johnll3p7pd2m9e4mcsqhst4eqnnnk34s65397npb8e59tjuqku6";
			String toEncode = user + ":" + password;
			byte[] encodedUserPassword = Base64.encodeBase64(toEncode.getBytes());

			String encodedString = new String(encodedUserPassword);
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl + "/accounts/token");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");

			conn.setRequestProperty("authorization", "Basic " + encodedString);

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			for (int c; (c = in.read()) >= 0;)
				received.append(Character.toChars(c));

			conn.disconnect();

			// System.out
			// .println("{{SmartIOTProtostub}} [newToken](" + conn.getResponseCode() + ")" +
			// received.toString());
			if (conn.getResponseCode() == 200) {
				newToken = received.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return newToken;

	}

	private JsonObject registerNewDevice(String name, String description) {

		try {
			StringBuilder received = new StringBuilder();
			JsonObject toCreateDevice = new JsonObject();
			toCreateDevice.put("name", name);
			if (description != null) {
				toCreateDevice.put("description", description);
			}

			URL url = new URL(smartIotUrl + "/devices");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);
			conn.setRequestMethod("POST");

			// add payload Json
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(toCreateDevice.toString());
			wr.flush();

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			for (int c; (c = in.read()) >= 0;)
				received.append(Character.toChars(c));

			if (conn.getResponseCode() == 401) {
				this.currentToken = Authentication();

			}
			conn.disconnect();

			// System.out
			// .println("{{SmartIOTProtostub}} [newDevice](" + conn.getResponseCode() + ")"
			// + received.toString());
			conn.disconnect();
			return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
			this.currentToken = Authentication();
			return new JsonObject().put("unauthorised", true);
		}

	}

	private boolean registerNewStream(String deviceID, String streamName) {

		try {
			StringBuilder received = new StringBuilder();
			String urlToCreate = smartIotUrl + "/devices/" + deviceID + "/streams/" + streamName;
			// logger.debug("{{SmartIOTProtostub}} create with url " + urlToCreate +
			// "\nwithtoken->" + currentToken);
			URL url = new URL(urlToCreate);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("PUT");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			// System.out
			// .println("{{SmartIOTProtostub}} [newStream](" + conn.getResponseCode() + ")"
			// + received.toString());
			if (conn.getResponseCode() == 204) {
				return true;
			} else {
				this.currentToken = Authentication();
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			this.currentToken = Authentication();
			return false;
		}

	}

	private boolean deleteStream(String deviceID, String streamName) {

		try {
			StringBuilder received = new StringBuilder();
			String urlToDelete = smartIotUrl + "/devices/" + deviceID + "/streams/" + streamName;
			// logger.debug("{{SmartIOTProtostub}} delete with url " + urlToDelete +
			// "\nwithtoken->" + currentToken);
			URL url = new URL(urlToDelete);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("DELETE");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			conn.disconnect();

			// logger.debug(
			// "{{SmartIOTProtostub}} [deleteStream](" + conn.getResponseCode() + ")" +
			// received.toString());
			if (conn.getResponseCode() == 204) {
				return true;
			} else {
				this.currentToken = Authentication();
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			this.currentToken = Authentication();
			return false;
		}

	}

	private boolean deleteDevice(String deviceID) {

		try {
			StringBuilder received = new StringBuilder();
			String urlToDelete = smartIotUrl + "/devices/" + deviceID;
			// logger.debug("{{SmartIOTProtostub}} delete with url " + urlToDelete +
			// "\nwithtoken->" + currentToken);
			URL url = new URL(urlToDelete);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("DELETE");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			conn.disconnect();

			// logger.debug(
			// "{{SmartIOTProtostub}} [deleteDevice](" + conn.getResponseCode() + ")" +
			// received.toString());
			if (conn.getResponseCode() == 204) {
				return true;
			} else {
				this.currentToken = Authentication();
				return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			this.currentToken = Authentication();
			return false;
		}

	}

	private String getStreamsList(String deviceID) {

		try {
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl + "/devices/" + deviceID + "/streams/");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			for (int c; (c = in.read()) >= 0;)
				received.append(Character.toChars(c));

			conn.disconnect();
			// logger.debug("[STREAMSLIST](" + conn.getResponseCode() + ")" +
			// received.toString());
			return received.toString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private JsonObject createSubscription(String subscriptionName, String subscriptionDescription, String deviceID,
			String streamName) {

		try {
			StringBuilder received = new StringBuilder();
			JsonObject toCreateDevice = new JsonObject();
			toCreateDevice.put("name", subscriptionName);
			toCreateDevice.put("description", subscriptionDescription);
			toCreateDevice.put("subscriber_id", appID);
			toCreateDevice.put("device_id", deviceID);
			toCreateDevice.put("stream", streamName);
			toCreateDevice.put("point_of_contact", pointOfContact);

			URL url = new URL(smartIotUrl + "/subscriptions");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);
			conn.setRequestMethod("POST");

			// add payload Json
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(toCreateDevice.toString());
			wr.flush();

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			for (int c; (c = in.read()) >= 0;)
				received.append(Character.toChars(c));

			conn.disconnect();

			// logger.debug(
			// "{{SmartIOTProtostub}} [newSubscription](" + conn.getResponseCode() + ")" +
			// received.toString());
			return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
