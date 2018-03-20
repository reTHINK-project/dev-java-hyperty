package altice_labs.dsm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

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
import token_rating.WalletManagerHyperty;
import token_rating.WalletManagerMessage;
import util.DateUtils;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class WalletManagerTest {

	private static String walletManagerHypertyURL;
	private static String walletManagerHypertyIdentity;
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static String reporterFromInvalid = "invalid";
	private static String userID = "identity";
	private static JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", userURL));
	private static String reporterAddress = "reporter";
	private static MongoClient mongoClient;
	private static String walletsCollection = "wallets";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
		walletManagerHypertyIdentity = "school://sharing-cities-dsm/wallet-manager";
		JsonObject config = new JsonObject().put("url", walletManagerHypertyURL).put("identity", identity)
				.put("database", "test").put("collection", "wallets").put("mongoHost", "localhost")
				.put("dataObjectUrl", "reporter");

		// pass observers
		JsonArray observers = new JsonArray();
		observers.add("");
		config.put("observers", observers);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsLocation, context.succeeding());
		System.out.println("-> Wallet Manager deployed");

		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// create wallet message
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);
		msg.put("identity", identity);
		msg.put("url", "url");
		msg.put("from", "123");

		vertx.eventBus().publish(walletManagerHypertyURL, msg);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		checkpoint.flag();

		// connect to Mongo
		makeMongoConnection(vertx);
	}

	@AfterAll
	static void deleteWallet(VertxTestContext testContext, Vertx vertx) {
		// JsonObject msg = new JsonObject();
		// msg.put("type", WalletManagerMessage.TYPE_DELETE);
		// msg.put("identity", identity);
		// msg.put("from", "from");
		//
		// vertx.eventBus().publish(walletManagerHypertyURL, msg);

		JsonObject query = new JsonObject();
		query.put("identity", identity);

		mongoClient.removeDocument(walletsCollection, query, res -> {
			System.out.println("Wallet removed from DB");
			testContext.completeNow();
		});

	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", "test")
				.put("database", "test").put("collection", walletsCollection);

		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	/**
	 * Test reporter subscription
	 * 
	 * @param testContext
	 * @param vertx
	 */
	@Test
	void testReporterSubscription(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);
		msg.put("url", "url");
		msg.put("from", userURL);

		String reporterSubscriptionAddress = reporterAddress + "/subscription";
		System.out.println("sending message to reporter on " + reporterSubscriptionAddress);

		// subscription
		vertx.eventBus().send(reporterSubscriptionAddress, msg, reply -> {
			// check reply 200
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(200, code);
			testContext.completeNow();
		});
	}
	
	@Test
	void testReporterOnReadValidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);
		msg.put("url", "url");
		msg.put("from", userURL);

		System.out.println("sending message to reporter on " + reporterAddress);

		// subscription
		vertx.eventBus().send(reporterAddress, msg, reply -> {
			// check reply 200
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(200, code);
			testContext.completeNow();
		});
	}

	@Test
	void testReporterOnReadInvalidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);
		msg.put("url", "url");
		msg.put("from", reporterFromInvalid);

		System.out.println("sending message to reporter on " + reporterAddress);

		// subscription
		vertx.eventBus().send(reporterAddress, msg, reply -> {
			// check reply 403
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(403, code);
			testContext.completeNow();
		});
	}

	@Test
	void testReporterSubscriptionInvalidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);
		msg.put("url", "url");
		msg.put("from", reporterFromInvalid);

		String reporterSubscriptionAddress = reporterAddress + "/subscription";
		System.out.println("sending message to reporter on " + reporterSubscriptionAddress);

		// subscription
		vertx.eventBus().send(reporterSubscriptionAddress, msg, reply -> {
			// check reply 403
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(403, code);
			testContext.completeNow();
		});
	}

	@Test
	void getWalletAddress(VertxTestContext testContext, Vertx vertx) {

		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_READ);
		JsonObject body = new JsonObject().put("resource", "user").put("value", userID);
		msg.put("body", body);

		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {
			// System.out.println(reply.result().toString());
			testContext.completeNow();
		});
	}
	
	@Test
	void getWallet(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		String walletAddress = "123";
		msg.put("type", WalletManagerMessage.TYPE_READ);
		JsonObject body = new JsonObject().put("resource", "wallet").put("value", "wallet-address");
		msg.put("body", body);

		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {
			System.out.println(reply.result().toString());
			testContext.completeNow();
		});
	}
	
	@Test
	void transferToWallet(VertxTestContext testContext, Vertx vertx) {
		String walletAddress = "123";
		JsonObject msg = new JsonObject();
		msg.put("type", WalletManagerMessage.TYPE_CREATE);

		// create transaction object
		JsonObject transaction = new JsonObject();
		transaction.put("address", walletAddress);
		transaction.put("type", "transfer");
		transaction.put("recipient", walletAddress);
		transaction.put("source", "source");
		transaction.put("date", DateUtils.getCurrentDateAsISO8601());
		transaction.put("value", 15);
		transaction.put("nonce", 1);
		JsonObject body = new JsonObject().put("resource", "wallet/" + "wallet-address").put("value", transaction);
		msg.put("body", body);

		vertx.eventBus().publish(walletManagerHypertyURL, msg);

		// wait sometime and check wallet
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// get wallet
		mongoClient.find(walletsCollection, new JsonObject().put("identity", identity), res -> {
			JsonObject walletInfo = res.result().get(0);

			// check balance updated
			int currentBalance = walletInfo.getInteger("balance");
			assertEquals(15, currentBalance);

			// check if transaction in transactions array
			JsonArray transactions = walletInfo.getJsonArray("transactions");
			assertEquals(1, transactions.size());
			testContext.completeNow();
		});

	}

}