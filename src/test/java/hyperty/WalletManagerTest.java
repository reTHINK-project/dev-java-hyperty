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
class WalletManagerTest {

	private static String mongoHost = "localhost";
	private static String pointOfContact = "https://url_contact";
	private static String SIOTurl = "https://iot.alticelabs.com/api";
	private static String userID = "user-guid://test-userID";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static String reporterFromInvalid = "invalid";
	private static JsonObject profileInfo = new JsonObject().put("age", 24).put("cause", "user-guid://school-0");
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userURL).put("guid", userID).put("info", profileInfo));
	private static JsonObject identityGUID = new JsonObject().put("userProfile", new JsonObject().put("guid", userID));
	private static JsonObject identityPublicWallets = new JsonObject().put("userProfile",
			new JsonObject().put("guid", "public-wallets"));

	// MongoDB
	private static MongoClient mongoClient;
	private static String db_name = "test";
	private static String walletsCollection = "wallets";
	private static String iotCollection = "siotdevices";
	private static String walletAddress = "test-userID";
	private static String rankingInfoAddress = "data://sharing-cities-dsm/ranking";

	// public wallets
	private static String school0ID = "0";
	private static String smartIotProtostubUrl = "runtime://sharing-cities-dsm/protostub/smart-iot";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject().put("url", walletManagerHypertyURL);
		config.put("identity", identity);

		// mongo
		config.put("db_name", "test");
		config.put("collection", walletsCollection);
		config.put("mongoHost", "localhost");
		config.put("mongoPorts", "27017");
		config.put("mongoCluster", "NO");
		config.put("streams", new JsonObject().put("ranking", rankingInfoAddress));

		// public wallets
		String wallet0Address = "school0-wallet";
		String wallet1Address = "school1-wallet";
		String wallet2Address = "school2-wallet";
		String school0ID = "user-guid://school-0";
		String school1ID = "user-guid://school-1";
		String school2ID = "user-guid://school-2";
		JsonObject feed0 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet0userID");
		JsonObject feed1 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet1userID");
		JsonObject feed2 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet2userID");

		// publicWallets
		JsonArray publicWallets = new JsonArray();
		JsonObject walletCause0 = new JsonObject();
		walletCause0.put("address", wallet0Address);
		walletCause0.put("identity", school0ID);
		walletCause0.put("externalFeeds", new JsonArray().add(feed0));
		publicWallets.add(walletCause0);

		JsonObject walletCause1 = new JsonObject();
		walletCause1.put("address", wallet1Address);
		walletCause1.put("identity", school1ID);
		walletCause1.put("externalFeeds", new JsonArray().add(feed1));
		publicWallets.add(walletCause1);

		JsonObject walletCause2 = new JsonObject();
		walletCause2.put("address", wallet2Address);
		walletCause2.put("identity", school2ID);
		walletCause2.put("externalFeeds", new JsonArray().add(feed2));
		publicWallets.add(walletCause2);

		config.put("publicWallets", publicWallets);
		config.put("siot_stub_url", smartIotProtostubUrl);

		// pass observers
		JsonArray observers = new JsonArray();
		observers.add("");
		config.put("observers", observers);
		config.put("rankingTimer", 2000);

		// deploy
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), options, context.succeeding());

		// deploy smart Iot protostub

		JsonObject configSmartIotStub = new JsonObject();
		configSmartIotStub.put("url", smartIotProtostubUrl);
		configSmartIotStub.put("db_name", "test");
		configSmartIotStub.put("collection", "siotdevices");
		configSmartIotStub.put("mongoHost", mongoHost);
		configSmartIotStub.put("smart_iot_url", SIOTurl);
		configSmartIotStub.put("point_of_contact", pointOfContact);

		DeploymentOptions optionsconfigSmartIotStub = new DeploymentOptions().setConfig(configSmartIotStub)
				.setWorker(true);
		vertx.deployVerticle(SmartIotProtostub.class.getName(), optionsconfigSmartIotStub, res -> {
			System.out.println("SmartIOTProtustub Result->" + res.result());
		});

		// connect to Mongo
		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// finish setup
		checkpoint.flag();
	}

	@AfterAll
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(2);

		// erase wallets
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(walletsCollection, query, res -> {
			System.out.println("Wallets removed from DB");
			setupLatch.countDown();
		});

		// erase siot
		query = new JsonObject();
		mongoClient.removeDocuments(iotCollection, query, res -> {
			System.out.println("SIOT removed from DB");
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
	@Disabled
	void testRanking(VertxTestContext testContext, Vertx vertx) {

		int numWallets = 10;
		CountDownLatch setupLatch = new CountDownLatch(numWallets);

		for (int i = 0; i < numWallets; i++) {

			new Thread(() -> { // create wallet
				System.out.println("no wallet yet, creating");
				byte[] array = new byte[7]; // length is bounded by 7
				new Random().nextBytes(array);
				String walletID = new String(array, Charset.forName("UTF-8"));

				int min = 10;
				int max = 1000;

				int randomNum = ThreadLocalRandom.current().nextInt(min, max + 1);

				// build wallet document
				JsonObject newWallet = new JsonObject();

				String address = "walletAddress";
				newWallet.put("address", address);
				newWallet.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", walletID)));
				newWallet.put("created", new Date().getTime());
				newWallet.put("balance", randomNum);
				newWallet.put("transactions", new JsonArray());
				newWallet.put("status", "active");

				JsonObject document = new JsonObject(newWallet.toString());

				mongoClient.save(walletsCollection, document, id -> {
					System.out.println("New wallet with ID:" + id);
					setupLatch.countDown();
				});
			}).start();

		}
		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		testContext.completeNow();
	}

	public static final String publicWalletsOnChangesAddress = "wallet://public-wallets/changes";

	@Test
//	@Disabled
	void createAndTransfer(VertxTestContext testContext, Vertx vertx) {
		System.out.println("createWallet()");

		// 0 - add handler
		vertx.eventBus().consumer(publicWalletsOnChangesAddress, message -> {
			System.out.println("publicWalletsOnChangesAddress" + message.body().toString());
		});

		// 1 - create wallet (pass cause)
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("identity", identity);
		msg.put("from", "myself");
		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
			System.out.println("Received reply from wallet!: " + res.result().body().toString());
			JsonObject newMsg = new JsonObject();
			JsonObject body = new JsonObject().put("code", 200);
			newMsg.put("body", body);
			res.result().reply(newMsg);
		});

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// 2 - delete wallet
//		msg = new JsonObject();
//		msg.put("type", "delete");
//		msg.put("identity", identity);
//		msg.put("from", "myself");
//		JsonObject body = new JsonObject();
//		body.put("resource", "wallet");
//		body.put("value", "test-userID");
//		msg.put("body", body);
//		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
//			System.out.println("[WalletManagerTest] delete info: " + res.result().body().toString());
//			JsonObject wallet = (JsonObject) res.result().body();
//			walletAddress = wallet.getString("address");
//		});

		transferToWallet(testContext, vertx);

	}

	@Test
	@Disabled
	void removeWallet(VertxTestContext testContext, Vertx vertx) {
		System.out.println("removeWallet()");

		// 1 - create wallet (pass cause)
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", school0ID);
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

		// TODO - check if removed from DB

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
	void getPublicWalletsByRead(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", userID);
		msg.put("identity", identity);
		JsonObject body = new JsonObject().put("resource", "wallet").put("value", "public-wallets");
		msg.put("body", body);
		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {
			JsonObject wallet = new JsonObject(reply.result().body().toString());
			System.out.println("getPublicWalletsByRead(): " + wallet);
			testContext.completeNow();
		});
	}

	@Test
	@Disabled
	void getPublicWalletsByCreate(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - getPublicWalletsByCreate()");
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", userID);
		msg.put("identity", identityPublicWallets);
		vertx.eventBus().send(walletManagerHypertyURL, msg, reply -> {

			JsonObject newMsg = new JsonObject();
			JsonObject body = new JsonObject().put("code", 200);
			newMsg.put("body", body);
			reply.result().reply(newMsg, rep -> {
				JsonObject wallet = new JsonObject(rep.result().body().toString()).getJsonObject("wallet");
				System.out.println("getPublicWalletsByCreate(): " + wallet);
				testContext.completeNow();
			});
		});
	}

	void transferToWallet(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		System.out.println("transferToWallet() ");

		// create transaction object
		JsonObject transaction = new JsonObject();
		transaction.put("address", walletAddress);
		transaction.put("type", "transfer");
		transaction.put("recipient", walletAddress);
		transaction.put("source", "checkin");
		transaction.put("date", DateUtilsHelper.getCurrentDateAsISO8601());
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
		mongoClient.find(walletsCollection, new JsonObject().put("identity", identityGUID), res -> {
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