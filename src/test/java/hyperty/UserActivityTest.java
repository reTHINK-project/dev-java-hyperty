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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import tokenRating.UserActivityRatingHyperty;
import walletManager.WalletManagerHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class UserActivityTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	private static String userActivityHypertyURL = "hyperty://sharing-cities-dsm/user-activity";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	// mongo config
	private static MongoClient mongoClient;
	// collections
	private static String ratesCollection = "rates";
	private static String walletsCollection = "wallets";
	private static String dataobjectsCollection = "dataobjects";
	
	private static String db_name = "test";
	private static String source = "user-activity";
	private static String mongoHost = "localhost";
	

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		String streamAddress = "vertx://sharing-cities-dsm/user-activity";
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", userID).put("guid", userID));
		JsonObject configUserActivity = new JsonObject();
		configUserActivity.put("url", userActivityHypertyURL);
		configUserActivity.put("identity", identity);
		// mongo
		configUserActivity.put("db_name", "test");
		configUserActivity.put("collection", "rates");
		configUserActivity.put("mongoHost", mongoHost);

		configUserActivity.put("tokens_per_walking_km", 10);
		configUserActivity.put("tokens_per_biking_km", 10);
		configUserActivity.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configUserActivity.put("hyperty", "123");
		configUserActivity.put("stream", streamAddress);
		DeploymentOptions optionsUserActivity = new DeploymentOptions().setConfig(configUserActivity).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(UserActivityRatingHyperty.class.getName(), optionsUserActivity, context.succeeding());


		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);

		configWalletManager.put("observers", new JsonArray().add(""));

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(true);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});

		makeMongoConnection(vertx);

		vertx.eventBus().consumer(subscriptionsAddress, message -> {
			System.out.println("TEST - sub received: " + message.body().toString());
			// send reply
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(6000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));

		vertx.eventBus().send(userActivityHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch setupLatch = new CountDownLatch(1);

		new Thread(() -> { // create wallet
			System.out.println("no wallet yet, creating");

			// build wallet document
			JsonObject newWallet = new JsonObject();

			String address = "walletAddress";
			newWallet.put("address", address);
			newWallet.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", 0);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");

			JsonObject document = new JsonObject(newWallet.toString());

			mongoClient.save(walletsCollection, document, id -> {
				System.out.println("New wallet with ID:" + id);
				setupLatch.countDown();
			});
		}).start();

	

		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		checkpoint.flag();
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("database", db_name);
		mongoconfig.put("collection", ratesCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	@AfterAll
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(3);

		// remove from rates
		JsonObject query = new JsonObject();
		query.put("user", userID);
		mongoClient.removeDocument(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			setupLatch.countDown();
		});

		// remove from wallets
		query = new JsonObject();
		query.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
		mongoClient.removeDocument(walletsCollection, query, res -> {
			System.out.println("Wallet removed from DB");
			setupLatch.countDown();
		});

		// remove from dataobjects
		query = new JsonObject();
		query.put("url", userID);
		mongoClient.removeDocument(dataobjectsCollection, query, res -> {
			System.out.println("Dataobject removed from DB");
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
	void sessionWithoutTokens(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - Session without tokens");
		JsonObject activityMessage = new JsonObject();

		activityMessage.put("identity", new JsonObject());
		activityMessage.put("userID", userID);
		activityMessage.put("type", "user_walking_context");
		activityMessage.put("value", 200);
		activityMessage.put("source", source);
		JsonArray toSend = new JsonArray();
		toSend.add(activityMessage);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(8000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// check if session is unprocessed
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray sessions = rates.getJsonArray("user-activity");
			assertEquals(1, sessions.size());
			assertEquals(false, sessions.getJsonObject(0).getBoolean("processed"));
			testContext.completeNow();
		});
	}

	@Test
	void sessionWithTokens(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - Session with tokens");
		JsonObject activityMessage = new JsonObject();


		activityMessage.put("identity", new JsonObject());
		activityMessage.put("userID", userID);
		activityMessage.put("type", "user_walking_context");
		activityMessage.put("value", 600);
		activityMessage.put("source", source);
		JsonArray toSend = new JsonArray();
		toSend.add(activityMessage);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(8000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// check if session is unprocessed
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray sessions = rates.getJsonArray("user-activity");
			assertEquals(2, sessions.size());
			// check all processed
			assertEquals(true, sessions.getJsonObject(0).getBoolean("processed"));
			assertEquals(true, sessions.getJsonObject(1).getBoolean("processed"));
			testContext.completeNow();
		});
	}

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", userID);
		vertx.eventBus().publish("token-rating", msg);
	}
}