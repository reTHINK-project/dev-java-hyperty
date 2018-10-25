package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Main test structure: <br>
 * 1 - create 2 agents <br>
 * 2 - add a ticket (won't be accepted since no agent is registered) <br>
 * 3 - register both agents <br>
 * 4 - change agent 1 status offline -> online <br>
 * 5 - pending ticket is accepted <br>
 * 6 - new ticket (ticketAccepted() and removeTicketInvitation()) <br>
 * 7 - ticket update (ongoing -> closed) <br>
 * 8 - unregister agents
 * 
 * @author felgueiras
 *
 */
@ExtendWith(VertxExtension.class)
@Disabled
class CRMTest {

	private static String logMessage = "[TEST CRM] ";

	// MongoDB
	private static String mongoHost = "localhost";
	private static String dbName = "test";
	private static String userID = "user-guid://test-userID";
	private static MongoClient mongoClient;
	private static String agentsCollection = "agents";
	private static String ticketsCollection = "tickets";

	// URLs
	private static String crmHypertyURL = "hyperty://sharing-cities-dsm/crm";
	private static String crmHypertyURLTickets = "hyperty://sharing-cities-dsm/crm/tickets";
	private static String crmHypertyURLStatus = "hyperty://sharing-cities-dsm/crm/status";
	private static String crmHypertyURLAgentValidation = "resolve-role";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static String objectURL = "comm://example.com/djshkds-shgdhg";

	// agents
	private static String userGuid0 = "cguid0";
	private static String userGuid1 = "cguid1";
	private static String userGuid2 = "cguid2";
	private static String agent0Code = "agent0Code";
	private static String agent0Address = "agent0Address";
	private static String agent1Code = "agent1Code";
	private static String agent1Address = "agent1Address";
	private static String agent2Code = "agent2Code";
	private static String agent2Address = "agent2Address";

	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID));

	private static JsonObject profileInfoAgent1 = new JsonObject().put("code", agent1Code);
	private static JsonObject identityAgent1 = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userGuid1).put("info", profileInfoAgent1));
	private static JsonObject profileInfoAgent2 = new JsonObject().put("code", agent2Code);
	private static JsonObject identityAgent2 = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userGuid2).put("info", profileInfoAgent2));
	private static JsonObject profileInfoAgent0 = new JsonObject().put("code", agent0Code);
	private static JsonObject identityAgent0 = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userGuid0).put("info", profileInfoAgent0));

	private static String codeWrong = "ppp";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", crmHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", dbName);
		config.put("collection", agentsCollection);
		config.put("mongoHost", mongoHost);
		config.put("mongoPorts", "27017");
		config.put("mongoCluster", "NO");

		// agents
		JsonArray agents = new JsonArray();
		JsonObject agent0 = new JsonObject().put("address", agent0Address).put("code", agent0Code);
		JsonObject agent1 = new JsonObject().put("address", agent1Address).put("code", agent1Code);
		JsonObject agent2 = new JsonObject().put("address", agent2Address).put("code", agent2Code);
		agents.add(agent0);
		agents.add(agent1);
