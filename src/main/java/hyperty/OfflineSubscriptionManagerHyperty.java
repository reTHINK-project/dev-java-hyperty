package hyperty;

import java.util.concurrent.CountDownLatch;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

/**
 * Provides functionalities to support data streams synchronization setup
 * between peers without requiring to have both online simultaneously.
 * 
 * @author felgueiras
 *
 */
public class OfflineSubscriptionManagerHyperty extends AbstractHyperty {

	private static final String logMessage = "[OfflineSubMgr] ";
	private String subscriptionsCollection = "pendingsubscriptions";

	// handler URLs
	private static String statusHandler;
	private static String registryURL;

	@Override
	public void start() {

		super.start();
		
		System.out.println(logMessage + "start() ");

		registryURL = config().getString("registry");
		statusHandler = config().getString("url") + "/status";

		handleStatusRequests();
	}

	@Override
	public Handler<Message<JsonObject>> onMessage() {

		return message -> {

			if (mandatoryFieldsValidator(message)) {
				System.out.println(logMessage + "handleSubscriptions() new message: " + message.body().toString());

				JsonObject msg = new JsonObject(message.body().toString());
				switch (msg.getString("type")) {
				case "subscribe":
					newSubscription(msg, message);
					break;
				default:
					System.out.println("Incorrect message type: " + msg.getString("type"));
					break;
				}

			}
		};
	}

	private void handleStatusRequests() {

		vertx.eventBus().<JsonObject>consumer(statusHandler, message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleStatusRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "update":
				if (msg.getJsonObject("body") != null) {
					statusUpdate(msg.getJsonObject("body"));
				}
				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * For all online events received it checks if the CGUID is associated to any
	 * pending subscription and if yes the processPendingSubscription(subscribeMsg)
	 * function is executed.
	 * 
	 * @param jsonObject
	 */
	private void statusUpdate(JsonObject msg) {
		System.out.println(logMessage + "statusUpdate() " + msg.toString());
		if (msg.getString("status").equals("online")) {

			JsonObject query = new JsonObject().put("user", msg.getString("resource"));
			mongoClient.find(collection, query, res -> {
				System.out.println(logMessage + "statusUpdate() reply " + res.result().toString());
				for (Object obj : res.result()) {
					JsonObject pendingMessage = (JsonObject) obj;
					processPendingSubscription(pendingMessage);
				}
			});
		}
	}

	boolean walletsExist;

	/**
	 * It Checks that received body.code is in the config.agents array and if there
	 * is still no user allocated in the AgentsPool, it updates it the new user
	 * agent CGUID.
	 * 
	 * @param msg
	 * @param message
	 */
	public void newSubscription(JsonObject msg, Message<JsonObject> message) {

		/*
		 * "id" : 2,</br> "type" : "subscribe", "from" :
		 * "hyperty-runtime://<observer-sp-domain>/<hyperty-observer-runtime-instance-identifier>/sm",
		 * "to" : "<ObjectURL>/subscription", "body" : { "source" :
		 * "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>" }
		 */
		JsonObject body = msg.getJsonObject("body");
		System.out.println(logMessage + "newSubscription(): " + body.toString());

		// 1 - It replies with 200 OK
		JsonObject response = new JsonObject().put("code", 200);
		message.reply(response);
		// 2- Queries the registry about cguid status.
		boolean online = queryRegistry(msg);

		// 3 - If online it executes the processPendingSubscription(subscribeMsg)
		// otherwise it stores it in the pendingSubscriptions collection.
		if (online) {
			processPendingSubscription(msg);
		} else {
			storeMessageInDB(msg);
		}
	}

	/**
	 * Store message in collection
	 * 
	 * @param msg
	 */
	private void storeMessageInDB(JsonObject msg) {
		JsonObject saveInDB = new JsonObject();
		saveInDB.put("message", msg);
		saveInDB.put("user", msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid"));
		JsonObject document = new JsonObject(saveInDB.toString());
		mongoClient.save(subscriptionsCollection, document, id -> {
			System.out.println(logMessage + "storeMessage(): " + document);
		});
	}

	/**
	 * Subscribe message is forwarded to subscribeMsg.to and in case a 200 Ok
	 * response is received the subscribeMsg is removed from pendingSubscription
	 * collection.
	 * 
	 * @param msg
	 */
	private void processPendingSubscription(JsonObject msg) {
		System.out.println(logMessage + "processPendingSubscription(): " + msg.toString());
		String forwardAddress = msg.getString("to");
		send(forwardAddress, msg, reply -> {
			JsonObject body = reply.result().body().getJsonObject("body");
			System.out.println(logMessage + "processPendingSubscription() reply " + body.toString());
			if (body.getInteger("code") == 200) {
				removeMessageFromDB(msg);
			}
		});
	}

	/**
	 * Remove message from collection
	 * 
	 * @param msg
	 */
	private void removeMessageFromDB(JsonObject msg) {
		System.out.println(logMessage + "removeMessageFromDB(): " + msg);
		JsonObject query = new JsonObject().put("id", msg.getString("id"));
		mongoClient.findOneAndDelete(subscriptionsCollection, query, id -> {
		});
	}

	JsonObject queryReply;

	private boolean queryRegistry(JsonObject msg) {
		System.out.println(logMessage + "queryRegistry() " + msg.toString());
		CountDownLatch registryLatch = new CountDownLatch(1);
		JsonObject registryMsg = new JsonObject();
		registryMsg.put("type", "read");
		JsonObject body = new JsonObject();
		body.put("resource", msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid"));
		registryMsg.put("type", "read");
		registryMsg.put("body", body);

		new Thread(() -> {
			send(registryURL, registryMsg, reply -> {
				JsonObject replyBody = reply.result().body().getJsonObject("body");
				System.out.println(logMessage + "queryRegistry() reply " + replyBody.toString());
				queryReply = replyBody;
				registryLatch.countDown();
			});
		}).start();

		try {
			registryLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return queryReply.getJsonObject("entry").getString("status", "offline").equals("online");
	}

}
