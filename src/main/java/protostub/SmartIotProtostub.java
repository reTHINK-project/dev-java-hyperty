package protostub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

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
	private CountDownLatch createDevice;
	private CountDownLatch findDevice;

	@Override
	public void start() {
		System.out.println("{{SmartIOTProtostub}} Starting SmartIotProtostub");
		this.url = config().getString("url");
		this.collection = config().getString("collection");
		this.database = config().getString("db_name");
		this.mongoHost = config().getString("mongoHost");
		this.smartIotUrl = config().getString("smart_iot_url");
		this.pointOfContact = config().getString("point_of_contact");

		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());

		if (mongoHost != null) {
			System.out.println("{{SmartIOTProtostub}} Setting up Mongo to:" + this.url);
			final String uri = "mongodb://" + mongoHost + ":27017";

			final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", this.database);

			mongoClient = MongoClient.createShared(vertx, mongoconfig);
		}

		this.currentToken = Authentication();
		System.out.println("{{SmartIOTProtostub}} token ->" + this.currentToken);

		RegisterApp();
		System.out.println("{{SmartIOTProtostub}} app -> " + appID + ":" + appSecret);

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
		 * System.out.println("subscription result" + subscription.toString());
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

			System.out.println("{{SmartIOTProtostub}} New message -> " + message.body().toString());
			if (mandatoryFieldsValidator(message)) {

				System.out.println("{{SmartIOTProtostub}} -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
						+ message.body());

				final JsonObject body = new JsonObject(message.body().toString()).getJsonObject("body");
				final String type = message.body().getString("type");

				switch (type) {
				case "read":

					System.out.println("{{SmartIOTProtostub}} read message ");

					break;
				case "create":
					System.out.println("{{SmartIOTProtostub}} create message ");
					// message.reply(new JsonObject());

					if (body.getString("resource").equals("device")) {
						handleDeviceCreationRequest(message);
					} else if (body.getString("resource").equals("stream")) {
						handleStreamCreationRequest(message);
					}

					break;
				default:
					break;
				}

			}
		};
	}

	private void handleDeviceCreationRequest(Message<JsonObject> message) {
		// TODO Auto-generated method stub
		final JsonObject messageToCreate = new JsonObject(message.body().toString());

		final JsonObject userProfile = messageToCreate.getJsonObject("identity").getJsonObject("userProfile");

		final String guid = userProfile.containsKey("guid") ? userProfile.getString("guid") : null;
		JsonObject responseDenied = new JsonObject().put("body", new JsonObject().put("code", 406));
		JsonObject responseOK = new JsonObject().put("body", new JsonObject().put("code", 200));

		if (guid != null) {

			final JsonObject body = messageToCreate.getJsonObject("body");
			final String description = body.containsKey("description") ? body.getString("description") : null;
			final String name = guid.split("//")[1];

			createDevice = new CountDownLatch(2);

			new Thread(() -> {
				mongoClient.find(this.collection, new JsonObject().put("guid", guid), res -> {
					createDevice.countDown();
					if (res.result().size() == 0) {

						System.out.println("{{SmartIOTProtostub}} no device yet, creating");

						JsonObject newDevice = registerNewDevice(name, description);

						if (newDevice != null) {
							System.out.println("{{SmartIOTProtostub}} Device for " + name + "  -> |||  create with id->"
									+ newDevice.getString("id"));

							JsonObject document = new JsonObject().put("guid", guid).put("device", newDevice);
							mongoClient.save(this.collection, document, id -> {
								createDevice.countDown();
								System.out.println("{{SmartIOTProtostub}} New device added to mongo" + id);
								message.reply(responseOK);
							});
						} else {
							System.out.println("{{SmartIOTProtostub}} Creation request failed");
							createDevice.countDown();
							message.reply(responseDenied);
						}
					} else {
						System.out.println("{{SmartIOTProtostub}} Device already created for this user");
						createDevice.countDown();
						message.reply(responseDenied);
					}
				});

			}).start();

			try {
				createDevice.await(5L, TimeUnit.SECONDS);

				return;
			} catch (InterruptedException e) {
				System.out.println(e);
			}
			return;
		}

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
		JsonObject responseOK = new JsonObject().put("body", new JsonObject().put("code", 200));

		if (guid != null) {
			System.out.println("{{SmartIOTProtostub}} search Device of guid->" + guid);
			final JsonObject body = messageToCreate.getJsonObject("body");
			final String streamName = body.containsKey("platformUserId") ? null : body.getString("platformUID");
			final String deviceID = findDevice(guid);

			System.out.println("{{SmartIOTProtostub}} DeviceID returned->" + deviceID + " ->streamName:" + streamName);
			if (deviceID != null && streamName != null) {

				if (registerNewStream(deviceID, streamName)) {

					JsonArray streamList = new JsonObject(getStreamsList(deviceID)).getJsonArray("streams");
					System.out.println("{{SmartIOTProtostub}} - stream list" + streamList.toString());

					int x;
					for (x = 0; x < streamList.size(); x++) {
						JsonObject currentStream = streamList.getJsonObject(x);
						if (currentStream.getString("name").equals(streamName)) {
							System.out.println("{{SmartIOTProtostub}} stream created was" + currentStream.toString());
							JsonObject ctx = new JsonObject().put("contextValue", "");
							currentStream.put("context", ctx);

							JsonObject update = new JsonObject().put("$push",
									new JsonObject().put("device.stream_list", currentStream));

							System.out.println("{{SmartIOTProtostub}} push" + update.toString());

							mongoClient.updateCollection(this.collection, new JsonObject().put("guid", guid), update,
									res -> {
										System.out.println("result>" + res.succeeded());

									});

						}
					}

					message.reply(responseOK);
				}
			}

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

	private String findDevice(String guid) {

		final String device[] = new String[1];
		findDevice = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find(this.collection, new JsonObject().put("guid", guid), res -> {
				if (res.result().size() != 0) {
					JsonObject deviceFound = res.result().get(0).getJsonObject("device");
					System.out.println("3,5" + deviceFound.getString("id"));
					device[0] = deviceFound.getString("id");
					System.out.println("3,9" + device[0]);
				}
				findDevice.countDown();
			});

		}).start();

		try {
			findDevice.await(5L, TimeUnit.SECONDS);
			return device[0];
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		return device[0];
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

			System.out
					.println("{{SmartIOTProtostub}} [newToken](" + conn.getResponseCode() + ")" + received.toString());
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

			conn.disconnect();

			System.out
					.println("{{SmartIOTProtostub}} [newDevice](" + conn.getResponseCode() + ")" + received.toString());
			return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private boolean registerNewStream(String deviceID, String streamName) {

		try {
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl + "/devices/" + deviceID + "/streams/" + streamName);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("PUT");

			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			for (int c; (c = in.read()) >= 0;)
				received.append(Character.toChars(c));

			conn.disconnect();

			System.out
					.println("{{SmartIOTProtostub}} [newStream](" + conn.getResponseCode() + ")" + received.toString());
			if (conn.getResponseCode() == 204) {
				return true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;

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
			System.out.println("[STREAMSLIST](" + conn.getResponseCode() + ")" + received.toString());
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

			System.out.println(
					"{{SmartIOTProtostub}} [newSubscription](" + conn.getResponseCode() + ")" + received.toString());
			return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
