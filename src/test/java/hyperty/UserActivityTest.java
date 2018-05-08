package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
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
import tokenRating.CheckInRatingHyperty;
import tokenRating.UserActivityRatingHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class UserActivityTest {

	private static String activityHertyURL;
	private static String from = "user://sharing-cities-dsm/checkin-identity";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", userURL));

	private static String userID = "test-userID";
	// mongo config
	private static MongoClient mongoClient;
	private static String ratesCollection = "rates";
	private static String db_name = "test";
	private static String mongoHost = "localhost";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		activityHertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		JsonObject config = new JsonObject().put("url", activityHertyURL).put("identity", identity);
		config.put("tokens_per_checkin", 10);
		config.put("checkin_radius", 500);
		config.put("min_frequency", 1);
		config.put("hyperty", "123");
		config.put("stream", "token-rating");
		config.put("identity", identity);
		config.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		config.put("streams", new JsonObject().put("shops", "data://sharing-cities-dsm/shops"));
		// mongo
		config.put("collection", ratesCollection);
		config.put("db_name", db_name);
		config.put("mongoHost", mongoHost);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(UserActivityRatingHyperty.class.getName(), optionsLocation, context.succeeding());

		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		
		msg.put("type", "create");
		msg.put("from", from);
		JsonObject identityCheckIN  = new JsonObject().put("userProfile", new JsonObject().put("userURL", from));
		msg.put("identity", identityCheckIN);
		vertx.eventBus().publish("token-rating", msg);

		CountDownLatch setupLatch = new CountDownLatch(2);

		new Thread(() -> {
			JsonObject document = new JsonObject();
			document.put("user", userID);
			document.put("checkin", new JsonArray());
			mongoClient.insert(ratesCollection, document, res -> {
				System.out.println("Setup complete - user activity");
				setupLatch.countDown();
			});
		}).start();
		
		try {
			setupLatch.await(5L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
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
		});
	}

	@Test
	void newRating(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - new rating");
		JsonObject checkInMessage = new JsonObject().put("activity", "walking").put("distance", 50);
		checkInMessage.put("userID", userID);
		checkInMessage.put("session", "session-1");
		vertx.eventBus().publish(from, checkInMessage);

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

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", from);
		vertx.eventBus().publish("token-rating", msg);
	}
}