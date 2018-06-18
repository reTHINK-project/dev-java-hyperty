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
import tokenRating.CheckInRatingHyperty;
import walletManager.WalletManagerHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class CheckInTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	private static String checkinHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
	private static String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";
	private static String bonusInfoStreamAddress = "data://sharing-cities-dsm/bonus";
	private static String from = "tester";

	private static String storeID = "test-shopID";
	// mongo config
	private static MongoClient mongoClient;
	private static String ratesCollection = "rates";
	private static String shopsCollection = "shops";
	private static String walletsCollection = "wallets";
	private static String dataobjectsCollection = "dataobjects";
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", userID).put("guid", userID));
		JsonObject config = new JsonObject();
		config.put("url", checkinHypertyURL);
		config.put("identity", identity);
		config.put("tokens_per_checkin", 10);
		config.put("checkin_radius", 500);
		config.put("min_frequency", 1);
		config.put("hyperty", "123");
		config.put("stream", "token-rating");
		config.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		config.put("streams",
				new JsonObject().put("shops", shopsInfoStreamAddress).put("bonus", bonusInfoStreamAddress));
		// mongo
		config.put("collection", ratesCollection);
		config.put("db_name", db_name);
		config.put("mongoHost", mongoHost);

		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());

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
			System.out.println("ElearningRatingHyperty Result->" + res.result());
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

		vertx.eventBus().send(checkinHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch setupLatch = new CountDownLatch(2);

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

		new Thread(() -> {
			// add shop
			JsonObject document = new JsonObject();
			document.put("id", storeID);
			JsonObject storeLocation = new JsonObject();
			storeLocation.put("degrees-latitude", 40);
			storeLocation.put("degrees-longitude", 50);
			document.put("location", storeLocation);
			mongoClient.insert(shopsCollection, document, res -> {
				System.out.println("Setup complete - shops");
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

		CountDownLatch setupLatch = new CountDownLatch(4);

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

		// remove from shops
		query = new JsonObject();
		query.put("id", storeID);
		mongoClient.removeDocument(shopsCollection, query, res -> {
			System.out.println("Store removed from DB");
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
	void userCloseToShop(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - User close to shop");
		// latitude
		JsonObject latitude = new JsonObject().put("name", "latitude").put("value", 40.0001);
		// longitude
		JsonObject longitude = new JsonObject().put("name", "longitude").put("value", 50);
		// shopID
		JsonObject checkin = new JsonObject().put("name", "checkin").put("value", storeID);
		// checkInMessage.put("identity", new JsonObject());
		// checkInMessage.put("userID", userID);

		JsonArray toSend = new JsonArray();
		toSend.add(latitude);
		toSend.add(longitude);
		toSend.add(checkin);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(6000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// check if rate was added to user
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray checkIns = rates.getJsonArray("checkin");
			assertEquals(1, checkIns.size());
			testContext.completeNow();
		});

	}

	@Test
	@Disabled
	void userFarFromShop(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - User far from shop");
		JsonObject checkInMessage = new JsonObject().put("latitude", 40.1).put("longitude", 50);
		checkInMessage.put("userID", userID);
		checkInMessage.put("shopID", storeID);
		vertx.eventBus().publish(from, checkInMessage);

		// wait for op
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// check if rate was added to user
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray checkIns = rates.getJsonArray("checkin");
			assertEquals(0, checkIns.size());
			testContext.completeNow();
		});

	}

	@Test
	void getBonus(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - get bonus");
		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(bonusInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonObject bonus = (JsonObject) message.result().body();
			System.out.println("BONUS: " + bonus);
			testContext.completeNow();
		});
	}

	@Test
	void getShops(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - get shops");
		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(shopsInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonObject shops = (JsonObject) message.result().body();
			System.out.println("SHOPS: " + shops);
			testContext.completeNow();
		});
	}
}