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
import token_rating.CheckInRatingHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class CheckInTest {

	private static String locationHypertyURL;
	private String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";
	private static String from = "tester";
	private static String userURL = "user://sharing-cities-dsm/location-identity";
	private static JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", userURL));
	private static String ratesCollection = "rates";
	private static String shopsCollection = "shops";
	private static MongoClient mongoClient;
	private static String userID = "test-userID";
	private static String storeID = "test-shopID";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		locationHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", identity);
		config.put("tokens_per_checkin", 10);
		config.put("checkin_radius", 500);
		config.put("min_frequency", 1);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());

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
		vertx.eventBus().publish("token-rating", msg);

		// insert entry in "rates"
		JsonObject document = new JsonObject();
		document.put("user", userID);
		document.put("checkin", new JsonArray());
		mongoClient.insert(ratesCollection, document, res -> {
			System.out.println("Setup complete - rates");
		});

		// add shops
		document = new JsonObject();
		document.put("id", storeID);
		JsonObject storeLocation = new JsonObject();
		storeLocation.put("degrees-latitude", 40);
		storeLocation.put("degrees-longitude", 50);
		document.put("location", storeLocation);
		mongoClient.insert(shopsCollection, document, res -> {
			System.out.println("Setup complete - shops");
		});

		// wait for handler setup
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		checkpoint.flag();
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", "test")
				.put("database", "test").put("collection", ratesCollection);
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

		// remove from shops
		query = new JsonObject();
		query.put("id", storeID);
		mongoClient.removeDocument(shopsCollection, query, res -> {
			System.out.println("Store removed from DB");
			testContext.completeNow();
		});

	}

	@Test
	void userCloseToShop(VertxTestContext testContext, Vertx vertx) {
		System.out.println("User close to shop");
		JsonObject checkInMessage = new JsonObject().put("latitude", 40.1).put("longitude", 50);
		checkInMessage.put("userID", userID);
		checkInMessage.put("shopID", storeID);
		vertx.eventBus().publish(from, checkInMessage);

		// wait for op
		try {
			Thread.sleep(8000);
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

	// TODO
	void userNotInAnyShop(VertxTestContext testContext, Vertx vertx) {
		JsonObject userLocation = new JsonObject().put("latitude", -40).put("longitude", 50);
		userLocation.put("userID", userID);
		userLocation.put("shopID", storeID);
		vertx.eventBus().publish(from, userLocation);
		testContext.completeNow();
	}

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", from);
		vertx.eventBus().publish("token-rating", msg);
	}

	@Test
	void getStoreLocations(VertxTestContext testContext, Vertx vertx) {

		JsonObject config = new JsonObject().put("type", "other");
		vertx.eventBus().send(shopsInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonArray locations = (JsonArray) message.result().body();
			testContext.completeNow();
		});
	}
}