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
@Disabled
@ExtendWith(VertxExtension.class)
class RegistryTest {



	private static String testHypertyURL;
	private static JsonObject identity;
	private static String crmHypertyURL = "hyperty://sharing-cities-dsm/crm";
	private static String offlineSubMgrHypertyURL = "hyperty://sharing-cities-dsm/offline-sub-mgr";
	private static String crmStatus = crmHypertyURL + "/status";
	private static String offlineSMStatus = offlineSubMgrHypertyURL + "/status";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {
		
	


		identity  = new JsonObject().put("userProfile", new JsonObject().put("userURL", "user://sharing-cities-dsm/test"));
		testHypertyURL = "hyperty://sharing-cities-dsm/test";
		JsonObject config = new JsonObject().put("url", testHypertyURL).put("identity", identity)
											.put("collection", "registry").put("db_name", "test").put("mongoHost", "localhost")
											.put("checkStatusTimer", 60000)
											.put("CRMHypertyStatus", crmStatus)
											.put("offlineSMStatus", offlineSMStatus);
	
		
		
		
		DeploymentOptions optRegistry = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		
		vertx.deployVerticle(RegistryHyperty.class.getName(), optRegistry, context.succeeding());
		
		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		checkpoint.flag();
	}

	@Test
	public void getGuidStatus(VertxTestContext context, Vertx vertx) {
		JsonObject config = new JsonObject().put("type", "read")
				.put("from", "hyperty://hypertyurlfrom")
				.put("identity", identity)
				.put("body", new JsonObject().put("resource", "user-guid://testguid"));
	
		
		
		String statusUrl = testHypertyURL+"/status";
		vertx.eventBus().send(statusUrl, config, message -> {
			
			System.out.println("DATA returned" + message.result().body().toString());
			
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			int statusCode = body.getInteger("code");
			
			assertEquals(200, statusCode);
			

			assertNotNull(body.getJsonObject("value"));
			context.completeNow();
			
		});
		
	}
	
	@Test
	public void updateGuidStatus(VertxTestContext context, Vertx vertx) {
		
		vertx.eventBus().consumer(offlineSMStatus, message -> { 
			System.out.println("new message on->" + offlineSMStatus);
			System.out.println("message:" + message.body().toString());

			JsonObject msg = new JsonObject(message.body().toString());
			String status = msg.getJsonObject("body").getString("status");
			
			assertEquals("online", status);
			context.completeNow();
		});
		
		JsonObject config = new JsonObject().put("type", "update")
				.put("from", "hyperty://hypertyurlfrom")
				.put("identity", identity)
				.put("body", new JsonObject().put("resource", "user-guid://testguid")
						.put("status", "online"));
		

		String statusUrl = testHypertyURL+"/status";
		vertx.eventBus().send(statusUrl, config, message -> {
			
			System.out.println("DATA returned" + message.result().body().toString());
			
			// assert reply contains data and identity fields
			JsonObject body = new JsonObject(message.result().body().toString());
			int statusCode = body.getInteger("code");
			
			assertEquals(200, statusCode);
			
			context.checkpoint();
			
		});
	}
}