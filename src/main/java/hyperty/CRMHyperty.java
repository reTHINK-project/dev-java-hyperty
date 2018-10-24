package hyperty;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import hyperty.RegistryHyperty.CheckStatusTimer;
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
//			System.out.printf(logMessage + "checkNewTickets(): %d available\n", results.size());

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
					System.out.println(logMessage + "checkNewTickets(): not accepted");
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
			System.out.println(logMessage + "handleAgentValidationRequests(): " + message.body().toString());
			isAgent = false;
			String code = new JsonObject(message.body().toString()).getString("code");
			JsonObject replyMessage = new JsonObject(message.body().toString());
			if (configContainsCode(code)) {
				isAgent = true;
				// check if this code is already associated with an user
//				CountDownLatch agentsLatch = new CountDownLatch(1);
//				new Thread(() -> {
//					JsonObject query = new JsonObject().put("code", code).put("user", new JsonObject().put("$ne", ""));
//					mongoClient.find(agentsCollection, query, res -> {
//						JsonArray results = new JsonArray(res.result());
//						if (results.size() == 0) {
//							isAgent = true;
//						}
//						agentsLatch.countDown();
//					});
//				}).start();
//				try {
//					agentsLatch.await();
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
			}
			replyMessage.put("role", isAgent ? "agent" : "user");
			message.reply(replyMessage);
		});
	}

	boolean walletsExist;

	boolean areThereAgents = false;

	/**
	 * Create agents.
	 * 
	 * @param agentsConfig
	 */
	public void createAgents(JsonArray agentsConfig) {

		System.out.println(logMessage + "createAgents(): " + agentsConfig.toString());
		areThereAgents = false;

		CountDownLatch agentsLatch = new CountDownLatch(1);
		walletsExist = false;

		CountDownLatch checkAgentsLatch = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find(agentsCollection, new JsonObject(), res -> {
				JsonArray results = new JsonArray(res.result());
				if (results.size() != 0) {
					areThereAgents = true;
				}
				checkAgentsLatch.countDown();
			});
		}).start();
		try {
			checkAgentsLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println(logMessage + "areThereAgents(): " + areThereAgents);
		if (areThereAgents) {
			return;
		}

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
						newAgent.put("address", "");
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
		System.out.println(logMessage + "handleAgentRegistration(): " + msg.toString());
		String code = msg.getJsonObject("identity").getJsonObject("userProfile").getJsonObject("info")
				.getString("code");
		String guid = msg.getJsonObject("identity").getJsonObject("userProfile").getString("guid");
		CountDownLatch latch = new CountDownLatch(1);

		if (configContainsCode(code)) {
			// check if user allocated
			new Thread(() -> {
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
					mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", code), document,
							id -> {
								System.out.println(logMessage + "handleAgentRegistration(): agent updated " + document);
								message.reply(new JsonObject().put("agent", agent));
								latch.countDown();
							});
//					} else {
//						JsonObject response = new JsonObject().put("code", 400).put("reason",
//								"agent is already allocated with user");
//						message.reply(response);
//						latch.countDown();
//
//					}

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
		System.out.println(logMessage + "configContainsCode(): " + code);
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
		// TODO
		System.out.println(logMessage + "forwardPendingTickets() for agent " + agent);
		String address = agent.getString("address");
//		for (Object entry : pendingTickets) {
//			JsonObject ticket = (JsonObject) entry;
//			JsonObject msg = new JsonObject();
//			msg.put("body", ticket);
//			msg.put("type", "forward");
//			send(address, msg, reply -> {
//				JsonObject body = reply.result().body().getJsonObject("body");
//				if (body.getInteger("code") == 200) {
//					ticketAccepted(ticket, agent.getString("code"));
//				}
//			});
//		}

	}

	/**
	 * Handle tickets related operations (new, close)
	 */
	private void handleTicketRequests() {
		vertx.eventBus().<JsonObject>consumer(ticketsHandler, message -> {
			System.out.println(logMessage + "handleTicketRequests(): " + message.body().toString());
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
		JsonObject value = msg.getJsonObject("body").getJsonObject("value");
		JsonObject ticket = new JsonObject();
		ticket.put("message", msg);
		ticket.put("url", msg.getString("from").split("/subscription")[0]);
		ticket.put("created", value.getString("created"));
		ticket.put("lastModified", value.getString("lastModified"));
		ticket.put("status", ticketNew);
		ticket.put("user",
				msg.getJsonObject("body").getJsonObject("identity").getJsonObject("userProfile").getString("guid"));
		System.out.println(logMessage + "handleNewTicket(): " + ticket.toString());
		// save ticket in DB
		mongoClient.save(ticketsCollection, ticket, id -> {
			System.out.println(logMessage + "handleNewTicket(): new ticket " + ticket);
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
		System.out.println(logMessage + "handleTicketUpdate(): " + msg.toString());
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

//		JsonObject query = new JsonObject().put("user", ticket.getString("user"));
//		mongoClient.find(agentsCollection, query, res -> {
//			JsonArray results = new JsonArray(res.result());
//			JsonObject agent = results.getJsonObject(0);
//			JsonArray tickets = agent.getJsonArray("tickets");
//			for (Object entry : tickets) {
//				JsonObject ticketInDB = (JsonObject) entry;
//				if (ticketInDB.getString("id").equals(ticket.getString("id"))) {
//					System.out.println(logMessage + "handleTicketUpdate() found ticket: " + ticketInDB.toString());
//					ticketInDB.put("status", ticket.getString("status"));
//					// update DB
//					JsonObject document = new JsonObject(agent.toString());
//					mongoClient.findOneAndReplace(agentsCollection, query, document, id -> {
//						// ticket is removed from the pendingTickets array
//						pendingTickets.remove(ticket);
//					});
//				}
//			}
//		});

	}

	/**
	 * Checks if ticket belongs to user, change its status to closed and update
	 * collection.
	 * 
	 * @param msg
	 */
	private void ticketUpdateClosed(JsonObject msg) {
		System.out.println(logMessage + "ticketUpdateClosed(): " + msg);

		String msgobjectURL = msg.getString("from");
		String participantHypertyURL = msg.getJsonObject("body").getString("participant");
		String agentCode = getCodeForAgent(participantHypertyURL);
		if (agentCode.equals("")) {
			// agent code not registered
			System.out.println(logMessage + "ticketUpdateClosed(): no agent for user " + participantHypertyURL);
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
				System.out.println(logMessage + "ticketUpdateClosed() updated: " + document);
			});
		});

		JsonObject agentQuery = new JsonObject().put("code", agentCode);
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

	}

	/**
	 * Checks the ticket is new or pending and belongs to the user then it executes
	 * the ticketAccepted function. Otherwise, the message is ignored.
	 * 
	 * @param msg
	 */
	private void ticketUpdateNewParticipant(JsonObject msg) {
		System.out.println(logMessage + "ticketUpdateNewParticipant(): " + msg);
		String msgobjectURL = msg.getString("from");
		String participantHypertyURL = msg.getJsonObject("body").getString("participant");
		String agentCode = getCodeForAgent(participantHypertyURL);
		if (agentCode.equals("")) {
			// agent code not registered
			System.out.println(logMessage + "ticketUpdateNewParticipant(): no agent for user " + participantHypertyURL);
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
					ticketAccepted(ticket, agentCode);
					removeTicketInvitation(ticket, agentCode);
				}
			}
		});
	}

	String agentCode = "";

	private String getCodeForAgent(String agentHypertyURL) {

		CountDownLatch getCodeForAgent = new CountDownLatch(1);
		new Thread(() -> {
			JsonObject query = new JsonObject().put("address", agentHypertyURL);
			mongoClient.find(agentsCollection, query, res -> {
				JsonArray results = new JsonArray(res.result());
				if (results.size() > 0) {
					JsonObject agent = results.getJsonObject(0);
					agentCode = agent.getString("code");
				}
				getCodeForAgent.countDown();
			});
		}).start();
		try {
			getCodeForAgent.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return agentCode;
	}

	private static String openedTickets = "openedTickets";

	/**
	 * Ticket is added to agent and removed from pendingTickets array.
	 * 
	 * @param ticket
	 * @param agentCode
	 */
	private void ticketAccepted(JsonObject ticket, String agentCode) {
		System.out.println(logMessage + "ticketAccepted(): " + ticket.toString());
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
		System.out.println(logMessage + "forwardMessage(): ");

		JsonObject query = new JsonObject().put("user", new JsonObject().put("$ne", ""));
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			int expected = results.size();
			System.out.println(logMessage + "forwardMessage() to " + expected + " agents");
			vertx.executeBlocking(future -> {
				CountDownLatch repliesLatch = new CountDownLatch(expected);
				for (Object entry : results) {
					JsonObject agent = (JsonObject) entry;
					String address = agent.getString("address");
					String guid = agent.getString("user");
					String code = agent.getString("code");
					message.put("to", address);
					new Thread(() -> {
						send(guid, message, reply -> {
						});
//						, reply -> {
//							System.out.println(
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
					}).start();
					try {
						repliesLatch.await(10L, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				future.complete();
			}, reply -> {
				System.out.println(logMessage + "forwardMessage() finished");
			});

		});
	}

	/**
	 * Delete message is sent to all remaining invited Agents
	 * 
	 * @param ticket
	 * @param code   - code of agent who accepted ticket
	 */
	private void removeTicketInvitation(JsonObject ticket, String acceptingAgentCode) {
		JsonObject query = new JsonObject().put("code", new JsonObject().put("$ne", agentCode));
		String ticketObjectURL = ticket.getString("url");
		mongoClient.find(agentsCollection, query, res -> {
			JsonArray results = new JsonArray(res.result());
			for (Object entry : results) {
				JsonObject agent = (JsonObject) entry;
				System.out.println(logMessage + "removeTicketInvitation(): " + agent);
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
