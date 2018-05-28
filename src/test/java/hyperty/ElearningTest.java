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
import tokenRating.ElearningRatingHyperty;
import walletManager.WalletManagerHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class ElearningTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	// mongo config
	private static MongoClient mongoClient;
	private static String ratesCollection = "rates";
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String userActivityHypertyURL = "hyperty://sharing-cities-dsm/elearning";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		String streamAddress = "vertx://sharing-cities-dsm/elearning";
		JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", userID));
		JsonObject configElearning = new JsonObject();
		configElearning.put("url", userActivityHypertyURL);
		configElearning.put("identity", identity);

		// mongo
		configElearning.put("db_name", "test");
		configElearning.put("collection", "rates");
		configElearning.put("mongoHost", mongoHost);

		configElearning.put("tokens_per_completed_quiz", 10);
		configElearning.put("tokens_per_correct_answer", 10);
		configElearning.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configElearning.put("streams", new JsonObject().put("elearning", "data://sharing-cities-dsm/elearning"));
		configElearning.put("hyperty", "123");
		configElearning.put("stream", streamAddress);
		DeploymentOptions optionsElearning = new DeploymentOptions().setConfig(configElearning).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(ElearningRatingHyperty.class.getName(), optionsElearning, context.succeeding());

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
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("userURL", userID)));

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

		new Thread(() -> { // insert entry in "rates"
			JsonObject document = new JsonObject();
			document.put("user", userID);
			document.put("checkin", new JsonArray());
			document.put("user-activity", new JsonArray());
			document.put("elearning", new JsonArray());
			mongoClient.insert(ratesCollection, document, res -> {
				System.out.println("Setup complete - rates");
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

		// remove from rates
		JsonObject query = new JsonObject();
		query.put("user", userID);
		mongoClient.removeDocument(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			testContext.completeNow();
		});

	}

	@Test
	void correctQuizz(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - correct quizz");
		JsonObject message = new JsonObject();
		JsonArray answers = new JsonArray().add(2).add(2).add(2);
		message.put("identity", new JsonObject());
		message.put("userID", userID);
		//
		message.put("id", "Energias Renováveis");
		message.put("date", "2018-05-24");
		message.put("answers", answers);
		//
		JsonArray toSend = new JsonArray();
		toSend.add(message);
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
			JsonArray quizzes = rates.getJsonArray("elearning");
			assertEquals(1, quizzes.size());
			// assertEquals(false, quizzes.getJsonObject(0).getBoolean("processed"));
			testContext.completeNow();
		});
		
		// TODO - validate tokens ?
	}

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", userID);
		vertx.eventBus().publish("token-rating", msg);
	}
}