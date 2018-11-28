package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
	private static String urlSubscription = "hyperty://sharing-cities-dsm/offline-sub-mgr/subscription";
	private static String registryURL = "hyperty://sharing-cities-dsm/registry";
	private static String offlineSubMgrURLStatus = "hyperty://sharing-cities-dsm/offline-sub-mgr/status";
	private static String urlRegister = "hyperty://sharing-cities-dsm/offline-sub-mgr/register";
	private static String userURL = "user://sharing-cities-dsm/location-identity";

	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));

	// MongoDB
	private static MongoClient mongoClient;
	private static String pendingSubscriptions = "pendingSubscriptions";
	private static String dataObjectsRegistry = "dataObjectsRegistry";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", offlineSubMgrHypertyURL);
		config.put("identity", identity);
		config.put("registry", registryURL);

		// mongo
		config.put("db_name", "test");
		config.put("collection", pendingSubscriptions);
		config.put("mongoHost", mongoHost);
		config.put("mongoPorts", "27017");
		config.put("mongoCluster", "NO");

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(false);
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

		CountDownLatch setupLatch = new CountDownLatch(2);
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(pendingSubscriptions, query, res -> {
			setupLatch.countDown();
		});
		mongoClient.removeDocuments(dataObjectsRegistry, query, res -> {
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static String subscriptionURL = "observer/subscription";

	@Test
//	@Disabled
	void statusTest(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "statusTest()");

		sendStatusMessage(vertx);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(pendingSubscriptions, new JsonObject(), res -> {
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

	private void sendStatusMessage(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "update");
		JsonObject body = new JsonObject();
		body.put("resource", userID);
		body.put("status", "online");
		msg.put("body", body);
		vertx.eventBus().send(offlineSubMgrURLStatus, msg);
	}

	CountDownLatch latch;

	@Test
	@Disabled
	void dataObjectTest(VertxTestContext testContext, Vertx vertx) {
		System.out.println(logMessage + "dataObjectTest: 1 - Registration ");

		try {
			sendDOCreationMessage(vertx);
			Thread.sleep(1000);
			latch = new CountDownLatch(1);
			new Thread(() -> {
				mongoClient.find(dataObjectsRegistry, new JsonObject(), res -> {
					assertEquals(1, res.result().size());
					latch.countDown();
				});
			}).start();
			latch.await();

			testContext.completeNow();

			System.out.println(logMessage + "dataObjectTest: 2 - Unregistration ");

			sendDODeleteMessage(vertx);
			Thread.sleep(1000);
			latch = new CountDownLatch(1);
			new Thread(() -> {
				mongoClient.find(dataObjectsRegistry, new JsonObject(), res -> {
					assertEquals(0, res.result().size());
					latch.countDown();
				});
			}).start();
			latch.await();

		} catch (Exception e) {
			e.printStackTrace();
		}
		testContext.completeNow();
	}

	private void sendDODeleteMessage(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("identity", identity);
		msg.put("id", "1");
		msg.put("from", "hyperty://<sp-domain>/<hyperty-instance-identifier>");
		msg.put("to", "hyperty-runtime://<sp-domain>/<hyperty-runtime-instance-identifier>/sm");
		JsonObject body = new JsonObject();
		body.put("resource", "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>");
		msg.put("body", body);
		vertx.eventBus().send(urlRegister, msg);
	}

	private void sendDOCreationMessage(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("id", "1");
		msg.put("from", "hyperty://<sp-domain>/<hyperty-instance-identifier>");
		msg.put("to", "hyperty-runtime://<sp-domain>/<hyperty-runtime-instance-identifier>/sm");
		JsonObject body = new JsonObject();
		body.put("resource", "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>");
		body.put("value", new JsonObject());
		msg.put("body", body);
		vertx.eventBus().send(urlRegister, msg);
	}

	@Test
	@Disabled
	void subscriptionOnline(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "subscriptionOnline()");
		sendDOCreationMessage(vertx);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		addMessageConsumers(vertx, "online");
		sendSubscriptionMessage(vertx);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(pendingSubscriptions, new JsonObject(), res -> {
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

	private void sendSubscriptionMessage(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "subscribe");
		msg.put("identity", identity);
		msg.put("id", "1");
		msg.put("from", "hyperty-runtime://<observer-sp-domain>/<hyperty-observer-runtime-instance-identifier>/sm");
		msg.put("to", subscriptionURL);
		JsonObject body = new JsonObject();
		body.put("source", "hyperty://<observer-sp-domain>/<hyperty-observer-instance-identifier>");
		msg.put("body", body);
		vertx.eventBus().send(urlSubscription, msg);

	}

	@Test
	@Disabled
	void subscriptionOffline(VertxTestContext testContext, Vertx vertx) {

		System.out.println(logMessage + "subscriptionOffline()");
		sendDOCreationMessage(vertx);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		addMessageConsumers(vertx, "offline");
		sendSubscriptionMessage(vertx);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch latch = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(pendingSubscriptions, new JsonObject(), res -> {
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
		mongoconfig.put("db_name", dbName);
		mongoconfig.put("database", dbName);
		mongoconfig.put("collection", pendingSubscriptions);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	MessageConsumer<Object> consumer1;
	MessageConsumer<Object> consumer2;

	void addMessageConsumers(Vertx vertx, String status) {
		// to handler
		consumer1 = vertx.eventBus().consumer(subscriptionURL, message -> {
			System.out.println(logMessage + "replying");
			JsonObject body = new JsonObject().put("code", 200).put("value", new JsonObject());
			JsonObject msg = new JsonObject().put("body", body);
			msg.put("type", "response");
			msg.put("from", subscriptionURL);
			msg.put("to", "hyperty-runtime://<observer-sp-domain>/<hyperty-observer-runtime-instance-identifier>/sm");
			message.reply(msg);
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