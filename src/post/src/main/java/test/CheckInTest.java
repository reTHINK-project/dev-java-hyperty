package test;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.Gson;

import static org.mockito.Mockito.*;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import token_rating.CheckInRatingHyperty;
import token_rating.TokenMessage;

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

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		locationHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		CheckInRatingHyperty hyp = mock(CheckInRatingHyperty.class);

		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());
		System.out.println("Deployed");

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		sendCreateMessage(vertx);
		checkpoint.flag();
	}

	static void sendCreateMessage(Vertx vertx) {
		TokenMessage msg = new TokenMessage();
		msg.setType("create");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}

	@Test
	void userCloseToShop(VertxTestContext testContext, Vertx vertx) {
		JsonObject checkInMessage = new JsonObject().put("latitude", 40).put("longitude", 50);
		checkInMessage.put("userID", "123");
		checkInMessage.put("shopID", "2");
		vertx.eventBus().publish(from, checkInMessage);
//		testContext.completeNow();
	}
	
	
	void userNotInAnyShop(VertxTestContext testContext, Vertx vertx) {
		JsonObject userLocation = new JsonObject().put("latitude", -40).put("longitude", 50);
		vertx.eventBus().publish(from, userLocation);
		testContext.completeNow();
	}


	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		TokenMessage msg = new TokenMessage();
		msg.setType("delete");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}

	
	void getStoreLocations(VertxTestContext testContext, Vertx vertx) {
		System.out.println("Sending message");

		JsonObject config = new JsonObject().put("type", "other");
		vertx.eventBus().send(shopsInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonArray locations = (JsonArray) message.result().body();
			System.out.println(locations);
			testContext.completeNow();
		});
	}
}