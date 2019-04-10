package hyperty;

import java.util.Arrays;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
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
	private static String pendingSubscriptionsCollection = "pendingSubscriptions";
	private static String pendingDeletesCollection = "pendingDeletes";
	private static String dataObjectsRegistry = "dataObjectsRegistry";
	private static final String ticketsCollection = "tickets";
	private static final String cancelsCollection = "pendingCancels";
	private static final String aggentsCollection = "agents";

	public static final String ticketOngoing = "ongoing";

	// handler URLs
	private static String statusHandler;
	private static String registerHandler;
	private static String subscriptionHandler;
	private static String registryURL;
	private static String offlineHandler;

	@Override
	public void start() {

		super.start();

		logger.debug(logMessage + "start() ");

		registryURL = config().getString("registry");
		statusHandler = config().getString("url") + "/status";
		subscriptionHandler = config().getString("url") + "/subscription";
		registerHandler = config().getString("url") + "/register";
		offlineHandler = config().getString("url") + "offline";

		handleStatusRequests();
		handleSubscriptionRequests();
		handleDORequests();
		handleOfflineHandler();
	}

	private void handleSubscriptionRequests() {

		vertx.eventBus().<JsonObject>consumer(subscriptionHandler, message -> {

			if (mandatoryFieldsValidator(message)) {
				System.out
						.println(logMessage + "handleSubscriptionRequests() new message: " + message.body().toString());

				JsonObject msg = new JsonObject(message.body().toString());
				switch (msg.getString("type")) {
				case "subscribe":
					handleSubscription(msg, message);
					break;
				default:
					logger.debug("Incorrect message type: " + msg.getString("type"));
					break;
				}

			}
		});
	}

	private void handleDORequests() {

		vertx.eventBus().<JsonObject>consumer(registerHandler, message -> {
			// mandatoryFieldsValidator(message);
			JsonObject msg = new JsonObject(message.body().toString());
			logger.debug(logMessage + "handleDORequests(): " + msg);

			switch (msg.getString("type")) {
			case "create":
				dataObjectRegister(message, msg);
				break;
			case "delete":
				dataObjectUnregister(message, msg);
				break;
			default:
				logger.debug("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * Stores message at dataObjectsRegistry data collection and it replies with 200
	 * OK.
	 *
	 * @param message
	 *
	 * @param msg
	 */
	private void dataObjectRegister(Message<JsonObject> message, JsonObject msg) {
		storeMessageInDB(msg, dataObjectsRegistry);
		JsonObject response = new JsonObject().put("code", 200);
		JsonObject responseOK = new JsonObject().put("body", response);
		message.reply(responseOK);
	}

	/**
	 * Removes data object message from dataObjects data collection and it replies
	 * with 200 OK.
	 *
	 * @param message
	 * @param msg
	 */
	private void dataObjectUnregister(Message<JsonObject> message, JsonObject msg) {

		System.out.println("dataObjectUnregister: message" + message.body().toString());
		System.out.println("dataObjectUnregister: msg" + msg.toString());
		/*
		 * removeMessageFromDB(msg, dataObjectsRegistry); JsonObject response = new
		 * JsonObject().put("code", 200); message.reply(response);
		 */
		
		JsonObject body = msg.getJsonObject("body");
		String dataObjectUrl = body.getJsonObject("body").getString("resource");
		System.out.println("dataObject url:" + dataObjectUrl);
		
		JsonObject deleteMsg = body;
		deleteMsg.put("from", dataObjectUrl + "/subscription");
		deleteMsg.put("to", dataObjectUrl + "/changes");
		
		JsonObject query = new JsonObject().put("tickets", new JsonObject().put("$in", new JsonArray().add(dataObjectUrl)));
		System.out.println("query:" + query.toString());
		mongoClient.find(aggentsCollection, query, resultHandler -> {
			
			logger.debug(
					logMessage + " query result " + resultHandler.result().toString());
			
			if(resultHandler.result().size()>0) {
				JsonObject agent = resultHandler.result().get(0);
				String agentGuid = agent.getString("user");
				Future<Boolean> online = queryRegistryAgent(agentGuid);
				online.setHandler(asyncResult -> {
					if (asyncResult.succeeded()) {
						if (online.result()) {
							logger.debug(logMessage + " status result online");
							processPendingDelete(deleteMsg, agentGuid);
						} else {
							logger.debug(logMessage + " status result offline");
							
							JsonObject saveInDB = new JsonObject();
							saveInDB.put("message", deleteMsg);
							saveInDB.put("user", agentGuid);
							JsonObject document = new JsonObject(saveInDB.toString());

							mongoClient.save(pendingDeletesCollection, document, id -> {
								logger.debug(logMessage + "storeMessage(): " + document);
							});
						}
					} else {
						// oh ! we have a problem...
					}
				});
			}

		});
		


	}

	private void handleStatusRequests() {

		vertx.eventBus().<JsonObject>consumer(statusHandler, message -> {
			mandatoryFieldsValidator(message);
			logger.debug(logMessage + "handleStatusRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "update":
				if (msg.getJsonObject("body") != null) {
					statusUpdate(msg.getJsonObject("body"));
				}
				break;
			default:
				logger.debug("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * For all online events received it checks if the CGUID is associated to any
	 * pending subscription at pendingSubscriptions collection and if yes the
	 * processPendingSubscription(subscribeMsg) function is executed
	 *
	 * @param msg
	 */
	private void statusUpdate(JsonObject msg) {
		logger.debug(logMessage + "statusUpdate()  new msg, msg too long" + msg.toString());
		if (msg.getString("status").equals("online")) {

			JsonObject query = new JsonObject().put("user", msg.getString("resource"));
			mongoClient.find(pendingSubscriptionsCollection, query, res -> {
				logger.debug(
						logMessage + " pending statusUpdate(): cguid associated with msgs: " + res.result().toString());
				for (Object obj : res.result()) {
					JsonObject pendingSubscriptionMessage = ((JsonObject) obj).getJsonObject("message");
					processPendingSubscription(pendingSubscriptionMessage, msg.getString("resource"));
				}
			});
			
			mongoClient.find(pendingDeletesCollection, query, res -> {
				logger.debug(
						logMessage + " pending statusUpdate(): cguid associated with msgs: " + res.result().toString());
				for (Object obj : res.result()) {
					JsonObject pendingDeleteMessage = ((JsonObject) obj).getJsonObject("message");
					processPendingDelete(pendingDeleteMessage, msg.getString("resource"));
				}
			});

		}
	}

	boolean walletsExist;

	/**
	 * @param msg
	 * @param message
	 */
	public void handleSubscription(JsonObject msg, Message<JsonObject> message) {

		JsonObject body = msg.getJsonObject("body");

		logger.debug(logMessage + "handleSubscription(): " + body.toString());

		// 1- It queries the Data Objects Registry collection for the data object URL to
		// be subscribed (message.body.resource), and replies with 200 OK where
		// reply.body.value = message.body.value.
		JsonObject query = new JsonObject().put("message.body.body.resource",
				body.getString("to").split("/subscription")[0]);
		mongoClient.find(dataObjectsRegistry, query, res -> {
			JsonObject dataObject = res.result().get(0);
			logger.debug(logMessage + "handleSubscription() reply  " + dataObject.toString());
			JsonObject response = new JsonObject();
			response.put("body", new JsonObject().put("value", dataObject.getJsonObject("message").getJsonObject("body")
					.getJsonObject("body").getJsonObject("value")).put("code", 200));
			message.reply(response);
			// 2- Queries the registry about cguid status.
			Future<Boolean> online = queryRegistry(dataObject);
			online.setHandler(asyncResult -> {
				if (asyncResult.succeeded()) {
					if (online.result()) {
						processPendingSubscription(msg, dataObject.getJsonObject("message").getJsonObject("body")
								.getJsonObject("body").getJsonObject("identity").getString("guid"));
					} else {

						/*
						 * { type: "update", from: "object url", body: { status: "new-participant",
						 * participant: <agent-hyperty-url> } }
						 */

						JsonObject msgUpdate = new JsonObject();
						msgUpdate.put("type", "update");
						msgUpdate.put("from", body.getString("to").split("/subscription")[0]);
						JsonObject bodyMsgUpdate = new JsonObject().put("status", "new-participant").put("participant",
								body.getJsonObject("body").getString("subscriber"));
						msgUpdate.put("body", bodyMsgUpdate);

						publish(offlineHandler, msgUpdate);

						JsonObject saveInDB = new JsonObject();
						saveInDB.put("message", msg);
						saveInDB.put("user", dataObject.getString("user"));
						JsonObject document = new JsonObject(saveInDB.toString());

						mongoClient.save(pendingSubscriptionsCollection, document, id -> {
							logger.debug(logMessage + "storeMessage(): " + document);
						});
					}
				} else {
					// oh ! we have a problem...
				}
			});
//				3- If online it executes the processPendingSubscription(subscribeMsg) otherwise it stores it in the pendingSubscriptions collection.

		});

	}

	/**
	 * Store message in collection
	 *
	 * @param msg
	 */
	private void storeMessageInDB(JsonObject msg, String collection) {
		JsonObject saveInDB = new JsonObject();
		System.out.println("storeMessage msg" + msg.toString());
		saveInDB.put("message", msg);
		saveInDB.put("user",
				msg.getJsonObject("body").getJsonObject("body").getJsonObject("identity").getString("guid"));
		JsonObject document = new JsonObject(saveInDB.toString());
		mongoClient.save(collection, document, id -> {
			logger.debug(logMessage + "storeMessage(): " + document);
		});
	}

	/**
	 * *
	 *
	 * @param subscribeMsg
	 */
	private void processPendingSubscription(JsonObject subscribeMsg, String addressGuid) {

		// Subscribe message is forwarded to subscribeMsg.to and in case a 200 Ok
		// response is received it executes the subscribeMsg is removed from
		// pendingSubscription collection.
		if (subscribeMsg != null && subscribeMsg.containsKey("to")) {
			logger.debug(logMessage + "processPendingSubscription(): " + subscribeMsg.toString());

			logger.debug(logMessage + "forwarding to: " + addressGuid);
			send(addressGuid, subscribeMsg.getJsonObject("body"), reply -> {
				JsonObject body = reply.result().body().getJsonObject("body");
				logger.debug(logMessage + "processPendingSubscription() reply " + body.toString());
				logger.debug(
						logMessage + "processPendingSubscription() reply all msg " + reply.result().body().toString());

				if (body.getInteger("code") == 200) {
					removeMessageFromDB(subscribeMsg, pendingSubscriptionsCollection);
				}
			});
		}
	}

	/**
	 * *
	 *
	 * @param subscribeMsg
	 */
	private void processPendingDelete(JsonObject deleteMsg, String addressGuid) {

		logger.debug(logMessage + "processPendingDelete msg(): " + deleteMsg.toString());
		logger.debug(logMessage + "processPendingDelete guid(): " + addressGuid);

		if (deleteMsg != null && deleteMsg.containsKey("to")) {
			logger.debug(logMessage + "processPendingDelete(): " + deleteMsg.toString());

			logger.debug(logMessage + "forwarding to: " + addressGuid);
			send(addressGuid, deleteMsg, reply -> {
				JsonObject body = reply.result().body().getJsonObject("body");
				logger.debug(logMessage + "pendingDeletesCollection() reply " + body.toString());
				logger.debug(
						logMessage + "pendingDeletesCollection() reply all msg " + reply.result().body().toString());

				if (body.getInteger("code") == 200) {
					removeMessageFromDB(deleteMsg, pendingDeletesCollection);
				}
			});
		}
	}

	/**
	 * Reply message is forwarded to subscribeReply.to and the subscribeReply is
	 * removed from pendingSubscriptionReplies collection.
	 *
	 * @param subscribeReplyMsg
	 */
	private void processPendingSubscriptionReply(JsonObject subscribeReplyMsg) {
		logger.debug(logMessage + "processPendingSubscriptionReply() " + subscribeReplyMsg.toString());
		String forwardAddress = subscribeReplyMsg.getString("to");
		send(forwardAddress, subscribeReplyMsg, reply -> {
		});
//		removeMessageFromDB(subscribeReplyMsg, pendingSubscriptionsRepliesCollection);
	}

	/**
	 * Remove message from collection.
	 *
	 * @param msg
	 * @param collection
	 */
	private void removeMessageFromDB(JsonObject msg, String collection) {
		logger.debug(logMessage + "removeMessageFromDB(): " + msg + " from	 collection " + collection);
		JsonObject query = new JsonObject().put("message.body.resource",
				msg.getJsonObject("body").getString("resource"));

		mongoClient.findOneAndDelete(collection, query, id -> {
		});
	}

	private Future<Boolean> queryRegistry(JsonObject msg) {
		logger.debug(logMessage + "queryRegistry() " + msg.toString());
		Future<Boolean> registryFuture = Future.future();
		JsonObject registryMsg = new JsonObject();
		registryMsg.put("type", "read");
		JsonObject body = new JsonObject();
		body.put("resource", msg.getString("user"));
		registryMsg.put("from", this.url);
		registryMsg.put("identity",
				msg.getJsonObject("message").getJsonObject("body").getJsonObject("body").getJsonObject("identity"));
		registryMsg.put("body", body);

		send(registryURL, registryMsg, reply -> {
			logger.debug(logMessage + "read reply ->" + reply.result().body().toString());
			JsonObject replyValue = reply.result().body().getJsonObject("value");
			logger.debug(logMessage + "queryRegistry() reply " + replyValue.toString());
			registryFuture.complete(replyValue.getString("status").equals("online"));
		});

		return registryFuture;
	}
	
	private Future<Boolean> queryRegistryAgent(String guid) {
		logger.debug(logMessage + "queryRegistry() " + guid);
		Future<Boolean> registryFuture = Future.future();
		JsonObject registryMsg = new JsonObject();
		registryMsg.put("type", "read");
		JsonObject body = new JsonObject();
		body.put("resource", guid);
		registryMsg.put("from", this.url);
		registryMsg.put("identity",new JsonObject());
		registryMsg.put("body", body);

		send(registryURL, registryMsg, reply -> {
			logger.debug(logMessage + "read reply ->" + reply.result().body().toString());
			JsonObject replyValue = reply.result().body().getJsonObject("value");
			logger.debug(logMessage + "queryRegistry() reply " + replyValue.toString());
			registryFuture.complete(replyValue.getString("status").equals("online"));
		});

		return registryFuture;
	}

	private void handleOfflineHandler() {

		vertx.eventBus().<JsonObject>consumer(offlineHandler, message -> {

			logger.debug(logMessage + "handleOffline(): " + message.body().toString());

			/*
			 * { type: "update", from: "object url", body: { status: "new-participant",
			 * participant: <agent-hyperty-url> } }
			 */
			JsonObject msg = new JsonObject(message.body().toString());
			String ticketUrl = msg.getString("from");
			switch (msg.getString("type")) {
			case "update":
				if (msg.getJsonObject("body") != null) {

					JsonObject body = msg.getJsonObject("body");
					String status = body.getString("status");

					if (status.equals("new-participant")) {

						JsonObject query = new JsonObject().put("url", ticketUrl);
						mongoClient.find(ticketsCollection, query, res -> {
							JsonArray results = new JsonArray(res.result());
							JsonObject ticket = results.getJsonObject(0);
							String statusTicket = ticket.getString("status");

							ticket.put("status", ticketOngoing);

							if (statusTicket.equals("pending")) {
								JsonObject document = new JsonObject(ticket.toString());
								mongoClient.findOneAndReplace(ticketsCollection, query, document, id -> {
								});
							}

						});

					}
				}
				break;
			default:
				logger.debug("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

}
