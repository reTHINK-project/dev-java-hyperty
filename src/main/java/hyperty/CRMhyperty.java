package hyperty;

import java.util.concurrent.CountDownLatch;

import data_objects.DataObjectReporter;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CRMhyperty extends AbstractHyperty {

	private String agentsCollection = "agents";

	private static final String logMessage = "[CRM] ";

	@Override
	public void start() {

		super.start();

		handleAgentRequests();
		handleTicketRequests();

		JsonArray agentsConfig = config().getJsonArray("agents");
		if (agentsConfig != null) {
			createAgents(agentsConfig);
		}

	}

	boolean walletsExist;

	/**
	 * Create agents
	 * 
	 * @param agentsConfig
	 */
	public void createAgents(JsonArray agentsConfig) {

		CountDownLatch agentsLatch = new CountDownLatch(1);
		walletsExist = false;

		System.out.println(logMessage + "createAgents(): " + agentsConfig.toString());

		// for each agent in config...
		// ...check if it exists

		for (Object agent : agentsConfig) {
			JsonObject agentJson = (JsonObject) agent;
			System.out.println(agent);
			new Thread(() -> {
				JsonObject query = new JsonObject().put("code", agentJson.getString("code"));
				mongoClient.find(agentsCollection, query, res -> {
					JsonArray results = new JsonArray(res.result());
					if (results.size() == 0) {
						// set identity
						JsonObject newAgent = new JsonObject();
						newAgent.put("code", agentJson.getString("code"));
//						newAgent.put("user", agentJson.getString("name"));
						newAgent.put("user", "");
						newAgent.put("openedTickets", 0);
						newAgent.put("status", "online");
						JsonObject document = new JsonObject(newAgent.toString());
						mongoClient.save(agentsCollection, document, id -> {
							System.out.println(logMessage + "createAgents(): new agent " + document);
						});
						agentsLatch.countDown();
					} else {
						// already exists
						agentsLatch.countDown();
					}
				});
			}).start();
			try {
				agentsLatch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> subscriptionHandler() {
		return msg -> {
			mongoClient.find("wallets", new JsonObject(), res -> {
				JsonArray quizzes = new JsonArray(res.result());
				// reply with elearning info
				msg.reply(quizzes);
			});
		};

	}

	/**
	 * Handle requests.
	 */
	private void handleAgentRequests() {

		vertx.eventBus().<JsonObject>consumer(config().getString("url"), message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "delete":
//				walletDelete(msg);
				break;
			case "create":
				if (msg.getJsonObject("body") != null) {
					handleTransfer(msg);
				}
				break;
			case "read":
				JsonObject body = msg.getJsonObject("body");
				final String resource = body.getString("resource");
				System.out.println("Resource is " + resource);
				switch (resource) {
				case "user":
					// Wallet address request
					walletAddressRequest(msg, message);
					break;
				case "wallet":
					// Wallet read
					walletRead(msg, message);
					break;
				default:
					break;
				}

				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	private void handleTicketRequests() {

		vertx.eventBus().<JsonObject>consumer(config().getString("url") + "/agents", message -> {
			mandatoryFieldsValidator(message);
			System.out.println(logMessage + "handleTicketRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "create":
				if (msg.getJsonObject("body") != null) {
					handleNewTicket(msg);
				}
				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	JsonArray newTickets = new JsonArray();

	private void handleNewTicket(JsonObject msg) {
		JsonObject ticket = msg.getJsonObject("body").getJsonObject("ticket");
		System.out.println(logMessage + "handleNewTicket(): " + ticket.toString());

		/**
		 * 1- It forwards the message to all agents and add the new ticket to newTickets
		 * array.
		 **/
		newTickets.add(ticket);

		/**
		 * 2- The first agent executes ticketAccepted function: the ticket is allocated
		 * to the agent in the agentsPool collection, the ticket is removed from the
		 * pendingTickets array and a delete message is sent to all remaining invited
		 * Agents (todo: specify this new message that should be similar to delete msg
		 * used to remove user from chat).
		 **/

		/**
		 * 3- In case no agent accepts the ticket, ie a timeout message is received for
		 * all invited Agents the message is moved from newTickets array to
		 * pendingTickets array.
		 */
	}

	JsonObject foundAgent;

	/**
	 * It Checks that received body.code is in the config.agents array and if there
	 * is still no user allocated in the AgentsPool, it updates it the new user
	 * agent CGUID.
	 * 
	 * @param msg
	 */
	@Override
	public void handleTransfer(JsonObject msg) {

		JsonObject body = msg.getJsonObject("body");
		String code = body.getString("code");
		String guid = body.getString("guid");
		System.out.println(logMessage + "handleAgentRegistration(): " + code);

		foundAgent = null;

		JsonArray agentsConfig = config().getJsonArray("agents");
		for (Object agent : agentsConfig) {
			JsonObject agentJson = (JsonObject) agent;
			if (agentJson.getString("code").equals(code)) {
				foundAgent = agentJson;
				break;
			}
		}

		if (foundAgent != null) {
			CountDownLatch agentsLatch = new CountDownLatch(1);
			// check if no user allocated to this code
			new Thread(() -> {
				JsonObject query = new JsonObject().put("code", foundAgent.getString("code"));
				mongoClient.find(agentsCollection, query, res -> {
					JsonObject agentInCollection = new JsonArray(res.result()).getJsonObject(0);
					if (agentInCollection.getString("user").equals("")) {
						// set identity
						agentInCollection.put("user", guid);
						JsonObject document = new JsonObject(agentInCollection.toString());
						mongoClient.save(agentsCollection, document, id -> {
							System.out.println(logMessage + "handleAgentRegistration(): agent updated" + document);
						});
						agentsLatch.countDown();
					} else {
						// already exists
						agentsLatch.countDown();
					}
				});
			}).start();
			try {
				agentsLatch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

	public void inviteObservers(String dataObjectUrl, Handler<Message<JsonObject>> subscriptionHandler,
			Handler<Message<JsonObject>> readHandler) {
		// An invitation is sent to config.observers
		DataObjectReporter reporter = create(dataObjectUrl, new JsonObject(), true, subscriptionHandler, readHandler);
		reporter.setMongoClient(mongoClient);
		// pass handler function that will handle subscription events
		// reporter.setSubscriptionHandler(requestsHandler);
		// reporter.setReadHandler(readHandler);
	}

	JsonObject walletToReturn;

	/**
	 * Return wallet address for a user.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletAddressRequest(JsonObject msg, Message<JsonObject> message) {
		System.out.println("Getting wallet address  msg:" + msg.toString());
		JsonObject body = msg.getJsonObject("body");
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("guid", body.getString("value")));

		JsonObject toSearch = new JsonObject().put("identity", identity);

		System.out.println("Search on " + this.collection + "  with data" + toSearch.toString());

		mongoClient.find(this.collection, toSearch, res -> {
			if (res.result().size() != 0) {
				JsonObject walletInfo = res.result().get(0);
				// reply with address
				System.out.println("Returned wallet: " + walletInfo.toString());
				message.reply(walletInfo);
			}
		});

	}

	/**
	 * Return wallet.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletRead(JsonObject msg, Message<JsonObject> message) {
		JsonObject body = msg.getJsonObject("body");
		String walletAddress = body.getString("value");
		System.out.println(logMessage + "walletRead(): getting wallet for address " + walletAddress);

		mongoClient.find(agentsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject wallet = res.result().get(0);
			System.out.println(logMessage + "walletRead(): " + wallet);
			message.reply(wallet.toString());
		});

	}

	String causeID;

	String causeAddress = "";

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> requestsHandler() {
		return msg -> {
			System.out.println("REQUESTS HANDLER: " + msg.body().toString());
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", "");
			response.put("to", msg.body().getString("from"));
			JsonObject sendMsgBody = new JsonObject();
			if (validateSource(from, msg.body().getString("address"), msg.body().getJsonObject("identity"),
					agentsCollection)) {
				sendMsgBody.put("code", 200);
				response.put("body", sendMsgBody);
				System.out.println("REQUESTS HANDLER reply");
				msg.reply(response);
			} else {
				sendMsgBody.put("code", 403);
				response.put("body", sendMsgBody);
				msg.reply(response);
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
			System.out.println("READ HANDLER: " + msg.body().toString());
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", "");
			response.put("to", msg.body().getString("from"));

			JsonObject sendMsgBody = new JsonObject();
			if (!validateSource(from, msg.body().getString("address"), msg.body().getJsonObject("identity"),
					agentsCollection)) {
				sendMsgBody.put("code", 403);
				response.put("body", sendMsgBody);
				msg.reply(response);
			}

			mongoClient.find(agentsCollection, new JsonObject().put("identity", identity), res -> {
				JsonObject wallet = res.result().get(0);
				System.out.println(wallet);

				sendMsgBody.put("code", 200).put("wallet", wallet);
				response.put("body", sendMsgBody);
				msg.reply(response);
			});

		};

	}

}
