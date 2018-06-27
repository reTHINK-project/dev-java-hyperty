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
import util.DateUtils;
import walletManager.WalletManagerHyperty;


@ExtendWith(VertxExtension.class)
class WalletManagerTest {

	private static String userID = "test-userID";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static String reporterFromInvalid = "invalid";
	private static JsonObject profileInfo = new JsonObject().put("age", 24);
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = "test";
	private static String walletsCollection = "wallets";
	private static String walletAddress;
	private static String rankingInfoAddress = "data://sharing-cities-dsm/ranking";

	// public wallets
	private static String publicWalletAddress = "school0-wallet";
	private static int schoolID = 0;
	private static String smartIoTPlatform = "IoT0";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", walletManagerHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", "test");
		config.put("collection", walletsCollection);
		config.put("mongoHost", "localhost");
		config.put("streams", new JsonObject().put("ranking", rankingInfoAddress));

		// publicWallets
		JsonArray publicWallets = new JsonArray();
		JsonObject publicWallet = new JsonObject();
		publicWallet.put("address", publicWalletAddress);
		publicWallet.put("identity", schoolID);
		publicWallet.put("externalFeeds", smartIoTPlatform);
		publicWallets.add(publicWallet);
		config.put("publicWallets", publicWallets);

		// pass observers
		JsonArray observers = new JsonArray();
		observers.add("");
		config.put("observers", observers);

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), options, context.succeeding());

		// connect to Mongo
		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// finish setup
		checkpoint.flag();
	}

	@AfterAll
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(1);

		// erase wallets
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(walletsCollection, query, res -> {
			System.out.println("Wallets removed from DB");
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
	void createWalletAndTransfer(VertxTestContext testContext, Vertx vertx) {

		// 1 - create wallet (pass cause)
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", 0);
		identityWithInfo.getJsonObject("userProfile").put("info", info);
		msg.put("identity", identityWithInfo);
		msg.put("from", "myself");
		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
			System.out.println("Received reply from wallet!: " + res.result().body().toString());
			JsonObject newMsg = new JsonObject();
			JsonObject body = new JsonObject().put("code", 200);
			newMsg.put("body", body);
			res.result().reply(newMsg);
		});

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// 2 - get wallet address
		msg = new JsonObject();
		msg.put("type", "read");
		msg.put("identity", identity);
		msg.put("from", "myself");
		JsonObject body = new JsonObject();
		body.put("resource", "user");
		body.put("value", userID);
		msg.put("body", body);
		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
			System.out.println("[WalletManagerTest] wallet info: " + res.result().body().toString());
			JsonObject wallet = (JsonObject) res.result().body();
			walletAddress = wallet.getString("address");
		});

		// wait for op
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// 3- transfer to wallet
		msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		body = new JsonObject();
		body.put("resource", "something/" + walletAddress);
		JsonObject transaction = new JsonObject();
		transaction.put("recipient", "1");
		transaction.put("source", "1");
		transaction.put("date", DateUtils.getCurrentDateAsISO8601());
		transaction.put("value", 10);
		transaction.put("nonce", "10");
		body.put("value", transaction);
		msg.put("body", body);
		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
			System.out.println("[WalletManagerTest] wallet transaction: " + res.result().body().toString());
			JsonObject wallet = (JsonObject) res.result().body();
			walletAddress = wallet.getString("address");
		});

		// wait for op
		try {
			Thread.sleep(4000);
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
		mongoconfig.put("collection", walletsCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	/**
	 * Test reporter subscription
	 * 
	 * @param testContext
	 * @param vertx
	 */
	@Test
	@Disabled
	void testReporterSubscriptionValidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("url", "url");
		msg.put("from", userURL);
		msg.put("address", walletAddress);
		msg.put("identity", identity);

		String reporterSubscriptionAddress = walletAddress + "/subscription";
		System.out.println("sending message to reporter on " + reporterSubscriptionAddress);

		// subscription
		vertx.eventBus().send(reporterSubscriptionAddress, msg, reply -> {
			// check reply 200
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			assertEquals(200, code);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void testReporterOnReadValidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("url", "url");
		msg.put("from", userURL);
		msg.put("address", walletAddress);
		msg.put("identity", identity);

		System.out.println("sending message to reporter on " + walletAddress);

		// subscription
		vertx.eventBus().send(walletAddress, msg, reply -> {
			// check reply 200
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(200, code);
			testContext.completeNow();
		});
	}

	void testReporterOnReadInvalidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("url", "url");
		msg.put("from", reporterFromInvalid);

		System.out.println("sending message to reporter on " + walletAddress);

		// subscription
		vertx.eventBus().send(walletAddress, msg, reply -> {
			// check reply 403
			JsonObject rep = new JsonObject(reply.result().body().toString());
			int code = rep.getJsonObject("body").getInteger("code");
			System.out.println("Reporter reply: " + reply.result().body().toString());
			assertEquals(403, code);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void testReporterSubscriptionInvalidOrigin(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("url", "url");
		msg.put("from", reporterFromInvalid);

		String reporterSubscriptionAddress = walletAddress + "/subscription";
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
	@Disabled
	void getWalletAddress(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		JsonObject body = new JsonObject().put("resource", "user").put("value", identity);
		msg.put("body", body);

		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void getWallet(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		JsonObject body = new JsonObject().put("resource", "wallet").put("value", walletAddress);
		msg.put("body", body);

		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void getRankingInfo(VertxTestContext testContext, Vertx vertx) {
		System.out.println("getRankingInfo");
		JsonObject config = new JsonObject().put("type", "read").put("guid", userID);
		vertx.eventBus().send(rankingInfoAddress, config, message -> {
			// assert reply not null
			int ranking = ((JsonObject) message.result().body()).getInteger("ranking");
			System.out.println("Rankings: " + ranking);
			assertEquals(2, ranking);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void transferToWallet(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");

		// create transaction object
		JsonObject transaction = new JsonObject();
		transaction.put("address", walletAddress);
		transaction.put("type", "transfer");
		transaction.put("recipient", walletAddress);
		transaction.put("source", "source");
		transaction.put("date", DateUtils.getCurrentDateAsISO8601());
		transaction.put("value", 15);
		transaction.put("nonce", 1);
		transaction.put("from", "");
		JsonObject body = new JsonObject().put("resource", "wallet/" + walletAddress).put("value", transaction);
		msg.put("body", body);
		msg.put("from", userURL);

		vertx.eventBus().publish(walletManagerHypertyURL, msg);

		// wait some time and check wallet
		try {
			Thread.sleep(4000);
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