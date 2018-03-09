package test;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import rest.post.LocationHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@RunWith(VertxUnitRunner.class)
public class JUnitVerticleTest {

	Vertx vertx;
	int port;
	private String locationHypertyURL;
	private String locationHypertyIdentity;

	@Before
	public void before(TestContext context) throws IOException {
		ServerSocket socket = new ServerSocket(0);
		port = socket.getLocalPort();
		socket.close();

		locationHypertyURL = "school://sharing-cities-dsm/location-url";
		locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		vertx = Vertx.vertx();
		vertx.deployVerticle(LocationHyperty.class.getName(), optionsLocation, context.asyncAssertSuccess());
	}

	@Test
	public void getInitialDataIdentity(TestContext context) {
		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(locationHypertyURL, config, message -> {
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			String identity = body.getString("identity");
			context.assertEquals(locationHypertyIdentity, identity);
			
			JsonObject data = body.getJsonObject("data");
			context.assertNotNull(data);
			
		});
	}
}