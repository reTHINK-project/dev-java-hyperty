package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Date;
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
 * 7 - unregister agents
 * 
 * @author felgueiras
 *
 */
@ExtendWith(VertxExtension.class)
@Disabled
class CRMTest {

	private static String mongoHost = "localhost";
	private static String dbName = "test";
	private static String userID = "user-guid://test-userID";

	// URLs
	private static String crmHypertyURL = "hyperty://sharing-cities-dsm/crm";
	private static String crmHypertyURLAgents = "hyperty://sharing-cities-dsm/crm/agents";
	private static String crmHypertyURLTickets = "hyperty://sharing-cities-dsm/crm/tickets";
	private static String crmHypertyURLStatus = "hyperty://sharing-cities-dsm/crm/status";
	private static String userURL = "user://sharing-cities-dsm/location-identity";

	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = dbName;
	private static String agentsCollection = "agents";

	// agents
	private static String userGuid1 = "cguid1";
	private static String userGuid2 = "cguid2";
	private static String agent1Code = "agent1Code";
	private static String agent1Address = "agent1Address";
	private static String agent2Code = "agent2Code";
	private static String agent2Address = "agent2Address";

	private static String codeWrong = "ppp";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", crmHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", "test");
		config.put("collection", agentsCollection);
		config.put("mongoHost", mongoHost);

		// agents
		JsonArray agents = new JsonArray();
		JsonObject agent1 = new JsonObject().put("address", agent1Address).put("code", agent1Code);
		JsonObject agent2 = new JsonObject().put("address", agent2Address).put("code", agent2Code);
		agents.add(agent1);
		agents.add(agent2);
		config.put("agents", agents);

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

		CountDownLatch setupLatch = new CountDownLatch(1);
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(agentsCollection, query, res -> {
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	void mainTest2(VertxTestContext testContext, Vertx vertx) {
		System.out.println(logMessage + "2 - agent registration");

		vertx.eventBus().consumer(agent1Address, message -> {
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		vertx.eventBus().consumer(agent2Address, message -> {
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// register agent 2
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent2Code).put("user", userGuid2));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid2);
		});

		// register agent 1
		msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent1Code).put("user", userGuid1));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid1);

			System.out.println(logMessage + "3 - agent 1 offline -> online");

			JsonObject msg2 = new JsonObject();
			msg2.put("type", "update");
			msg2.put("identity", identity);
			msg2.put("from", "myself");
			JsonObject statusMsg = new JsonObject();
			statusMsg.put("resource", userGuid1);
			statusMsg.put("status", "online");
			msg2.put("body", statusMsg);
			vertx.eventBus().send(crmHypertyURLStatus, msg2);
			try {
				Thread.sleep(5000);
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

				mainTest3(testContext, vertx);
			});
		});
	}

	@Test
	void testRegistrationWrongCode(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", codeWrong).put("user", userGuid1));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject response = (JsonObject) res.result().body();
			System.out.println("Received reply from agents hyperty: " + response.toString());
			int code = response.getInteger("code");
			assertEquals(code, 400);
			testContext.completeNow();
		});
	}

	void mainTest3(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "4 - new ticket");
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
		msg.put("from", "myself");
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
		System.out.println(logMessage + "ticket update (ongoing -> closed)");
		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject ticket = new JsonObject();
		ticket.put("creation", new Date().getTime());
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
		
		System.out.println(logMessage + "unregister agent");
		// unregister agent 1
		msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent1Code).put("user", userGuid2));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, "");
			testContext.completeNow();
		});

	}

	private static String logMessage = "[TEST CRM] ";

	@Test
	void mainTest(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "1 - new ticket (not accepted)");

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject ticket = new JsonObject();
		ticket.put("creation", new Date().getTime());
		ticket.put("user", userGuid1);
		ticket.put("id", "0");
		ticket.put("lastModified", new Date().getTime());
		ticket.put("message", "Preciso de ajuda na app.");
		msg.put("body", new JsonObject().put("ticket", ticket));
		vertx.eventBus().send(crmHypertyURLTickets, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(2);
		verifyAgent(agent1Code, latch);
		verifyAgent(agent2Code, latch);

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		mainTest2(testContext, vertx);
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
				assertEquals("offline", status);
				assertEquals("", user);
				latch.countDown();
			});
		}).start();
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("database", db_name);
		mongoconfig.put("collection", agentsCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

}