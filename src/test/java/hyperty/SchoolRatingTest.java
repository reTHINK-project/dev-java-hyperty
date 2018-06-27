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
import tokenRating.SchoolRatingHyperty;
import util.DateUtils;
import walletManager.WalletManagerHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class SchoolRatingTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	private static String schoolRatingHypertyURL = "hyperty://sharing-cities-dsm/school-rating";
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
	private static JsonObject identity;

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", userID).put("guid", userID));
		JsonObject config = new JsonObject();
		config.put("url", schoolRatingHypertyURL);
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
		vertx.deployVerticle(SchoolRatingHyperty.class.getName(), optionsLocation, context.succeeding());

		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);
		
		final String causeAddress = "cause1-address";

		configWalletManager.put("observers", new JsonArray().add(causeAddress));
		configWalletManager.put("causes", new JsonArray().add(causeAddress));

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(true);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});

		makeMongoConnection(vertx);
		
		vertx.eventBus().consumer(causeAddress, message -> {
			JsonObject msg = (JsonObject) message.body();
			System.out.println("was invited: " + msg);
			
			// send message to invitation address (get from)
			String invitationAddress = msg.getString("from");
			System.out.println("invitation address: " + invitationAddress);
			
			msg.put("from", causeAddress);
			vertx.eventBus().send(invitationAddress, msg, res -> {
				System.out.println("Received reply from wallet invitation: " + res.result().body().toString());
				JsonObject newMsg = new JsonObject();
				JsonObject body = new JsonObject().put("code", 200);
				newMsg.put("body", body);
			});
			
			
		});

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

		vertx.eventBus().send(schoolRatingHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		try {
			Thread.sleep(5000);
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
		mongoClient.removeDocuments(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			setupLatch.countDown();
		});

		// remove from wallets
		query = new JsonObject();
		mongoClient.removeDocuments(walletsCollection, query, res -> {
			System.out.println("Wallet removed from DB");
			setupLatch.countDown();
		});

		// remove from dataobjects
		query = new JsonObject();
		mongoClient.removeDocuments(dataobjectsCollection, query, res -> {
			System.out.println("Dataobject removed from DB");
			setupLatch.countDown();
		});

		// remove from shops
		query = new JsonObject();
		mongoClient.removeDocuments(shopsCollection, query, res -> {
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
	void createWalletAndSubscribe(VertxTestContext testContext, Vertx vertx) {
		
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", 0);
		identityWithInfo.getJsonObject("userProfile").put("info", info);
		msg.put("identity", identityWithInfo);
		msg.put("from", schoolRatingHypertyURL);
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
		
		testContext.completeNow();

	}

	@Test
	@Disabled
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