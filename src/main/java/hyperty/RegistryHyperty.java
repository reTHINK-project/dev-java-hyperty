package hyperty;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import data_objects.DataObjectReporter;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;

/**
 * This runtime feature is responsible to keep track of the status of users
 * (online / offline) using hyperties executed in the Vertx Runtime, based on
 * status events published by Vertx Runtime Protostub.
 * 
 * @author felgueiras
 */
public class RegistryHyperty extends AbstractHyperty {



	private static final String logMessage = "[Registry] ";

	/**
	 * frequency in seconds to execute checkStatus process.
	 */
	int checkStatusTimer;

	@Override
	public void start() {

		super.start();
		handleRequests();
		
		checkStatusTimer = config().getInteger("checkStatusTimer");
		Timer timer = new Timer();
		timer.schedule(new CheckStatusTimer(), 0, checkStatusTimer);

	}

	class CheckStatusTimer extends TimerTask {
		public void run() {
			checkStatus();
		}
	}

	/**
	 * This function is executed by a timer every config.checkStatusTimer seconds.
	 * 
	 * For each entry in the registry collection where timeNow - lastModified >
	 * config.checkStatusTimer it updates its status to offline, and publishes its
	 * new status (ensure this event is not processed by the registry status handler
	 * specified above).
	 */
	private void checkStatus() {

		System.out.println(logMessage + "checkStatus()");
		Long timeNow = new Date().getTime();

		mongoClient.find(collection, new JsonObject(), res -> {
			JsonArray registryEntries = new JsonArray(res.result());
			for (JsonObject entry : res.result()) {
				String status = entry.getString("status");
				if (status.equals("online")) {
					Long lastModified = entry.getLong("lastModified");
					System.out.println("test" + "\nlm:" + lastModified + " \ntimenow" + timeNow );
					if (timeNow - lastModified > checkStatusTimer) {
						
							entry.put("status", "offline");
							entry.put("lastModified", timeNow);
							mongoClient.findOneAndReplace(collection, new JsonObject().put("guid", entry.getString("guid")),
									entry, id -> {
										System.out.println(logMessage + "checkStatus() document updated: " + entry);
									});
					}
				}
			}
		});
	}


	/**
	 * Handle requests.
	 */
	private void handleRequests() {
		System.out.println("Waiting on ->" + config().getString("url") + "/status");
		vertx.eventBus().<JsonObject>consumer(config().getString("url") + "/status", message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "update":
				if (msg.getJsonObject("body") != null) {
					updateStatus(msg, message);
				}
				break;
			case "read":
				if (msg.getJsonObject("body") != null) {
					retrieveStatus(msg, message);
				}
				break;

			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * It updates the registry collection with received info including last modified
	 * timestamp.
	 * 
	 * @param msg
	 * @param message 
	 */
	private void updateStatus(JsonObject msg, Message<JsonObject> message) {
		System.out.println(logMessage + "updateStatus(): " + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		String guid = body.getString("resource");
		String status = body.getString("status");
		long lastMofidified = new Date().getTime();;

		// get entry for that cguid
		CountDownLatch registryLatch = new CountDownLatch(1);
		// check if no user allocated to this code
		new Thread(() -> {
			JsonObject query = new JsonObject().put("guid", guid);
			mongoClient.find(collection, query, res -> {
				JsonObject entry = new JsonArray(res.result()).getJsonObject(0);
				// set identity
				entry.put("status", status);
				entry.put("lastModified", lastMofidified);
				JsonObject document = new JsonObject(entry.toString());
				mongoClient.findOneAndReplace(collection, new JsonObject().put("guid", entry.getString("guid")),
						document, id -> {
							System.out.println(logMessage + "updateStatus(): registry updated" + document);
						});
				registryLatch.countDown();
				JsonObject response = new JsonObject().put("code",200);
				message.reply(response);
			});
		}).start();
		try {
			registryLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	
	
	
	private void retrieveStatus(JsonObject msg, Message<JsonObject> message) {
		System.out.println(logMessage + "updateStatus(): " + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		String guid = body.getString("resource");

		// get entry for that cguid
		CountDownLatch statusLatch = new CountDownLatch(1);
		// check if no user allocated to this code
		new Thread(() -> {
			JsonObject query = new JsonObject().put("guid", guid);
			mongoClient.find(collection, query, res -> {
				JsonObject response ;
				if (res.result().size() > 0) {
					JsonObject entry = new JsonArray(res.result()).getJsonObject(0);
					response = new JsonObject().put("code",200).put("value", entry);
					
				} else {
					response = new JsonObject().put("code",404).put("value", new JsonObject());
				}
				message.reply(response);

				statusLatch.countDown();
			});
		}).start();
		try {
			statusLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}