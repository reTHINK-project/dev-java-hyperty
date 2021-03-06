package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import tokenRating.EnergySavingRatingHyperty;
import walletManager.WalletManagerHyperty;

@ExtendWith(VertxExtension.class)
@Disabled
class EnergySavingRatingTest {

	private static final String logMessage = "[EnergySavingRatingTest] ";
	private static String pointOfContact = "https://url_contact";
	private static String SIOTurl = "https://iot.alticelabs.com/api";

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	private static String energySavingRatingHypertyURL = "hyperty://sharing-cities-dsm/energy-saving-rating";
	private static String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";
	private static String bonusInfoStreamAddress = "data://sharing-cities-dsm/bonus";

	// mongo config
	private static MongoClient mongoClient;
	private static String ratesCollection = "rates";
	private static String shopsCollection = "shops";
	private static String walletsCollection = "wallets";
	private static String dataobjectsCollection = "dataobjects";
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", userID).put("guid", userID));

	// public wallets
	private static String school0ID = "user-guid://school-0";
	private static String school1ID = "user-guid://school-1";
	private static String school2ID = "user-guid://school-2";

	private static String userCGUIDURL = "userCGUIDURL";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		String smartIotProtostubUrl = "runtime://sharing-cities-dsm/protostub/smart-iot";

		JsonObject config = new JsonObject();
		config.put("url", energySavingRatingHypertyURL);
		config.put("identity", identity);
		// config
		config.put("hyperty", "123");
		config.put("stream", "token-rating");
		config.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		// mongo
		config.put("collection", ratesCollection);
		config.put("db_name", db_name);
		config.put("mongoHost", mongoHost);
		config.put("mongoPorts", "27017");
		config.put("mongoCluster", "NO");

		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(false);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(EnergySavingRatingHyperty.class.getName(), optionsLocation, context.succeeding());

		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);
		configWalletManager.put("mongoPorts", "27017");
		configWalletManager.put("mongoCluster", "NO");

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

		configWalletManager.put("publicWallets", publicWallets);

		final String causeAddress = "cause1-address";

		configWalletManager.put("observers", new JsonArray().add(causeAddress));
		configWalletManager.put("causes", new JsonArray().add(causeAddress));
		configWalletManager.put("siot_stub_url", smartIotProtostubUrl);
		configWalletManager.put("rankingTimer", 2000);

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(false);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});

		// deploy smart Iot protostub

		JsonObject configSmartIotStub = new JsonObject();
		configSmartIotStub.put("url", smartIotProtostubUrl);
		configSmartIotStub.put("db_name", "test");
		configSmartIotStub.put("collection", "siotdevices");
		configSmartIotStub.put("mongoHost", mongoHost);
		configSmartIotStub.put("smart_iot_url", SIOTurl);
		configSmartIotStub.put("point_of_contact", pointOfContact);

		DeploymentOptions optionsconfigSmartIotStub = new DeploymentOptions().setConfig(configSmartIotStub)
				.setWorker(false);
		vertx.deployVerticle(SmartIotProtostub.class.getName(), optionsconfigSmartIotStub, res -> {
			System.out.println("SmartIOTProtustub Result->" + res.result());
		});

		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(3000);
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

		CountDownLatch setupLatch = new CountDownLatch(2);

		// remove from rates
		JsonObject query = new JsonObject();
		mongoClient.removeDocuments(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			setupLatch.countDown();
		});

		// remove from wallets
		query = new JsonObject();
