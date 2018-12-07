package runHyperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class testConnectionSIOT {

	static String smartIotUrl = "https://iot.alticelabs.com/api";
	static String currentToken;
	private static Vertx vertx;
	protected static MongoClient mongoClient = null;

	public static void main(String[] args) throws ParseException {
		// TODO Auto-generated method stub
		String name = "newDeviceName1";
		String description = "newDeviceDescription1";
		currentToken = getNewToken();
		/*JsonObject newDevice = registerNewDevice(name, description);
		JsonObject app = registerNewDevice("app name", "app description");
		String appID = app.getString("id");
		String appSecret = app.getString("secret");
		String streamName = "userguidddd-dddddd-dddasdas-idddd-dddddd-dsdas";
		String pointOfContact = "https://pointofcontactURL";*/
		registerNewStream("9b42a603-e1a0-499c-a763-ee5340ecddc4", "test8");
	/*	getStreamsList(newDevice.getString("id"));
		deleteStream(newDevice.getString("id"), streamName);

		JsonObject subscription = createSubscription("suscriptionName", "subscriptionDescription", appID, newDevice.getString("id"), streamName, pointOfContact);
		//System.out.println("subscription result" + subscription.toString());
*/


	}

	private static String getNewToken() {
		try {
			String user = "luis";
			String password = "vr6hamqs1tgb2fe0dfmj7r4l1fv4bf2v1rrjcbi3uv7ve5imv506";
			String toEncode = user + ":" + password;
			byte[] encodedBytes = Base64.encodeBase64(toEncode.getBytes());

			String encodedString = new String(encodedBytes);
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl+"/accounts/token");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
	        conn.setRequestMethod("POST");

	        conn.setRequestProperty("authorization","Basic " + encodedString);

	        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

	        for (int c; (c = in.read()) >= 0;)
	            received.append(Character.toChars(c));

	        conn.disconnect();
	        //System.out.println("[newToken]("+conn.getResponseCode()+")" + received.toString());

	        if (conn.getResponseCode() == 200) {
	        	return received.toString();
	        } else return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static JsonObject registerNewDevice(String name, String description) {

		try {

			StringBuilder received = new StringBuilder();
			JsonObject toCreateDevice   = new JsonObject();
			toCreateDevice.put("name", name);
			toCreateDevice.put("description", description);

			URL url = new URL(smartIotUrl+"/devices");
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
	        //System.out.println("[newDevice]("+conn.getResponseCode()+")" + received.toString());
	        return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;


	}

	private static void registerNewStream(String deviceID, String streamName) {

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
	        //System.out.println("[newStream]("+conn.getResponseCode()+")" + received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static JsonObject createSubscription(String subscriptionName, String subscriptionDescription, String appID, String deviceID,
			String streamName, String pointOfContact) {

		try {
			StringBuilder received = new StringBuilder();
			JsonObject toCreateDevice   = new JsonObject();
			toCreateDevice.put("name", subscriptionName);
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

	        //System.out.println("[newSubscription]("+conn.getResponseCode()+")" + received.toString());
	        return new JsonObject(received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private static void getStreamsList(String deviceID) {

		try {
			StringBuilder received = new StringBuilder();
			URL url = new URL(smartIotUrl+"/devices/"+ deviceID + "/streams/");
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
	        conn.setRequestMethod("GET");

	        conn.setRequestProperty("authorization","Bearer " + currentToken);

	        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

	        for (int c; (c = in.read()) >= 0;)
	            received.append(Character.toChars(c));

	        conn.disconnect();
	        //System.out.println("[STREAMSLIST]("+conn.getResponseCode()+")" + received.toString());

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static boolean deleteStream(String deviceID, String streamName) {

		try {
			StringBuilder received = new StringBuilder();
			String urlToDelete = smartIotUrl + "/devices/" + deviceID + "/streams/" + streamName;
			//System.out.println("{{SmartIOTProtostub}} delete with url " + urlToDelete +"\nwithtoken->" + currentToken);
			URL url = new URL(urlToDelete);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("DELETE");
			conn.setRequestProperty("authorization", "Bearer " + currentToken);

			conn.disconnect();

			//System.out.println("{{SmartIOTProtostub}} [deleteStream](" + conn.getResponseCode() + ")" + received.toString());
			if (conn.getResponseCode() == 204) {
				return true;
			}

		} catch (Exception e) {

			return false;
		}
		return false;

	}
}
