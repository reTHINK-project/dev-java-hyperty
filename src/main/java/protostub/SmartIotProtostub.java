package protostub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.codec.binary.Base64;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
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
		//new device
		JsonObject newDevice = registerNewDevice("device name", "device description");
		
		//register stream
		String streamName = "userguidddd-dddddd-dddasdas-idddd-dddddd-dsdas";
		registerNewStream(newDevice.getString("id"), streamName);

		//new subscription
		JsonObject subscription = createSubscription("subcriptionName", "subscriptionDescription", newDevice.getString("id"), streamName);
		System.out.println("subscription result" + subscription.toString());
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
				final JsonObject identity = new JsonObject(message.body().toString()).getJsonObject("identity");
				final String type = new JsonObject(message.body().toString()).getString("type");
				JsonObject response = new JsonObject();
				switch (type) {
				case "read":

					System.out.println("{{SmartIOTProtostub}} read message ");
					

					break;
				case "create":
					System.out.println("{{SmartIOTProtostub}} create message ");

					if (body.getString("resource").equals("device")) {
						System.out.println("new device->" + registerNewDevice(body.getString("name"), body.getString("description")));
					}
					break;
				default:
					break;
				}

			}
		};
	}

	/**
	 * 
	 * @param message
	 * @return true when mandatory fields are defined
	 */
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
			String user = "5b1e2f6a-81e6-475b-b494-64a30908f4c7";
			String password = "johnll3p7pd2m9e4mcsqhst4eqnnnk34s65397npb8e59tjuqku6";
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

			System.out.println("[newToken](" + conn.getResponseCode() + ")" + received.toString());
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
			toCreateDevice.put("description", description);

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

			System.out.println("{{SmartIOTProtostub}} [newDevice](" + conn.getResponseCode() + ")" + received.toString());
			return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private boolean registerNewStream(String deviceID, String streamName) {
		
		try {
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl+"/devices/"+ deviceID + "/streams/" + streamName);
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
	        conn.setRequestMethod("PUT");

	        conn.setRequestProperty("authorization","Bearer " + currentToken);

	        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

	        for (int c; (c = in.read()) >= 0;)
	            received.append(Character.toChars(c));

	        conn.disconnect();

	        System.out.println("{{SmartIOTProtostub}} [newStream]("+conn.getResponseCode()+")" + received.toString());
	        if (conn.getResponseCode() == 204) {
	        	return true;
	        }

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
		
	}
	
	private JsonObject createSubscription(String subscriptionName, String subscriptionDescription, String deviceID,
			String streamName) {
		
		try {
			StringBuilder received = new StringBuilder();
			JsonObject toCreateDevice   = new JsonObject();
			toCreateDevice.put("name", subscriptionName);
			toCreateDevice.put("description", subscriptionDescription);
			toCreateDevice.put("subscriber_id", appID);
			toCreateDevice.put("device_id", deviceID);
			toCreateDevice.put("stream", streamName);
			toCreateDevice.put("point_of_contact", pointOfContact);
			
			URL url = new URL(smartIotUrl+"/subscriptions");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("Content-Type", "application/json");
	        conn.setRequestProperty("authorization","Bearer " + currentToken);
	        conn.setRequestMethod("POST");
	        
	        //add payload Json
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(toCreateDevice.toString());
			wr.flush();
	        
	        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
	        
	        for (int c; (c = in.read()) >= 0;)
	            received.append(Character.toChars(c));
  
	        conn.disconnect();

	        System.out.println("[newSubscription]("+conn.getResponseCode()+")" + received.toString());
	        return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;		
	}
	

}