//		mongoClient.removeDocuments(walletsCollection, query, res -> {
//			System.out.println("Wallet removed from DB");
//			setupLatch.countDown();
//		});

		// remove from dataobjects
		query = new JsonObject();
		mongoClient.removeDocuments(dataobjectsCollection, query, res -> {
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
	@Disabled
	void energySavingPrivate(VertxTestContext testContext, Vertx vertx) {

		// 1 - create wallet (pass cause)
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
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

		// 2 - subscribe (private)
		msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
		JsonObject body = new JsonObject();
		body.put("identity", userCGUIDURL);
		msg.put("body", body);
		vertx.eventBus().send(EnergySavingRatingHyperty.ratingPrivate, msg);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// 3 - send energySavingsMessage
		JsonObject energySavingsMessage = new JsonObject();
		String contextIdentifier = "something";
		energySavingsMessage.put("id", contextIdentifier);
		energySavingsMessage.put("unit", "WATT_PERCENTAGE");

		// ContextValues
		JsonObject contextValueUser = new JsonObject();
		contextValueUser.put("type", "power");
		contextValueUser.put("value", 10);

		JsonArray values = new JsonArray().add(contextValueUser);
		energySavingsMessage.put("values", values);

		JsonArray toSend = new JsonArray();
		toSend.add(energySavingsMessage);
		vertx.eventBus().send(changesAddress, toSend);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject walletIdentity = new JsonObject().put("userProfile", new JsonObject().put("guid", userID));
		JsonObject publicWalletIdentity = new JsonObject().put("userProfile",
				new JsonObject().put("guid", "user-guid://public-wallets"));

		CountDownLatch assertions = new CountDownLatch(2);

		new Thread(() -> {

			mongoClient.find(walletsCollection, new JsonObject().put("identity", walletIdentity), res -> {
				JsonObject walletInfo = res.result().get(0);

				// check balance updated
				int currentBalance = walletInfo.getInteger("balance");
				assertEquals(100, currentBalance);

				// check if transaction in transactions array
				JsonArray transactions = walletInfo.getJsonArray("transactions");
				assertEquals(1, transactions.size());
				assertions.countDown();
			});
		}).start();

		new Thread(() -> {

			mongoClient.find(walletsCollection, new JsonObject().put("identity", publicWalletIdentity), res -> {
				JsonObject wallets = res.result().get(0);
				System.out.println("here: " + wallets);
				JsonObject walletInfo = wallets.getJsonArray("wallets").getJsonObject(0);

				// check balance updated int currentBalance =
				int currentBalance = walletInfo.getInteger("balance");
				assertEquals(100, currentBalance);

				// check if transaction in transactions array JsonArray transactions =
				walletInfo.getJsonArray("transactions");
				JsonArray transactions = walletInfo.getJsonArray("transactions");
				assertEquals(1, transactions.size());
				assertions.countDown();

				// counters JsonObject counters =
				JsonObject counters = walletInfo.getJsonObject(WalletManagerHyperty.counters);
				assertEquals(100, (int) counters.getInteger("energy-saving"));
				assertions.countDown();
			});
		}).start();

		try {
			assertions.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	// @Disabled
	void energySavingPublic(VertxTestContext testContext, Vertx vertx) {

		// 1 - subscribe (public)
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("external", true);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
		JsonObject body = new JsonObject();
		body.put("identity", userCGUIDURL);
		msg.put("body", body);
		vertx.eventBus().send(EnergySavingRatingHyperty.ratingPublic, msg);

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// 2 - send energySavingsMessage
		JsonObject energySavingsMessage = new JsonObject();
		String contextIdentifier = "something";
		energySavingsMessage.put("id", contextIdentifier);
		energySavingsMessage.put("unit", "WATT_PERCENTAGE");

		// ContextValues
		JsonObject contextValueCause0 = new JsonObject();
		JsonObject contextValueCause1 = new JsonObject();
		JsonObject contextValueCause2 = new JsonObject();
		JsonArray values = new JsonArray();
		// cause 0
		contextValueCause0.put("type", "POWER");
		JsonObject value0 = new JsonObject();
		value0.put("id", school0ID);
		value0.put("value", 10);
		contextValueCause0.put("value", value0);
		values.add(contextValueCause0);
		// cause 1
		contextValueCause1.put("type", "POWER");
		JsonObject value1 = new JsonObject();
		value1.put("id", school1ID);
		value1.put("value", 20);
		contextValueCause1.put("value", value1);
		values.add(contextValueCause1);
		// cause 2
		contextValueCause2.put("type", "POWER");
		JsonObject value2 = new JsonObject();
		value2.put("id", school2ID);
		value2.put("value", 5);
		contextValueCause2.put("value", value2);
		values.add(contextValueCause2);

		energySavingsMessage.put("values", values);

		JsonArray toSend = new JsonArray();
		toSend.add(energySavingsMessage);
		vertx.eventBus().send(changesAddress, toSend);

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	@Disabled
	void getCauseSupporters(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(1);

		JsonObject messageData = new JsonObject();
		messageData.put("causeID", school0ID);
		vertx.eventBus().send("wallet-cause", messageData, res -> {
			JsonObject result = (JsonObject) res.result().body();
			int causeSupportersTotal = result.getInteger(WalletManagerHyperty.causeSupportersTotal);
			int causeSupportersWithSM = result.getInteger(WalletManagerHyperty.causeSupportersWithSM);
			setupLatch.countDown();
		});

		try {
			setupLatch.await(5L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}
}