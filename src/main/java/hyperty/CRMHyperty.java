package hyperty;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
	private static final String agentsCollection = "agents";
	private static final String ticketsCollection = "tickets";

	// handler URLs
	private static String ticketsHandler;
	private static String statusHandler;
	private static String agentValidationHandler;

	// ticket arrays
	private static JsonArray agentsConfig;
	int checkTicketsTimer;

	// update types
	public static final String newParticipant = "new-participant";
	public static final String closed = "closed";

	// ticket states
	public static final String ticketNew = "new";
	public static final String ticketPending = "pending";
	public static final String ticketOngoing = "ongoing";
	public static final String ticketClosed = "closed";

	@Override
	public void start() {

		super.start();

		ticketsHandler = config().getString("url") + "/tickets";
		statusHandler = config().getString("url") + "/status";
		agentValidationHandler = "resolve-role";

		handleTicketRequests();
		handleStatusRequests();
		handleAgentValidationRequests();

		agentsConfig = config().getJsonArray("agents");
		if (agentsConfig != null) {
			createAgents(agentsConfig);
		}

		Timer timer = new Timer();
		checkTicketsTimer = config().getInteger("checkTicketsTimer");
		timer.schedule(new CheckTicketsTimer(), 0, checkTicketsTimer);
	}

	class CheckTicketsTimer extends TimerTask {
		public void run() {
			checkNewTickets();
		}
	}

	/**
	 * Process newTickets array running every X seconds (eg 300 secs) to move new
	 * Tickets to pendingTickets array, in case no agent accepts new tickets ie if
	 * timeNow - createdData > x.
	 */
	private void checkNewTickets() {

		JsonObject newTicketsQuery = new JsonObject().put("status", ticketNew);
		mongoClient.find(ticketsCollection, newTicketsQuery, res -> {
			JsonArray results = new JsonArray(res.result());

			for (Object entry : results) {
				JsonObject ticket = (JsonObject) entry;
				long currentTime = new Date().getTime();
				DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
				long creationTime = 0;
				try {
					Date result1 = df1.parse(ticket.getString("created"));
					creationTime = result1.getTime();
				} catch (ParseException e) {
					e.printStackTrace();
				}
				if (currentTime - creationTime > checkTicketsTimer) {
					logger.debug(logMessage + "checkNewTickets(): not accepted");
					JsonObject ticketQuery = new JsonObject().put("url",
							ticket.getString("url").split("/subscription")[0]);
					ticket.put("status", ticketPending);
					mongoClient.findOneAndReplace(ticketsCollection, ticketQuery, ticket, id -> {
						System.out.printf(logMessage + "checkNewTickets(): ticket pending %s\n", ticket.toString());
					});
				}
			}
		});
	}

	boolean isAgent;

	/**
	 * Validate agent codes
	 */
	private void handleAgentValidationRequests() {

		vertx.eventBus().<JsonObject>consumer(agentValidationHandler, message -> {
			mandatoryFieldsValidator(message);
			logger.debug(logMessage + "handleAgentValidationRequests(): " + message.body().toString());
			isAgent = false;
			String code = new JsonObject(message.body().toString()).getString("code");
			JsonObject replyMessage = new JsonObject(message.body().toString());
			if (configContainsCode(code)) {
				isAgent = true;
			}
			replyMessage.put("role", isAgent ? "agent" : "user");
			message.reply(replyMessage);
		});
	}

	/**
	 * Create agents.
	 *
	 * @param agentsConfig
	 */
	public Future<Void> createAgents(JsonArray agentsConfig) {

		logger.debug(logMessage + "createAgents(): " + agentsConfig.toString());

		Future<Void> createAgentsFuture = Future.future();

		Future<Boolean> checkAgentsLatch = Future.future();

		mongoClient.find(agentsCollection, new JsonObject(), res -> {
			JsonArray results = new JsonArray(res.result());
			checkAgentsLatch.complete(results.size() != 0);
		});

		checkAgentsLatch.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				logger.debug(logMessage + "areThereAgents(): " + checkAgentsLatch.result());
				if (asyncResult.result()) {
					return;
				}

				List<Future> futures = new ArrayList<>();

				for (Object agent : agentsConfig) {
					Future<Void> createSingleAgent = createSingleAgent(agent);
					futures.add(createSingleAgent);
				}

				CompositeFuture.all(futures).setHandler(done -> {
					createAgentsFuture.complete();
				});
			} else {
				// oh ! we have a problem...
			}
		});

		return createAgentsFuture;

	}

	private Future<Void> createSingleAgent(Object agent) {
		Future<Void> createAgentFuture = Future.future();
		JsonObject agentJson = (JsonObject) agent;
		JsonObject query = new JsonObject().put("code", agentJson.getString("code"));
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			if (results.size() == 0) {
				// set identity
				JsonObject newAgent = new JsonObject();
				newAgent.put("code", agentJson.getString("code"));
				newAgent.put("address", "");
				newAgent.put("user", "");
				newAgent.put("openedTickets", 0);
				newAgent.put("tickets", new JsonArray());
				newAgent.put("status", "offline");
				JsonObject document = new JsonObject(newAgent.toString());
				mongoClient.save(agentsCollection, document, id -> {
					logger.debug(logMessage + "createAgents(): new agent " + document);
				});
			}

			createAgentFuture.complete();
		});

		return createAgentFuture;
	}

	@Override
	public Handler<Message<JsonObject>> onMessage() {

		return message -> {

			mandatoryFieldsValidator(message);

			logger.debug(logMessage + "handleAgentRequests(): " + message.body().toString());

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
				logger.debug("Incorrect message type: " + msg.getString("type"));
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
		logger.debug(logMessage + "handleAgentUnregistration(): " + body.toString());
		String code = body.getString("code");
		JsonObject query = new JsonObject().put("code", code);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			agent.put("user", "");
			agent.put("status", "offline");
			JsonObject document = new JsonObject(agent.toString());
			mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", code), document, id -> {
				message.reply(new JsonObject().put("body", new JsonObject().put("agent", agent).put("code", 200)));

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
	public Future<Void> handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		logger.debug(logMessage + "handleAgentRegistration(): " + msg.toString());
		String code = msg.getJsonObject("identity").getJsonObject("userProfile").getJsonObject("info")
				.getString("code");
		String guid = msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid");
		Future<Void> resultFuture = Future.future();

		if (configContainsCode(code)) {
			// check if user allocated
			JsonObject query = new JsonObject().put("code", code);
			mongoClient.find(agentsCollection, query, res -> {
				JsonArray results = new JsonArray(res.result());
				JsonObject agent = results.getJsonObject(0);
//					if (agent.getString("user").equals("")) {
				// update agent's associated user guid and address
				agent.put("user", guid);
				agent.put("status", "online");
				agent.put("address", msg.getString("from"));
				JsonObject document = new JsonObject(agent.toString());
				mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", code), document, id -> {
					logger.debug(logMessage + "handleAgentRegistration(): agent updated " + document);
					message.reply(new JsonObject().put("body", new JsonObject().put("agent", agent).put("code", 200)));
					resultFuture.complete();
				});
//					} else {
//						JsonObject response = new JsonObject().put("code", 400).put("reason",
//								"agent is already allocated with user");
//						message.reply(response);
//						latch.countDown();
//
//					}

			});
		} else {
			JsonObject response = new JsonObject().put("code", 400).put("reason", "there is no agent for that code");
			JsonObject responseMsg = new JsonObject().put("body", response);
			message.reply(responseMsg);
			resultFuture.complete();
		}

		return resultFuture;

	}

	private boolean configContainsCode(String code) {
		logger.debug(logMessage + "configContainsCode(): " + code);
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

	private void changeStatus(String status, JsonObject agent, JsonObject query) {
		logger.debug(logMessage + " changeStatus(): " + status);
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
		logger.debug(logMessage + "statusUpdate(): " + status.toString());
		String cguid = status.getString("resource");
		String nextStatus = status.getString("status");
		JsonObject query = new JsonObject().put("user", cguid);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			if (results.size() > 0) {

				JsonObject agent = results.getJsonObject(0);
				String previousStatus = agent.getString("status");

				// if (previousStatus.equals("offline") && nextStatus.equals("online")) {
				if (nextStatus.equals("online")) {
					changeStatus(nextStatus, agent, query);
				} else if (previousStatus.equals("online") && nextStatus.equals("offline")) {
					changeStatus(nextStatus, agent, query);
				}
			} else {
				logger.debug(logMessage + "statusUpdate(): no user");
			}

		});

	}

	/**
	 * Forward pending tickets to agent that went live.
	 *
	 * @param agent
	 */
	private void forwardPendingTickets(JsonObject agent) {
		// TODO
		logger.debug(logMessage + "forwardPendingTickets() for agent " + agent);
		String guid = agent.getString("user");

		JsonObject query = new JsonObject().put("status", "pending");
		mongoClient.find(ticketsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			if (results.size() == 0)
				return;

			for (int i = 0; i < res.result().size(); i++) {
				JsonObject ticket = res.result().get(i);
				JsonObject message = ticket.getJsonObject("message");
				logger.debug(logMessage + "forward tickets " + i);
				send(guid, message, reply -> {
				});
			}
		});
	}

	/**
	 * Handle tickets related operations (new, close)
	 */
	private void handleTicketRequests() {
		vertx.eventBus().<JsonObject>consumer(ticketsHandler, message -> {
			logger.debug(logMessage + "handleTicketRequests(): " + message.body().toString());
			mandatoryFieldsValidator(message);

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
				logger.debug("Incorrect message type: " + msg.getString("type"));
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
		JsonObject value = msg.getJsonObject("body").getJsonObject("value");
		JsonObject ticket = new JsonObject();
		String url = msg.getString("from").split("/subscription")[0];
		ticket.put("message", msg);
		ticket.put("url", url);
		ticket.put("created", value.getString("created"));
		ticket.put("lastModified", value.getString("lastModified"));
		ticket.put("status", ticketNew);
		ticket.put("user",
				msg.getJsonObject("body").getJsonObject("identity").getJsonObject("userProfile").getString("guid"));
		logger.debug(logMessage + "handleNewTicket(): " + ticket.toString());
		// save ticket in DB
		mongoClient.find(ticketsCollection, new JsonObject().put("url", url), resultHandler -> {
			if (resultHandler.result().size() == 0) {
				mongoClient.save(ticketsCollection, ticket, id -> {
					logger.debug(logMessage + "handleNewTicket(): new ticket " + ticket);
				});
			}
		});

		// forward the message to all agents and add the new ticket to newTickets array.
		forwardMessage(msg, ticket);
	}

	/**
	 * Handle ticket update.
	 *
	 * @param msg - ticket message
	 */
	private void handleTicketUpdate(JsonObject msg) {
		logger.debug(logMessage + "handleTicketUpdate(): " + msg.toString());
		String status = msg.getJsonObject("body").getString("status");
		switch (status) {
		case newParticipant:
			ticketUpdateNewParticipant(msg);
			break;
		case closed:
			ticketUpdateClosed(msg);
			break;

		default:
			break;
		}
	}

	/**
	 * Checks if ticket belongs to user, change its status to closed and update
	 * collection.
	 *
	 * @param msg
	 */
	private void ticketUpdateClosed(JsonObject msg) {
		logger.debug(logMessage + "ticketUpdateClosed(): " + msg);

		String msgobjectURL = msg.getString("from");
		String participantHypertyURL = msg.getJsonObject("body").getString("participant");
		Future<String> agentCode = getCodeForAgent(participantHypertyURL);
		agentCode.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				if (asyncResult.result().equals("")) {
					// agent code not registered
					logger.debug(logMessage + "ticketUpdateClosed(): no agent for user " + participantHypertyURL);
					return;
				}
				JsonObject query = new JsonObject().put("url", msgobjectURL);
				mongoClient.find(ticketsCollection, query, res -> {
					JsonArray results = new JsonArray(res.result());
					if (results.size() == 0)
						return;
					JsonObject ticket = results.getJsonObject(0);
					ticket.put("status", ticketClosed);
					JsonObject document = new JsonObject(ticket.toString());
					mongoClient.findOneAndReplace(ticketsCollection, query, document, id -> {
						logger.debug(logMessage + "ticketUpdateClosed() updated: " + document);
					});
				});

				JsonObject agentQuery = new JsonObject().put("code", asyncResult.result());
				// update agent
				mongoClient.find(agentsCollection, agentQuery, res -> {
					JsonArray results = new JsonArray(res.result());
					JsonObject agent = results.getJsonObject(0);
					JsonArray tickets = agent.getJsonArray("tickets");
					tickets.remove(tickets.size() - 1);
					agent.put(openedTickets, agent.getInteger(openedTickets) - 1);
					JsonObject document = new JsonObject(agent.toString());
					mongoClient.findOneAndReplace(agentsCollection, agentQuery, document, id -> {
					});
				});
			} else {
				// oh ! we have a problem...
			}
		});

	}

	/**
	 * Checks the ticket is new or pending and belongs to the user then it executes
	 * the ticketAccepted function. Otherwise, the message is ignored.
	 *
	 * @param msg
	 */
	private void ticketUpdateNewParticipant(JsonObject msg) {
		logger.debug(logMessage + "ticketUpdateNewParticipant(): " + msg);
		String msgobjectURL = msg.getString("from");
		String participantHypertyURL = msg.getJsonObject("body").getString("participant");
		Future<String> agentCode = getCodeForAgent(participantHypertyURL);
		agentCode.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				if (asyncResult.result().equals("")) {
					// agent code not registered
					logger.debug(
							logMessage + "ticketUpdateNewParticipant(): no agent for user " + participantHypertyURL);
					return;
				}

				JsonObject query = new JsonObject().put("url", msgobjectURL);
				mongoClient.find(ticketsCollection, query, res -> {
					JsonArray results = new JsonArray(res.result());
					JsonObject ticket = results.getJsonObject(0);
					String status = ticket.getString("status");
					String[] values = { ticketNew, ticketPending };
					if (Arrays.stream(values).anyMatch(status::equals)) {
						String objectURL = ticket.getJsonObject("message").getString("from").split("/subscription")[0];
						if (objectURL.equals(msgobjectURL)) {
							ticketAccepted(ticket, asyncResult.result());
							removeTicketInvitation(ticket, asyncResult.result());
						}
					}
				});
			} else {
				// oh ! we have a problem...
			}
		});

	}

	private Future<String> getCodeForAgent(String agentHypertyURL) {

		Future<String> getCodeForAgent = Future.future();
		JsonObject query = new JsonObject().put("address", agentHypertyURL);
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			if (results.size() > 0) {
				JsonObject agent = results.getJsonObject(0);
				getCodeForAgent.complete(agent.getString("code"));
			}
		});

		return getCodeForAgent;
	}

	private static String openedTickets = "openedTickets";

	/**
	 * Ticket is added to agent and removed from pendingTickets array.
	 *
	 * @param ticket
	 * @param agentCode
	 */
	private void ticketAccepted(JsonObject ticket, String agentCode) {
		logger.debug(logMessage + "ticketAccepted(): " + ticket.toString());
		JsonObject query = new JsonObject().put("code", agentCode);
		// update agent
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject agent = results.getJsonObject(0);
			JsonArray tickets = agent.getJsonArray("tickets");
			ticket.put("status", "ongoing");
			tickets.add(ticket.getString("url"));
			agent.put(openedTickets, agent.getInteger(openedTickets) + 1);
			JsonObject document = new JsonObject(agent.toString());
			mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
			});
		});
		// update ticket
		JsonObject ticketQuery = new JsonObject().put("url", ticket.getString("url").split("/subscription")[0]);
		mongoClient.find(ticketsCollection, ticketQuery, res -> {
			JsonArray results = new JsonArray(res.result());
			JsonObject ticketDB = results.getJsonObject(0);
			ticketDB.put("status", ticketOngoing);
			JsonObject document = new JsonObject(ticketDB.toString());
			mongoClient.findOneAndReplace(ticketsCollection, ticketQuery, document, id -> {

			});
		});
	}

	/**
	 * Forward message to all the agents.
	 *
	 * @param message
	 * @param ticket
	 */
	private void forwardMessage(JsonObject message, JsonObject ticket) {
		logger.debug(logMessage + "forwardMessage(): ");

		JsonObject query = new JsonObject().put("user", new JsonObject().put("$ne", ""));
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			int expected = results.size();
			logger.debug(logMessage + "forwardMessage() to " + expected + " agents");

			for (Object entry : results) {
				JsonObject agent = (JsonObject) entry;
				String address = agent.getString("address");
				String guid = agent.getString("user");
				String code = agent.getString("code");
				message.put("to", address);
				send(guid, message, reply -> {
				});

//						, reply -> {
//							//logger.debug(
//									logMessage + "forwardMessage() to address <" + guid + "> reply: " + reply.result());
//							if (reply.result() != null) {
//								JsonObject body = reply.result().body().getJsonObject("body");
//								if (body.getInteger("code") == 200) {
//									if (!agentHasAcceptedTicket) {
//										// first agent to accept ticket
//										agentHasAcceptedTicket = true;
//										ticketAccepted(ticket, code);
//										removeTicketInvitation(ticket, code);
//									}
//								}
//							}
//							repliesLatch.countDown();
//						});
			}

		});
	}

	/**
	 * Delete message is sent to all remaining invited Agents
	 *
	 * @param ticket
	 * @param code   - code of agent who accepted ticket
	 */
	private void removeTicketInvitation(JsonObject ticket, String acceptingAgentCode) {
		JsonObject query = new JsonObject().put("code", new JsonObject().put("$ne", acceptingAgentCode));
		String ticketObjectURL = ticket.getString("url");
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			for (Object entry : results) {
				JsonObject agent = (JsonObject) entry;
				logger.debug(logMessage + "removeTicketInvitation(): " + agent);
				String guid = agent.getString("user");
				JsonObject msg = new JsonObject();
				msg.put("body", new JsonObject().put("resource", ticketObjectURL));
				msg.put("type", "delete");
				msg.put("from", url);
				msg.put("to", guid);
				send(guid, msg, reply -> {
				});
			}
		});

	}

	JsonObject foundAgent;

}