//		agents.add(agent2);
		config.put("agents", agents);
		config.put("checkTicketsTimer", 2000);

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CRMHyperty.class.getName(), options, context.succeeding());

		// connect to Mongo
		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// finish setup
		checkpoint.flag();
	}

	@AfterAll
	static void cleanDB(VertxTestContext testContext, Vertx vertx) {
		System.out.println(logMessage + "cleanDB()");

		CountDownLatch setupLatch = new CountDownLatch(2);
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(agentsCollection, query, res -> {
			setupLatch.countDown();
		});
		mongoClient.removeDocuments(ticketsCollection, query, res -> {
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
//	@Disabled
	void testUpdateNewParticipant(VertxTestContext testContext, Vertx vertx) {

		System.out.println("\n" + logMessage + "testUpdateNewParticipant()");

		createTicket(vertx);
		registerAgent(vertx);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		updateNewParticipant(vertx);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// assertions
		CountDownLatch latch = new CountDownLatch(2);
		new Thread(() -> {
			mongoClient.find(agentsCollection, new JsonObject().put("code", agent0Code), res -> {
				JsonObject agentInfo = res.result().get(0);
				int openedTickets = agentInfo.getInteger("openedTickets");
				String status = agentInfo.getString("status");
				String user = agentInfo.getString("user");
				JsonArray tickets = agentInfo.getJsonArray("tickets");
				assertEquals(1, openedTickets);
				assertEquals(1, tickets.size());
				assertEquals("online", status);
				assertEquals(userGuid0, user);
				latch.countDown();
			});
		}).start();

		new Thread(() -> {
			mongoClient.find(ticketsCollection, new JsonObject(), res -> {
				JsonObject ticket = res.result().get(0);
				String status = ticket.getString("status");
//				String user = ticket.getString("user");
				assertEquals(CRMHyperty.ticketOngoing, status);
//				assertEquals(userGuid0, user);
				latch.countDown();
			});
		}).start();

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		testContext.completeNow();
	}

	private void updateNewParticipant(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		msg.put("identity", identityAgent1);
		msg.put("from", objectURL);
		JsonObject body = new JsonObject();
		body.put("status", CRMHyperty.newParticipant);
		body.put("participant", userGuid0);
		msg.put("body", body);
		vertx.eventBus().send(crmHypertyURLTickets, msg, res -> {
		});
	}

	@Test
	@Disabled
	void updateClosed(VertxTestContext testContext, Vertx vertx) {

		System.out.println("\n" + logMessage + "testUpdateClosed()");

		createTicket(vertx);
		registerAgent(vertx);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		updateNewParticipant(vertx);

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		msg.put("identity", identityAgent1);
		msg.put("from", objectURL);
		JsonObject body = new JsonObject();
		body.put("status", CRMHyperty.closed);
		body.put("participant", userGuid0);
		msg.put("body", body);
		vertx.eventBus().send(crmHypertyURLTickets, msg, res -> {
		});

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// assertions
		CountDownLatch latch = new CountDownLatch(2);
		new Thread(() -> {
			mongoClient.find(agentsCollection, new JsonObject().put("code", agent0Code), res -> {
				JsonObject agentInfo = res.result().get(0);
				int openedTickets = agentInfo.getInteger("openedTickets");
				String status = agentInfo.getString("status");
				String user = agentInfo.getString("user");
				JsonArray tickets = agentInfo.getJsonArray("tickets");
				assertEquals(0, openedTickets);
				assertEquals(0, tickets.size());
				assertEquals("online", status);
				assertEquals(userGuid0, user);
				latch.countDown();
			});
		}).start();

		new Thread(() -> {
			mongoClient.find(ticketsCollection, new JsonObject(), res -> {
				JsonObject ticket = res.result().get(0);
				String status = ticket.getString("status");
				assertEquals(CRMHyperty.ticketClosed, status);
				latch.countDown();
			});
		}).start();

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		cleanDB(testContext, vertx);
		testContext.completeNow();
	}

	private void registerAgent(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identityAgent0);
		msg.put("from", agent0Address);
		msg.put("body", new JsonObject().put("code", agent0Code).put("user", userGuid0));
		vertx.eventBus().send(crmHypertyURL, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid0);
		});

	}

	private void createTicket(Vertx vertx) {
		
		Date date = new Date();
		TimeZone tz = TimeZone.getTimeZone("UTC");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		sdf.setTimeZone(tz);
		String timeISO = sdf.format(date);
		System.out.println(timeISO);
		
		// new ticket
		JsonObject msg = new JsonObject();
		msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", objectURL + "/subscription");
		JsonObject body = new JsonObject();
		JsonObject value = new JsonObject();
		value.put("name", "topic-question");
		value.put("created", timeISO);
		value.put("lastModified", timeISO);
		body.put("value", value);
		body.put("identity", identity);
		msg.put("body", body);

		vertx.eventBus().send(crmHypertyURLTickets, msg);
	}

	@Test
	@Disabled
	void mainTest(VertxTestContext testContext, Vertx vertx) {

		// add agent 0 handler (accept)
		vertx.eventBus().consumer(userGuid0, message -> {
			System.out.println(logMessage + "agent 0 accepting");
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});
//		
		// add agent 0 handler (do not reply)
//		vertx.eventBus().consumer(userGuid0, message -> {
//			System.out.println(logMessage + "agent 0 not replying");
////			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
////			message.reply(reply);
//		});

		// register agent 0
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identityAgent0);
		msg.put("from", agent0Address);
		msg.put("body", new JsonObject().put("code", agent0Code).put("user", userGuid0));
		vertx.eventBus().send(crmHypertyURL, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid0);
		});

		System.out.println("\n" + logMessage + "1 - new ticket (not accepted)");
		msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", objectURL + "/subscription");
		JsonObject body = new JsonObject();
		JsonObject value = new JsonObject();
		value.put("name", "topic-question");
		value.put("created", new Date().getTime() + "");
		value.put("lastModified", new Date().getTime() + "");
		body.put("value", value);
		body.put("identity", identity);
		msg.put("body", body);

		vertx.eventBus().send(crmHypertyURLTickets, msg);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

