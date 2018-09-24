package hyperty;

import java.util.concurrent.CountDownLatch;

import io.vertx.core.Handler;
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
	private static String ticketsHandler;
	private static String statusHandler;

	// ticket arrays
	private static JsonArray newTickets = new JsonArray();
	private static JsonArray pendingTickets = new JsonArray();

	private static JsonArray agentsConfig;

	@Override
	public void start() {

		super.start();

		ticketsHandler = config().getString("url") + "/tickets";
		statusHandler = config().getString("url") + "/status";

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
						newAgent.put("address", agentJson.getString("address"));
						newAgent.put("user", "");
						newAgent.put("openedTickets", 0);
						newAgent.put("tickets", new JsonArray());
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

	@Override
	public Handler<Message<JsonObject>> onMessage() {

		return message -> {

			mandatoryFieldsValidator(message);

			System.out.println(logMessage + "handleAgentRequests(): " + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());

			switch (msg.getString("type")) {
			case "create":
				if (msg.getJsonObject("body") != null) {
					handleCreationRequest(msg, message);
				}
				break;
			case "delete":
				handleAgentUnregistration(msg, message);
				break;
			default:
				System.out.println("Incorrect message type: " + msg.getString("type"));
				break;
			}
		};
	}

	/**
	 * Unregister an user as agent. It checks there is an Agent for the identity,
	 * changing the status to "inactive" and moving its pending / opened tickets to
	 * other agents.
	 * 
	 * @param msg
	 * @param message
	 */
	private void handleAgentUnregistration(JsonObject msg, Message<JsonObject> message) {
		JsonObject body = msg.getJsonObject("body");
		System.out.println(logMessage + "handleAgentUnregistration(): " + body.toString());
		String code = body.getString("code");
		JsonObject query = new JsonObject().put("code", code);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			agent.put("user", "");
			agent.put("status", "offline");
			JsonObject document = new JsonObject(agent.toString());
			mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", code), document, id -> {
				message.reply(new JsonObject().put("agent", agent));

				// TODO - change opened tickets to other agents
			});

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

	private void changeStatus(String status, JsonObject agent, JsonObject query) {
		System.out.println(logMessage + " changeStatus(): " + status);
		agent.put("status", status);
		JsonObject document = new JsonObject(agent.toString());
		mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
			if (status.equals("online"))
				forwardPendingTickets(agent);
		});
	}

	/**
	 * For all online events received it checks if the CGUID is associated to any
	 * agent and forwards to it pending tickets. Execute funtion ticketAccepted for
	 * 200 ok accepting messages.
	 * 
	 * @param status - status message
	 */
	private void statusUpdate(JsonObject status) {
		System.out.println(logMessage + "statusUpdate(): " + status.toString());
		String cguid = status.getString("resource");
		String nextStatus = status.getString("status");
		JsonObject query = new JsonObject().put("user", cguid);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			if (results.size() > 0) {

				JsonObject agent = results.getJsonObject(0);
				String previousStatus = agent.getString("status");

				if (previousStatus.equals("offline") && nextStatus.equals("online")) {
					changeStatus(nextStatus, agent, query);
				} else if (previousStatus.equals("online") && nextStatus.equals("offline")) {
					changeStatus(nextStatus, agent, query);
				}
			} else {
				System.err.println(logMessage + "statusUpdate(): no user");
			}

		});

	}

	/**
	 * Forward pending tickets to agent that went live.
	 * 
	 * @param agent
	 */
	private void forwardPendingTickets(JsonObject agent) {
		System.out.println(logMessage + "forwardPendingTickets() for agent " + agent);
		String address = agent.getString("address");
		for (Object entry : pendingTickets) {
			JsonObject ticket = (JsonObject) entry;
			JsonObject msg = new JsonObject();
			msg.put("body", ticket);
			msg.put("type", "forward");
			send(address, msg, reply -> {
				JsonObject body = reply.result().body().getJsonObject("body");
				if (body.getInteger("code") == 200) {
					ticketAccepted(ticket, agent.getString("code"));
				}
			});
		}

	}

	/**
	 * Handle tickets related operations (new, close)
	 */
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
			case "update":
				handleTicketUpdate(msg);
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
		ticket.put("user", msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid"));
		System.out.println(logMessage + "handleNewTicket(): " + ticket.toString());

		// forward the message to all agents and add the new ticket to newTickets array.
		forwardMessage(msg, ticket);
		newTickets.add(ticket);

	}

	/**
	 * Handle ticket update.
	 * 
	 * @param msg - ticket message
	 */
	private void handleTicketUpdate(JsonObject msg) {
		JsonObject ticket = msg.getJsonObject("body").getJsonObject("ticket");
		System.out.println(logMessage + "handleTicketUpdate(): " + ticket.toString());

		JsonObject query = new JsonObject().put("user", ticket.getString("user"));
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			JsonArray tickets = agent.getJsonArray("tickets");
			for (Object entry : tickets) {
				JsonObject ticketInDB = (JsonObject) entry;
				if (ticketInDB.getString("id").equals(ticket.getString("id"))) {
					System.out.println(logMessage + "handleTicketUpdate() found ticket: " + ticketInDB.toString());
					ticketInDB.put("status", ticket.getString("status"));
					// update DB
					JsonObject document = new JsonObject(agent.toString());
					mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
						// ticket is removed from the pendingTickets array
						pendingTickets.remove(ticket);
					});
				}
			}
		});

	}

	/**
	 * Message is moved from newTickets array to pendingTickets array
	 * 
	 * @param ticket
	 */
	private void ticketNotAccepted(JsonObject ticket) {
		System.out.println(logMessage + "ticketNotAccepted(): " + ticket.toString());
		newTickets.remove(ticket);
		pendingTickets.add(ticket);
	}

	private static String openedTickets = "openedTickets";

	/**
	 * Ticket is added to agent and removed from pendingTickets array.
	 * 
	 * @param ticket
	 * @param agentCode
	 */
	private void ticketAccepted(JsonObject ticket, String agentCode) {
		System.out.println(logMessage + "ticketAccepted(): " + agentCode);
		JsonObject query = new JsonObject().put("code", agentCode);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			JsonArray tickets = agent.getJsonArray("tickets");
			ticket.put("status", "ongoing");
			tickets.add(ticket);
			agent.put(openedTickets, agent.getInteger(openedTickets) + 1);
			JsonObject document = new JsonObject(agent.toString());
			mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
				// ticket is removed from the pendingTickets array
				pendingTickets.remove(ticket);
			});
		});
	}

	boolean agentHasAcceptedTicket;

	/**
	 * Forward message to all the agents.
	 * 
	 * @param message
	 * @param ticket
	 */
	private void forwardMessage(JsonObject message, JsonObject ticket) {
		System.out.println(logMessage + "forwardMessage(): " + message.toString());

		agentHasAcceptedTicket = false;

		JsonObject query = new JsonObject().put("user", new JsonObject().put("$ne", ""));
		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(agentsCollection, query, res -> {
				JsonArray results = new JsonArray(res.result());
				for (Object entry : results) {
					JsonObject agent = (JsonObject) entry;
					String address = agent.getString("address");
					String code = agent.getString("code");
					send(address, message, reply -> {
						JsonObject body = reply.result().body().getJsonObject("body");
						if (body.getInteger("code") == 200) {
							if (!agentHasAcceptedTicket) {
								// first agent to accept ticket
								agentHasAcceptedTicket = true;
								ticketAccepted(ticket, code);
								removeTicketInvitation(ticket, code);
							}
						}
					});
				}
				latch.countDown();
			});
		}).start();
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (!agentHasAcceptedTicket) {
			/**
			 * In case no agent accepts the ticket, ie a timeout message is received for all
			 * invited Agents:
			 */
			ticketNotAccepted(ticket);
		}

	}

	/**
	 * Delete message is sent to all remaining invited Agents
	 * 
	 * @param ticket
	 * @param code   - code of agent who accepted ticket
	 */
	private void removeTicketInvitation(JsonObject ticket, String acceptingAgentCode) {
		System.out.println(logMessage + "removeTicketInvitation()");
		mongoClient.find(agentsCollection, new JsonObject(), res -> {
			JsonArray results = new JsonArray(res.result());
			for (Object entry : results) {
				JsonObject agent = (JsonObject) entry;
				String code = agent.getString("code");
				if (!code.equals(acceptingAgentCode)) {
					String address = agent.getString("address");
					// TODO - specify delete message format
					JsonObject msg = new JsonObject();
					msg.put("body", ticket);
					msg.put("type", "delete");
					send(address, msg, reply -> {
						JsonObject body = reply.result().body().getJsonObject("body");
						if (body.getInteger("code") == 200) {

						}
					});
				}
			}
		});

	}

	JsonObject foundAgent;

}
