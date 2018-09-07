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
	private static String crmHypertyURLTickets = "hyperty://sharing-cities-dsm/crm/agents";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));
	private static JsonObject identityPublicWallets = new JsonObject().put("userProfile",
			new JsonObject().put("guid", "public-wallets"));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = "test";
	private static String agentsCollection = "agents";
	private static String walletAddress;
	private static String rankingInfoAddress = "data://sharing-cities-dsm/ranking";

	// agents
	private static String agent1Code = "agent1Code";
	private static String school0ID = "0";
	private static String smartIotProtostubUrl = "runtime://sharing-cities-dsm/protostub/smart-iot";

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
		vertx.deployVerticle(CRMhyperty.class.getName(), options, context.succeeding());

		// connect to Mongo
		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(5000);
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

	@Test
	void testRegistrationHandler(VertxTestContext testContext, Vertx vertx) {

		String guid = "123";
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", school0ID);
		identityWithInfo.getJsonObject("userProfile").put("info", info);
		msg.put("identity", identityWithInfo);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent1Code).put("guid", guid));
		vertx.eventBus().send(crmHypertyURL, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// get wallet
		mongoClient.find(agentsCollection, new JsonObject().put("code", agent1Code), res -> {
			JsonObject agentInfo = res.result().get(0);

			// check balance updated
			String user = agentInfo.getString("user");
			assertEquals(user, guid);
			testContext.completeNow();
		});
	}

	@Test
	void testTicketHandler(VertxTestContext testContext, Vertx vertx) {

		String guid = "123";

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", school0ID);
		identityWithInfo.getJsonObject("userProfile").put("info", info);
		msg.put("identity", identityWithInfo);
		msg.put("from", "myself");
		JsonObject ticket = new JsonObject();
		ticket.put("status", "pending");
		ticket.put("creation", new Date().getTime());
		ticket.put("lastModified", new Date().getTime());
		ticket.put("lastMomessagedified", "Preciso de ajuda na app.");
		msg.put("body", new JsonObject().put("ticket", ticket));
		vertx.eventBus().send(crmHypertyURLTickets, msg);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// get wallet
		mongoClient.find(agentsCollection, new JsonObject().put("code", agent1Code), res -> {
			JsonObject agentInfo = res.result().get(0);

			// check balance updated
			String user = agentInfo.getString("user");
			assertEquals(user, guid);
			testContext.completeNow();
		});

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