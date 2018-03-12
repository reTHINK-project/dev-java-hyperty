package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import rest.post.LocationHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class AbstractHypertyTest {



	private static String locationHypertyURL;
	private static String locationHypertyIdentity;

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {
		

		locationHypertyURL = "school://sharing-cities-dsm/location-url";
		locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		
		vertx.deployVerticle(LocationHyperty.class.getName(), optionsLocation, context.succeeding());
		
		checkpoint.flag();
	}

	@Test
	public void getInitialDataIdentity(VertxTestContext context, Vertx vertx) {
		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(locationHypertyURL, config, message -> {
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			String identity = body.getString("identity");
			assertEquals(locationHypertyIdentity, identity);
			
			JsonObject data = body.getJsonObject("data");
			assertNotNull(data);
			context.completeNow();
			
		});
		
	}
}