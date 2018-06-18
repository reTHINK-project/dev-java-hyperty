package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class AbstractHypertyTest {



	private static String testHypertyURL;
	private static JsonObject identity;

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {
		
		identity  = new JsonObject().put("userProfile", new JsonObject().put("userURL", "user://sharing-cities-dsm/test"));
		testHypertyURL = "hyperty://sharing-cities-dsm/test";
		JsonObject config = new JsonObject().put("url", testHypertyURL).put("identity", identity)
											.put("collection", "location_data").put("db_name", "test").put("mongoHost", "localhost")
											.put("schemaURL", "hyperty-catalogue://catalogue.localhost/.well-known/dataschema/Context");
		config.put("streams", new JsonArray());
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		
		vertx.deployVerticle(TestHyperty.class.getName(), optionsLocation, context.succeeding());
		
		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		checkpoint.flag();
	}

	@Test
	public void getInitialDataIdentity(VertxTestContext context, Vertx vertx) {
		JsonObject config = new JsonObject().put("type", "read").put("from", "hyperty://hypertyurlfrom").put("identity", identity);
		
		
		vertx.eventBus().send(testHypertyURL, config, message -> {
			
			System.out.println("DATA returned" + message.result().body().toString());
			
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			JsonObject identityReturned = body.getJsonObject("identity");
			System.out.println("RETURNED IDENTITY:" + identityReturned.toString());
			
			assertEquals(identity, identityReturned);
			
			JsonArray data = body.getJsonArray("data");
			assertNotNull(data);
			context.completeNow();
			
		});
		
	}
}