package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

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

/**
 * Test integration of CRM, Registry and Offline Subscription Manager. Test
 * Structure: <br>
 * 1 - create Registry entry (status: "online")<br>
 * 2 - register user <br>
 * 3 - update status to offline (status: "offline") <br>
 * 4 - receive status update in CRM/Offline Subscription Manager <br>
 * 5 - update status to online (status: "online") <br>
 * 
 * @author felgueiras
 *
 */
@ExtendWith(VertxExtension.class)
@Disabled
class ChatTest {

	private static String logMessage = "[TEST CRM] ";
	private static String userID = "user-guid://test-userID";

	// URLs
	private static String crmHypertyURL = "hyperty://sharing-cities-dsm/crm";
	private static String crmHypertyURLAgents = "hyperty://sharing-cities-dsm/crm/agents";
	private static String crmHypertyURLStatus = "hyperty://sharing-cities-dsm/crm/status";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static String offlineSubMgrHypertyURL = "hyperty://sharing-cities-dsm/offline-sub-mgr";
	private static String offlineSubMgrStatusHypertyURL = "hyperty://sharing-cities-dsm/offline-sub-mgr/status";
	private static String registryURL = "hyperty://sharing-cities-dsm/registry";

	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String agentsCollection = "agents";
	private static String registryCollection = "registry";
	private static String subscriptionsCollection = "pendingsubscriptions";

	// agents
	private static String userGuid = "user-guid://user";
	private static String agent1Code = "agent1Code";
	private static String agent1Address = "agent1Address";
	private static String agent2Code = "agent2Code";
	private static String agent2Address = "agent2Address";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		/*
		 * CRM
		 */
		JsonObject configCRM = new JsonObject();
		configCRM.put("url", crmHypertyURL);
		configCRM.put("identity", identity);
		// mongo
		configCRM.put("db_name", db_name);
		configCRM.put("collection", agentsCollection);
		configCRM.put("mongoHost", mongoHost);
		// agents
		JsonArray agents = new JsonArray();
		JsonObject agent1 = new JsonObject().put("address", agent1Address).put("code", agent1Code);
		JsonObject agent2 = new JsonObject().put("address", agent2Address).put("code", agent2Code);
		agents.add(agent1);
		agents.add(agent2);
		configCRM.put("agents", agents);

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(configCRM).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CRMHyperty.class.getName(), options, context.succeeding());

		/*
		 * Registry
		 */
		JsonObject configRegistry = new JsonObject();
		configRegistry.put("url", registryURL);
		configRegistry.put("identity", identity);
		configRegistry.put("collection", registryCollection);
		configRegistry.put("db_name", db_name);
		configRegistry.put("mongoHost", mongoHost);
		configRegistry.put("checkStatusTimer", 20000);
		configRegistry.put("CRMHypertyStatus", crmHypertyURLStatus);
		configRegistry.put("offlineSMStatus", offlineSubMgrStatusHypertyURL);
		DeploymentOptions optRegistry = new DeploymentOptions().setConfig(configRegistry).setWorker(true);
		vertx.deployVerticle(RegistryHyperty.class.getName(), optRegistry, context.succeeding());

		/*
		 * Offline Subscription Manager
		 */
		JsonObject configOfflineSubMgr = new JsonObject().put("url", offlineSubMgrHypertyURL);
		configOfflineSubMgr.put("identity", identity);

		// mongo
		configOfflineSubMgr.put("db_name", db_name);
		configOfflineSubMgr.put("collection", subscriptionsCollection);
		configOfflineSubMgr.put("mongoHost", mongoHost);
		configOfflineSubMgr.put("registry", registryURL);

		// deploy
		DeploymentOptions optionsOfflineSubMgr = new DeploymentOptions().setConfig(configOfflineSubMgr).setWorker(true);
		vertx.deployVerticle(OfflineSubscriptionManagerHyperty.class.getName(), optionsOfflineSubMgr,
				context.succeeding());

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
		mongoClient.removeDocuments(registryCollection, query, res -> {
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
	void testSendStatusRegistry(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + " 1 - add Registry entry");

		// TODO - replace this by message
		CountDownLatch setupLatch = new CountDownLatch(1);

		new Thread(() -> {
			// add status to registry
			JsonObject regEntry = new JsonObject();
			regEntry.put("guid", userGuid);
			regEntry.put("status", "online");
			regEntry.put("lastModified", 12345);

			JsonObject document = new JsonObject(regEntry.toString());

			mongoClient.save(registryCollection, document, id -> {
				System.out.println("New registry entry with ID:" + id);
				setupLatch.countDown();
			});
		}).start();

		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println(logMessage + " 2 - register agent");
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		msg.put("body", new JsonObject().put("code", agent1Code).put("user", userGuid));
		vertx.eventBus().send(crmHypertyURLAgents, msg, res -> {
			JsonObject agentInfo = ((JsonObject) res.result().body()).getJsonObject("agent");
			String user = agentInfo.getString("user");
			assertEquals(user, userGuid);
		});

		System.out.println(logMessage + " 3 - update status to offline");
		msg = new JsonObject();
		msg.put("type", "update");
		msg.put("from", "hyperty://hypertyurlfrom");
		msg.put("identity", identity);
		msg.put("body",
				new JsonObject().put("resource", userGuid).put("status", "offline").put("lastModified", 1536657984));

		String statusUrl = registryURL + "/status";
		vertx.eventBus().send(statusUrl, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject readMessage = new JsonObject().put("type", "read").put("from", "hyperty://hypertyurlfrom")
				.put("identity", identity).put("body", new JsonObject().put("resource", userGuid));

		vertx.eventBus().send(registryURL + "/status", readMessage, message -> {
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			int statusCode = body.getInteger("code");
			JsonObject value = body.getJsonObject("value");
			String status = value.getString("status");
			assertEquals("offline", status);
			assertEquals(200, statusCode);
		});

		System.out.println(logMessage + " 4 - update status to online");
		msg = new JsonObject();
		msg.put("type", "update");
		msg.put("from", "hyperty://hypertyurlfrom");
		msg.put("identity", identity);
		msg.put("body",
				new JsonObject().put("resource", userGuid).put("status", "online").put("lastModified", 1536657984));

		vertx.eventBus().send(statusUrl, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		vertx.eventBus().send(registryURL + "/status", readMessage, message -> {
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			int statusCode = body.getInteger("code");
			JsonObject value = body.getJsonObject("value");
			String status = value.getString("status");
			assertEquals("online", status);
			assertEquals(200, statusCode);
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