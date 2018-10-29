package hyperty;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.mongodb.operation.UserExistsOperation;

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
	String CRMHypertyStatus;
	String offlineSMStatus;
	boolean userExist;

	@Override
	public void start() {

		super.start();
		handleRequests();
		
		checkStatusTimer = config().getInteger("checkStatusTimer");
		CRMHypertyStatus = config().getString("CRMHypertyStatus");
		offlineSMStatus = config().getString("offlineSMStatus");
		
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
							
							JsonObject body = new JsonObject();
							body.put("resource", entry.getString("guid"));
							body.put("status", "offline");
							
							JsonObject updateMessage = new JsonObject().put("type", "update");
							updateMessage.put("body", body);
							publish(CRMHypertyStatus, updateMessage);
							publish(offlineSMStatus, updateMessage);
							
					}
				}
			}
		});
	}


	/**
	 * Handle requests.
	 */
	private void handleRequests() {
		System.out.println("Waiting on ->" + config().getString("url") + "/registry");
		vertx.eventBus().<JsonObject>consumer(config().getString("url") + "/registry", message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());
			JsonObject response = new JsonObject();
			
			
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
			case "create":
				if (msg.getJsonObject("body") != null) {
					handleCreationRequest(msg, message);
					JsonObject body = new JsonObject().put("code", 200);
					response.put("body", body);
					message.reply(response);
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
				//publish message to crmStatus and offlineSMstatus
				publish(CRMHypertyStatus, msg);
				publish(offlineSMStatus, msg);
				
				registryLatch.countDown();
				JsonObject response = new JsonObject().put("code",200);
				message.reply(response);
			});
		}).start();
		try {
			registryLatch.await(5L, TimeUnit.SECONDS);
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
	
	@Override
	public Handler<Message<JsonObject>> onMessage() {

		return message -> {


			System.out.println(logMessage + "New message -> " + message.body().toString());
			if (mandatoryFieldsValidator(message)) {

				System.out.println(logMessage + "[NewData] -> [Worker]-" + Thread.currentThread().getName()
						+ "\n[Data] " + message.body());
				/*
				final String type = new JsonObject(message.body().toString()).getString("type");
				final JsonObject identity = new JsonObject(message.body().toString()).getJsonObject("identity");

				JsonObject response = new JsonObject();
				JsonObject body = new JsonObject().put("code", 200);
				response.put("body", body);
				switch (type) {

				case "create":

					JsonObject msg = new JsonObject(message.body().toString());
					handleCreationRequest(msg, message);
					message.reply(response);
					break;
				default:
					break;
				}*/

			}
		};
		
}
	
	@Override
	public void handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("REGISTRY HANDLE NEW ENTRY->" + msg.toString());
		if (msg.containsKey("identity")) {
			final String guid = msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid");
			if(!userExists(guid)) {
				addNewUser(guid);
				
			} else {
				System.out.println("user already exist");
			}
		}
	}
	

	private boolean userExists(String guid) {
		userExist = false;
		
		CountDownLatch getUserStatus= new CountDownLatch(1);


		new Thread(() -> {

			JsonObject query = new JsonObject().put("guid",guid);

			mongoClient.find(this.collection, query, res -> {

				JsonArray users = new JsonArray(res.result());
				if (users.size() != 0)
					userExist = true;
				getUserStatus.countDown();
			});
		}).start();

		try {
			getUserStatus.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return userExist;
	}
	
	private void addNewUser(String guid) {

		
		CountDownLatch addUser= new CountDownLatch(1);


		new Thread(() -> {

			Long date = new Date().getTime();
			JsonObject newUser = new JsonObject().put("guid",guid)
					.put("status", "online")
					.put("lastModified", date);
			JsonObject toUpdate = new JsonObject();
			toUpdate.put("type", "update");
			
			mongoClient.save(this.collection, newUser, id -> {
				System.out.println(logMessage + " new user " + id);
				newUser.remove("lastModified");
				toUpdate.put("body", newUser);
				publish(CRMHypertyStatus, toUpdate);
				publish(offlineSMStatus, toUpdate);
				addUser.countDown();
			});

		}).start();

		try {
			addUser.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}


}
