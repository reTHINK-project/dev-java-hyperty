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
import io.vertx.core.eventbus.MessageConsumer;
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
class OfflineSubscriptionManagerTest {

	private static String logMessage = "[TEST OfflineSubscriptionManager] ";

	private static String mongoHost = "localhost";
	private static String dbName = "test";
	private static String userID = "user-guid://test-userID";

	// URLs
	private static String offlineSubMgrHypertyURL = "hyperty://sharing-cities-dsm/offline-sub-mgr";
	private static String registryURL = "hyperty://sharing-cities-dsm/registry";
	private static String offlineSubMgrURLStatus = "hyperty://sharing-cities-dsm/offline-sub-mgr/status";
	private static String userURL = "user://sharing-cities-dsm/location-identity";

	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = dbName;
	private static String collection = "pendingsubscriptions";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", offlineSubMgrHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", "test");
		config.put("collection", collection);
		config.put("mongoHost", mongoHost);
		config.put("registry", registryURL);

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(OfflineSubscriptionManagerHyperty.class.getName(), options, context.succeeding());

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
		mongoClient.removeDocuments(collection, query, res -> {
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static String to = "observer/subscription";
	
	@Test
//	@Disabled
	void testStatusHandler(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "testStatusHandler()");

		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		JsonObject body = new JsonObject();
		body.put("resource", userID);
		body.put("status", "online");
		msg.put("body", body);
		vertx.eventBus().send(offlineSubMgrURLStatus, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(collection, new JsonObject(), res -> {
				assertEquals(0, res.result().size());
				latch.countDown();
			});
		}).start();

		try {
			latch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Test
	@Disabled
	void testSubscriptionOnline(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "testSubscriptionOnline()");

		addMessageConsumers(vertx, "online");

		JsonObject msg = new JsonObject();
		msg.put("type", "subscribe");
		msg.put("identity", identity);
		msg.put("id", "1");
		msg.put("from", "hyperty-runtime://<observer-sp-domain>/<hyperty-observer-runtime-instance-identifier>/sm");
		msg.put("to", to);
		JsonObject body = new JsonObject();
		body.put("source", "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>");
		msg.put("body", body);
		vertx.eventBus().send(offlineSubMgrHypertyURL, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(collection, new JsonObject(), res -> {
				assertEquals(0, res.result().size());
				latch.countDown();
			});
		}).start();

		try {
			latch.await();
			testContext.completeNow();
			unregisterMessageConsumers();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Test
//	@Disabled
	void testSubscriptionOffline(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "testSubscriptionOffline()");

		addMessageConsumers(vertx, "offline");

		JsonObject msg = new JsonObject();
		msg.put("type", "subscribe");
		msg.put("identity", identity);
		msg.put("id", "1");
		msg.put("from", "hyperty-runtime://<observer-sp-domain>/<hyperty-observer-runtime-instance-identifier>/sm");
		msg.put("to", to);
		JsonObject body = new JsonObject();
		body.put("source", "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>");
		msg.put("body", body);
		vertx.eventBus().send(offlineSubMgrHypertyURL, msg);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(collection, new JsonObject(), res -> {
				assertEquals(1, res.result().size());
				latch.countDown();
			});
		}).start();

		try {
			latch.await();
			testContext.completeNow();
			unregisterMessageConsumers();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("database", db_name);
		mongoconfig.put("collection", collection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	MessageConsumer<Object> consumer1;
	MessageConsumer<Object> consumer2;

	void addMessageConsumers(Vertx vertx, String status) {
		// to handler
		consumer1 = vertx.eventBus().consumer(to, message -> {
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// registry handler
		consumer2 = vertx.eventBus().consumer(registryURL, message -> {
			JsonObject entry = new JsonObject().put("guid", userID).put("status", status).put("lastModified", 123);
			JsonObject replyBody = new JsonObject().put("code", 200).put("entry", entry);
			JsonObject reply = new JsonObject().put("body", replyBody);
			message.reply(reply);
		});
	}

	void unregisterMessageConsumers() {
		consumer1.unregister();
		consumer2.unregister();
	}

}