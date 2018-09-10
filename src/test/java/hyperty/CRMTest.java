package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import protostub.SmartIotProtostub;
import util.DateUtilsHelper;
import walletManager.WalletManagerHyperty;

@ExtendWith(VertxExtension.class)
@Disabled
class CRMTest {

	private static String mongoHost = "localhost";
	private static String userID = "user-guid://test-userID";

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
	private static String db_name = "test";
	private static String agentsCollection = "agents";

	// agents
	private static String agent1Code = "agent1Code";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", crmHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", "test");
		config.put("collection", agentsCollection);
		config.put("mongoHost", "localhost");

		// agents
		JsonArray agents = new JsonArray();

		JsonObject agent1 = new JsonObject().put("name", "agent1name").put("code", agent1Code);
		agents.add(agent1);
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
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(1);

		// erase agents
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(agentsCollection, query, res -> {
			System.out.println("Agents removed from DB");
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private static String userGuid = "123";
	private static String codeRight = agent1Code;
	private static String codeWrong = "ppp";

	@Test
	@Disabled
	void testRegistrationRightCode(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", codeRight).put("user", userGuid));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			System.out.println("Received reply from agents hyperty: " + agentInfo.toString());
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void testRegistrationWrongCode(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", codeWrong).put("user", userGuid));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject response = (JsonObject) res.result().body();
			System.out.println("Received reply from agents hyperty: " + response.toString());
			int code = response.getInteger("code");
			assertEquals(code, 400);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void testTicketHandler(VertxTestContext testContext, Vertx vertx) {

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject ticket = new JsonObject();
		ticket.put("creation", new Date().getTime());
		ticket.put("user", userGuid);
		ticket.put("lastModified", new Date().getTime());
		ticket.put("message", "Preciso de ajuda na app.");
		msg.put("body", new JsonObject().put("ticket", ticket));
		vertx.eventBus().send(crmHypertyURLTickets, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
//	@Disabled
	void testStatusHandler(VertxTestContext testContext, Vertx vertx) {

		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject statusMsg = new JsonObject();
		statusMsg.put("resource", userGuid);
		statusMsg.put("status", "online");
		msg.put("body", statusMsg);
		vertx.eventBus().send(crmHypertyURLStatus, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

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