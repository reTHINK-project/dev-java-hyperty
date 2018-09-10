package hyperty;

import java.util.concurrent.CountDownLatch;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The CRM hyperty manages CRM Agents and forwards tickets to available Agents.
 * 
 * @author felgueiras
 *
 */
public class CRMHyperty extends AbstractHyperty {

	private static final String logMessage = "[CRM] ";
	private String agentsCollection = "agents";

	// handler URLs
	private static String agentRegistrationHandler;
	private static String ticketsHandler;
	private static String statusHandler;

	// ticket arrays
	private static JsonArray newTickets = new JsonArray();
	private static JsonArray pendingTickets = new JsonArray();

	private static JsonArray agentsConfig;

	@Override
	public void start() {

		super.start();

		agentRegistrationHandler = config().getString("url") + "/agents";
		ticketsHandler = config().getString("url") + "/tickets";
		statusHandler = config().getString("url") + "/status";

		handleAgentRequests();
		handleTicketRequests();
		handleStatusRequests();

		agentsConfig = config().getJsonArray("agents");
		if (agentsConfig != null) {
			createAgents(agentsConfig);
		}

	}

	boolean walletsExist;

	/**
	 * Create agents.
	 * 
	 * @param agentsConfig
	 */
	public void createAgents(JsonArray agentsConfig) {

		CountDownLatch agentsLatch = new CountDownLatch(1);
		walletsExist = false;

		System.out.println(logMessage + "createAgents(): " + agentsConfig.toString());

		for (Object agent : agentsConfig) {
			JsonObject agentJson = (JsonObject) agent;
			new Thread(() -> {
				JsonObject query = new JsonObject().put("code", agentJson.getString("code"));
				mongoClient.find(agentsCollection, query, res -> {
					JsonArray results = new JsonArray(res.result());
					if (results.size() == 0) {
						// set identity
						JsonObject newAgent = new JsonObject();
						newAgent.put("code", agentJson.getString("code"));
						// TODO - agent name/address?
//						newAgent.put("user", agentJson.getString("name"));
						newAgent.put("user", "");
						newAgent.put("openedTickets", 0);
						newAgent.put("status", "offline");
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
	 * Handle "agent" requests.
	 */
	private void handleAgentRequests() {

		System.out.println(logMessage + "handleAgentRequests() in " + agentRegistrationHandler);

		vertx.eventBus().<JsonObject>consumer(agentRegistrationHandler, message -> {
			mandatoryFieldsValidator(message);

			System.out.println(logMessage + "handleAgentRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "create":
				if (msg.getJsonObject("body") != null) {
					handleCreationRequest(msg, message);
				}
				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		});
	}

	/**
	 * It Checks that received body.code is in the config.agents array and if there
	 * is still no user allocated in the AgentsPool, it updates it the new user
	 * agent CGUID.
	 * 
	 * @param msg
	 * @param message
	 */
	@Override
	public void handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		JsonObject body = msg.getJsonObject("body");
		System.out.println(logMessage + "handleAgentRegistration(): " + body.toString());
		String code = body.getString("code");
		CountDownLatch latch = new CountDownLatch(1);

		if (configContainsCode(code)) {
			// check if user allocated
			new Thread(() -> {
				JsonObject query = new JsonObject().put("code", code);
				mongoClient.find(agentsCollection, query, res -> {
					JsonArray results = new JsonArray(res.result());
					JsonObject agent = results.getJsonObject(0);
					if (agent.getString("user").equals("")) {
						agent.put("user", body.getString("user"));
						JsonObject document = new JsonObject(agent.toString());
						mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", code), document,
								id -> {
									System.out.println(
											logMessage + "handleAgentRegistration(): agent updated " + document);
									message.reply(new JsonObject().put("agent", agent));
									latch.countDown();
								});
					} else {
						JsonObject response = new JsonObject().put("code", 400).put("reason",
								"agent is already allocated with user");
						message.reply(response);
						latch.countDown();

					}

				});
			}).start();
			try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			JsonObject response = new JsonObject().put("code", 400).put("reason", "there is no agent for that code");
			message.reply(response);
		}

	}

	private boolean configContainsCode(String code) {
		for (Object entry : agentsConfig) {
			JsonObject agent = (JsonObject) entry;
			if (agent.getString("code").equals(code))
				return true;
		}
		return false;
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
	 * For all live/online events received it checks if the CGUID is associated to
	 * any agent and forwards to it pending tickets. Execute funtion ticketAccepted
	 * for 200 ok accepting messages.
	 * 
	 * @param status - status message
	 */
	private void statusUpdate(JsonObject status) {
		System.out.println(logMessage + "statusUpdate(): " + status.toString());
		String cguid = status.getString("resource");
		String st = status.getString("status");
		if (st.equals("online")) {
			JsonObject query = new JsonObject().put("user", cguid);
			mongoClient.find(agentsCollection, query, res -> {
				JsonArray results = new JsonArray(res.result());
				if (results.size() > 0) {
					JsonObject agent = results.getJsonObject(0);
					// TODO - forward pending tickets to the agent
				} else {
					System.err.println(logMessage + "statusUpdate(): no user");
				}

			});
		}

	}

	private void handleTicketRequests() {

		vertx.eventBus().<JsonObject>consumer(ticketsHandler, message -> {
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

	/**
	 * Handle new tickets.
	 * 
	 * @param msg - ticket message
	 */
	private void handleNewTicket(JsonObject msg) {
		JsonObject ticket = msg.getJsonObject("body").getJsonObject("ticket");
		ticket.put("status", "pending");
		System.out.println(logMessage + "handleNewTicket(): " + ticket.toString());

		/**
		 * 1- It forwards the message to all agents and add the new ticket to newTickets
		 * array.
		 **/
		// TODO - forward message to all agents
		forwardMessage(ticket);
		newTickets.add(ticket);

		/**
		 * 2- The first agent executes ticketAccepted function.
		 **/
		// TODO - agent code
		ticketAccepted(ticket, "agent1Code");

		/**
		 * 3- In case no agent accepts the ticket, ie a timeout message is received for
		 * all invited Agents:
		 */
		// TODO - ticket not accepted
		ticketNotAccepted(ticket);
	}

	/**
	 * Message is moved from newTickets array to pendingTickets array
	 * 
	 * @param ticket
	 */
	private void ticketNotAccepted(JsonObject ticket) {
		newTickets.remove(ticket);
		pendingTickets.add(ticket);
	}

	private static String openedTickets = "openedTickets";

	/**
	 * (todo: specify this new message that should be similar to delete msg used to
	 * remove user from chat).
	 * 
	 * @param ticket
	 */
	private void ticketAccepted(JsonObject ticket, String agentCode) {
		// Ticket is allocated to the agent in the agentsPool collection
		JsonObject query = new JsonObject().put("code", agentCode);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			agent.put(openedTickets, agent.getInteger(openedTickets) + 1);
			JsonObject document = new JsonObject(agent.toString());
			mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
				System.out.println(logMessage + "ticketAccepted(): agent updated " + document);

				// ticket is removed from the pendingTickets array
				pendingTickets.remove(ticket);

				// TODO - delete message is sent to all remaining invited Agents
			});
		});

	}

	private void forwardMessage(JsonObject ticket) {
		System.out.println(logMessage + "forwardMessage(): " + ticket.toString());
	}

	JsonObject foundAgent;

}
