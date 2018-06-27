package hyperty;

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
import tokenRating.EnergySavingRatingHyperty;
import walletManager.WalletManagerHyperty;

@ExtendWith(VertxExtension.class)
@Disabled
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
			new JsonObject().put("userURL", userID).put("guid", userID));

	private static String userCGUIDURL = "userCGUIDURL";

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

		vertx.eventBus().consumer(userID, message -> {
			System.out.println(logMessage + "sub received: " + message.body().toString());
			// send reply
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(3000);
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


		// subscribe to stream
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
		JsonObject body = new JsonObject();
		body.put("identity", userCGUIDURL);
		msg.put("body", body);
		vertx.eventBus().send(energySavingRatingHypertyURL, msg, res -> {
			System.out.println(logMessage + "Received reply: " + res.result().body().toString());
			JsonObject newMsg = new JsonObject();
			newMsg.put("body", new JsonObject().put("code", 200));
			res.result().reply(newMsg);
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

		CountDownLatch setupLatch = new CountDownLatch(3);

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

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Test
	void sendToOnChanges(VertxTestContext testContext, Vertx vertx) {
		System.out.println(logMessage + "test - sendToOnChanges() ");

		JsonObject iotmessage = new JsonObject();
		iotmessage.put("name", "iot");
		iotmessage.put("data", 321);
		iotmessage.put("id", "3eecbfb1-745c-4768-a0b4-fffed40f8a5a");
		iotmessage.put("streamId", "1a5a1b97-1332-4d09-ba4b-169a6ce4dca4");
		iotmessage.put("deviceId", "355d0eb9-ee9a-4381-bcb0-cced33b8df05");
		iotmessage.put("type", EnergySavingRatingHyperty.ratingPublic);

		JsonArray toSend = new JsonArray();
		toSend.add(iotmessage);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println(logMessage + "changes reply: " + reply.toString());
		});

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}
}