package hyperty;

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
import tokenRating.EnergySavingRatingHyperty;
import walletManager.WalletManagerHyperty;

@ExtendWith(VertxExtension.class)
class EnergySavingRatingTest {

	private static final String logMessage = "[EnergySavingRatingTest] ";

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
			new JsonObject().put("userURL", userID).put("guid", userID));;

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject config = new JsonObject();
		config.put("url", energySavingRatingHypertyURL);
		config.put("identity", identity);
		// config
		config.put("tokens_per_checkin", 10);
		config.put("checkin_radius", 500);
		config.put("min_frequency", 1);
		//
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
		vertx.deployVerticle(EnergySavingRatingHyperty.class.getName(), optionsLocation, context.succeeding());

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
			System.out.println(logMessage + "sub received: " + message.body().toString());
			// send reply
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));

		vertx.eventBus().send(energySavingRatingHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		try {
			Thread.sleep(4000);
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

	private String SIOTendpoint = "siot0-endpoint";

	@Test
	void sendInvitation(VertxTestContext testContext, Vertx vertx) {
		System.out.println(logMessage + "test - sendInvitation ");

		/*
		 * On receiving an invitation to observe a new device energy comsuption stream,
		 * the Hyperty subscribes it adding a listener to it according to weather it is
		 * a public device or a private device. The invitation should include the wallet
		 * and the type of device.
		 */
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		JsonObject identityWithInfo = identity.copy();
		JsonObject info = new JsonObject().put("cause", 0);
		identityWithInfo.getJsonObject("userProfile").put("info", info);
		msg.put("identity", identityWithInfo);
		msg.put("from", subscriptionsAddress);
		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
			System.out.println(logMessage + "Received reply: " + res.result().body().toString());
			JsonObject newMsg = new JsonObject();
			JsonObject body = new JsonObject().put("code", 200);
			newMsg.put("body", body);
			res.result().reply(newMsg);
		});

		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println(logMessage + "sending data");
		JsonObject iotmessage = new JsonObject().put("name", "checkin").put("value", 0);
		iotmessage.put("data", "321");
		iotmessage.put("id", "3eecbfb1-745c-4768-a0b4-fffed40f8a5a");
		iotmessage.put("streamId", "1a5a1b97-1332-4d09-ba4b-169a6ce4dca4");
		iotmessage.put("deviceId", "355d0eb9-ee9a-4381-bcb0-cced33b8df05");
		iotmessage.put("type", EnergySavingRatingHyperty.ratingPublic);

		JsonArray toSend = new JsonArray();
		toSend.add(iotmessage);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println(logMessage + "changes reply: " + reply.toString());
		});

		testContext.completeNow();

	}
}