//		CountDownLatch latch = new CountDownLatch(2);
//		verifyAgent(agent1Code, latch);
//		verifyAgent(agent2Code, latch);
//
//		try {
//			latch.await();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

//		testContext.completeNow();
		mainTest2(testContext, vertx);
	}

	void mainTest2(VertxTestContext testContext, Vertx vertx) {
		System.out.println("\n" + logMessage + "2 - agent registration");

		vertx.eventBus().consumer(agent1Address, message -> {
			System.out.println(logMessage + "agent 1 accepting");
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		vertx.eventBus().consumer(agent2Address, message -> {
			System.out.println(logMessage + "agent 2 accepting");
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		CountDownLatch latch = new CountDownLatch(2);

		new Thread(() -> {

			// register agent 2
			JsonObject msg = new JsonObject();
			msg.put("type", "create");
			msg.put("identity", identityAgent2);
			msg.put("from", agent2Address);
			msg.put("body", new JsonObject().put("code", agent2Code).put("user", userGuid2));
			vertx.eventBus().send(crmHypertyURL, msg, res -> {
				JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
				String user = agentInfo.getString("user");
				assertEquals(user, userGuid2);
				latch.countDown();
			});

			// register agent 1
			msg = new JsonObject();
			msg.put("type", "create");
			msg.put("identity", identityAgent1);
			msg.put("from", agent1Address);
			msg.put("body", new JsonObject().put("code", agent1Code).put("user", userGuid1));
			vertx.eventBus().send(crmHypertyURL, msg, res -> {
				JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
				String user = agentInfo.getString("user");
				assertEquals(user, userGuid1);
				latch.countDown();
			});

		}).start();

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

//		testContext.completeNow();

		System.out.println("\n" + logMessage + "3 - agent 1 offline -> online");
		JsonObject msg2 = new JsonObject();
		msg2.put("type", "update");
		msg2.put("identity", identityAgent1);
		msg2.put("from", "myself");
		JsonObject statusMsg = new JsonObject();
		statusMsg.put("resource", userGuid1);
		statusMsg.put("status", "online");
		msg2.put("body", statusMsg);
		vertx.eventBus().send(crmHypertyURLStatus, msg2);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		mongoClient.find(agentsCollection, new JsonObject().put("code", agent1Code), res2 -> {
			JsonObject agent = res2.result().get(0);
			assertEquals(1, (int) agent.getInteger("openedTickets"));
			JsonArray tickets = agent.getJsonArray("tickets");
			assertEquals(1, tickets.size());
			assertEquals(userGuid1, agent.getString("user"));
			assertEquals("online", agent.getString("status"));

			testContext.completeNow();
//			mainTest3(testContext, vertx);
		});
	}

	@Test
	@Disabled
	void registrationWrongCode(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", codeWrong).put("user", userGuid1));
		vertx.eventBus().send(crmHypertyURL, msg, res -> {
			JsonObject response = (JsonObject) res.result().body();
			System.out.println("Received reply from agents hyperty: " + response.toString());
			int code = response.getInteger("code");
			assertEquals(code, 400);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void agentCodeValid(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "forward");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("code", agent1Code);
		vertx.eventBus().send(crmHypertyURLAgentValidation, msg, res -> {
			JsonObject response = (JsonObject) res.result().body();
			System.out.println("Received reply from agent validation: " + response.toString());
			String role = response.getString("role");
			assertEquals(role, "agent");
			testContext.completeNow();
		});

//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//
//		// change in Mongo
//		JsonObject query = new JsonObject().put("code", agent1Code);
//		mongoClient.find(agentsCollection, query, res -> {
//			JsonArray results = new JsonArray(res.result());
//			JsonObject agent = results.getJsonObject(0);
//			agent.put("user", "123");
//			agent.put("status", "online");
//			JsonObject document = new JsonObject(agent.toString());
//			mongoClient.findOneAndReplace(agentsCollection, new JsonObject().put("code", agent1Code), document, id -> {
//			});
//
//		});
//
//		try {
//			Thread.sleep(2000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		// check code which was taken
//		msg = new JsonObject();
//		msg.put("type", "forward");
//		msg.put("identity", identity);
//		msg.put("from", "myself");
//		msg.put("code", agent1Code);
//		vertx.eventBus().send(crmHypertyURLAgentValidation, msg, res -> {
//			JsonObject response = (JsonObject) res.result().body();
//			System.out.println("Received reply from agent validation: " + response.toString());
//			boolean valid = response.getBoolean("valid");
//			assertEquals(valid, false);
//			testContext.completeNow();
//		});

	}

	@Test
	@Disabled
	void addTicket(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", "myself");
		JsonObject body = new JsonObject();
		JsonObject value = new JsonObject();
		value.put("name", "topic");
		value.put("created", new Date().getTime() + "");
		value.put("lastModified", new Date().getTime() + "");
		body.put("value", value);
		body.put("identity", identity);
		msg.put("body", body);

		vertx.eventBus().send(crmHypertyURLTickets, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		testContext.completeNow();
	}

	@Test
	@Disabled
	void agentCodeInvalid(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "forward");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("code", "wrongCode");
		vertx.eventBus().send(crmHypertyURLAgentValidation, msg, res -> {
			JsonObject response = (JsonObject) res.result().body();
			System.out.println("Received reply from agent validation: " + response.toString());
			boolean valid = response.getBoolean("valid");
			assertEquals(valid, false);
			testContext.completeNow();
		});
	}

	void mainTest3(VertxTestContext testContext, Vertx vertx) {

		System.out.println("\n" + logMessage + "4 - new ticket");
		MessageConsumer<Object> consumer1 = vertx.eventBus().consumer(agent1Address, message -> {
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		MessageConsumer<Object> consumer2 = vertx.eventBus().consumer(agent2Address, message -> {
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// new ticket
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", objectURL + "/subscription");
		JsonObject ticket = new JsonObject();
		ticket.put("creation", new Date().getTime());
		ticket.put("user", userGuid1);
		ticket.put("id", "1");
		ticket.put("lastModified", new Date().getTime());
		ticket.put("message", "Preciso de ajuda na app.");
		msg.put("body", new JsonObject().put("ticket", ticket));
		vertx.eventBus().send(crmHypertyURLTickets, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		mongoClient.find(agentsCollection, new JsonObject().put("code", agent1Code), res -> {
			JsonObject agentInfo = res.result().get(0);
			int openedTickets = agentInfo.getInteger("openedTickets");
			JsonArray tickets = agentInfo.getJsonArray("tickets");
			assertEquals(2, openedTickets);
			assertEquals(2, tickets.size());
			consumer1.unregister();
			consumer2.unregister();

			mainTest4(testContext, vertx);
		});

	}

	private void mainTest4(VertxTestContext testContext, Vertx vertx) {
		System.out.println("\n" + logMessage + "5 - ticket update (ongoing -> closed)");
		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject ticket = new JsonObject();
		ticket.put("user", userGuid1);
		ticket.put("status", "closed");
		ticket.put("id", "0");
		msg.put("body", new JsonObject().put("ticket", ticket));
		vertx.eventBus().send(crmHypertyURLTickets, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("\n" + logMessage + "6 - unregister agent");
		// unregister agent 1
		msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent1Code).put("user", userGuid2));
		vertx.eventBus().send(crmHypertyURL, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, "");
			testContext.completeNow();
		});

	}

	static void verifyAgent(String agentCode, CountDownLatch latch) {
		new Thread(() -> {
			mongoClient.find(agentsCollection, new JsonObject().put("code", agentCode), res -> {
				JsonObject agentInfo = res.result().get(0);
				int openedTickets = agentInfo.getInteger("openedTickets");
				String status = agentInfo.getString("status");
				String user = agentInfo.getString("user");
				JsonArray tickets = agentInfo.getJsonArray("tickets");
				assertEquals(0, openedTickets);
				assertEquals(0, tickets.size());
				assertEquals("online", status);
				assertEquals("", user);
				latch.countDown();
			});
		}).start();
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", dbName);
		mongoconfig.put("database", dbName);
		mongoconfig.put("collection", agentsCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

}