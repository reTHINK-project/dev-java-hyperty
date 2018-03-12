package test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import token_rating.CheckInRatingHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@DisplayName("Test multiple @BeforeEach methods")
class CheckInTest {

	private static String locationHypertyURL;
	private static String locationHypertyIdentity;
	private String locationStreamAddress = "ctxt://sharing-cities-dsm/shops-location";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		locationHypertyURL = "school://sharing-cities-dsm/location-url";
		locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);
		
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());
		System.out.println("Deployed");
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		checkpoint.flag();
	}


	@Test
	void getStoreLocations(VertxTestContext testContext, Vertx vertx) {
		System.out.println("Sending message");
		
		JsonObject config = new JsonObject().put("type", "other");
		vertx.eventBus().send(locationStreamAddress, config, message -> {
			// assert reply not null
			JsonArray locations = (JsonArray) message.result().body();
			testContext.completeNow();

		});

	}
}