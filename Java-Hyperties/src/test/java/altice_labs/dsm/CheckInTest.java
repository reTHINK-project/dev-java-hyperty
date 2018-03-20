package altice_labs.dsm;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import token_rating.CheckInRatingHyperty;
import token_rating.WalletManagerMessage;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class CheckInTest {

	private static String locationHypertyURL;
	private static String locationHypertyIdentity;
	private String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";
	private static String from = "tester";
	private static int msgID;
	private static String ratesCollection = "rates";
	private static MongoClient mongoClient;
	private static String userID = "123";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		locationHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();

		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());
		System.out.println("Deployed");

		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		sendCreateMessage(vertx);

		// insert entry in "rates"
		addUserEntryInRatesCollection();

		// wait for handler setup
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		checkpoint.flag();
	}

	private static void addUserEntryInRatesCollection() {
		JsonObject document = new JsonObject();
		document.put("user", userID);
		document.put("checkin", new JsonArray());
		mongoClient.insert(ratesCollection, document, res -> {
			System.out.println("Setup complete");
		});
	}

	static void sendCreateMessage(Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", from);
		vertx.eventBus().publish("token-rating", msg);
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", "test")
				.put("database", "test").put("collection", ratesCollection);

		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	@AfterAll
	static void tearDown(VertxTestContext testContext, Vertx vertx) {

		JsonObject query = new JsonObject();
		query.put("user", userID);

		mongoClient.removeDocument(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			testContext.completeNow();
		});

		testContext.completeNow();

	}

	@Test
	void userCloseToShop(VertxTestContext testContext, Vertx vertx) {
		System.out.println("User close to shop");
		JsonObject checkInMessage = new JsonObject().put("latitude", 40).put("longitude", 50);
		checkInMessage.put("userID",userID);
		checkInMessage.put("shopID", "2");
		System.out.println("Sending message");
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

	void userNotInAnyShop(VertxTestContext testContext, Vertx vertx) {
		JsonObject userLocation = new JsonObject().put("latitude", -40).put("longitude", 50);
		vertx.eventBus().publish(from, userLocation);
		testContext.completeNow();
	}

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", from);
		vertx.eventBus().publish("token-rating", msg);
	}

	void getStoreLocations(VertxTestContext testContext, Vertx vertx) {
		System.out.println("Sending message");

		JsonObject config = new JsonObject().put("type", "other");
		vertx.eventBus().send(shopsInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonArray locations = (JsonArray) message.result().body();
			testContext.completeNow();
		});
	}
}