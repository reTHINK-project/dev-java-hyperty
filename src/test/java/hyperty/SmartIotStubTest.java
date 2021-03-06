package hyperty;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import protostub.SmartIotProtostub;
import tokenRating.EnergySavingRatingHyperty;
import tokenRating.UserActivityRatingHyperty;


/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class SmartIotStubTest {
	
	private static String smartIotProtostubUrl = "runtime://sharing-cities-dsm/protostub/smart-iot";
	private static JsonObject identity = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", "user://google.com/lduarte.suil@gmail.com").put("guid", "user-guid://test33190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"));
	private static JsonObject identity2 = new JsonObject().put("userProfile",
			new JsonObject().put("userURL", "user://google.com/lduarte.suil@gmail.com").put("guid", "user-guid://3232bf1a190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"));


	private static String mongoHost = "localhost";
	private static String SIOTurl = "https://iot.alticelabs.com/api";
	private static String pointOfContact = "https://url_contact";

	

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		Checkpoint checkpoint = context.checkpoint();	
		JsonObject configSmartIotStub= new JsonObject();
		configSmartIotStub.put("url", smartIotProtostubUrl);
		configSmartIotStub.put("db_name", "test");
		configSmartIotStub.put("collection", "siotdevices");
		configSmartIotStub.put("mongoHost", mongoHost);
		configSmartIotStub.put("smart_iot_url", SIOTurl);
		configSmartIotStub.put("point_of_contact", pointOfContact);


		DeploymentOptions optionsconfigSmartIotStub = new DeploymentOptions().setConfig(configSmartIotStub)
				.setWorker(false);
		vertx.deployVerticle(SmartIotProtostub.class.getName(), optionsconfigSmartIotStub, res -> {
			System.out.println("SmartIOTProtustub Result->" + res.result());
		});
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		/*
		// deploy user activity rating hyperty
		String userActivityHypertyURL = "hyperty://sharing-cities-dsm/user-activity";
		JsonObject configUserActivity = new JsonObject();
		configUserActivity.put("url", userActivityHypertyURL);
		configUserActivity.put("identity", identity);
		// mongo
		configUserActivity.put("db_name", "test");
		configUserActivity.put("collection", "rates");
		configUserActivity.put("mongoHost", mongoHost);

		configUserActivity.put("tokens_per_walking_km", 10);
		configUserActivity.put("tokens_per_biking_km", 10);
		configUserActivity.put("tokens_per_bikesharing_km", 10);
		configUserActivity.put("tokens_per_evehicle_km", 5);
		configUserActivity.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configUserActivity.put("hyperty", "123");
		configUserActivity.put("stream", "vertx://sharing-cities-dsm/user-activity");
		
		
		DeploymentOptions optionsUserActivity = new DeploymentOptions().setConfig(configUserActivity).setWorker(false);
		vertx.deployVerticle(UserActivityRatingHyperty.class.getName(), optionsUserActivity, res -> {
			System.out.println("UserActivityRatingHyperty Result->" + res.result());
		});
		*/
		String energySavingRatingHypertyURL = "hyperty://sharing-cities-dsm/energy-saving-rating";
		JsonObject configEnergySaving = new JsonObject();
		configEnergySaving.put("url", energySavingRatingHypertyURL);
		configEnergySaving.put("identity", identity);
		// config
		configEnergySaving.put("hyperty", "123");
		configEnergySaving.put("stream", "token-rating");
		configEnergySaving.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		// mongo
		configEnergySaving.put("collection", "rates");
		configEnergySaving.put("db_name", "test");
		configEnergySaving.put("mongoHost", mongoHost);

		DeploymentOptions optionsEnergy = new DeploymentOptions().setConfig(configEnergySaving).setWorker(false);
		vertx.deployVerticle(EnergySavingRatingHyperty.class.getName(), optionsEnergy, res -> {
			System.out.println("EnergySavingRatingHyperty Result->" + res.result());
		});
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		
		
		
		checkpoint.flag();
		
	}

	@Test
	void createDevice(VertxTestContext testContext, Vertx vertx) {
		
		
		/*
		 * 
		 * {
				"type": "create",
				"to": "runtime://sharing-cities-dsm/protostub/smart-iot",
				"from": "hyperty://localhost/15b36f88-51d1-4137-9f29-92651558bcbc",
				"identity": {
					"userProfile": {
						"userURL": "user://google.com/lduarte.suil@gmail.com",
						"guid": "user-guid://825bf1a190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"
					}
				},
				"body": {
					"resource": "device",
					"name": "device Name",
					"description": "device description"
				}
		 * 	}
		 * 
		 * 
		 * */
		
		
		System.out.println("TEST - create new Device");
		JsonObject message = new JsonObject();

		message.put("identity", identity);
		message.put("from", "hyperty://hyperty-url");
		message.put("type", "create");
		message.put("to", smartIotProtostubUrl);
		JsonObject body = new JsonObject().put("resource", "device")
				.put("name", "device Name")
				.put("description", "device description");
		message.put("body", body);

		
		vertx.eventBus().send(smartIotProtostubUrl, message, reply -> {
			System.out.println("REP: " + reply.result().body().toString());
			int code = new JsonObject(reply.result().body().toString()).getJsonObject("body").getInteger("code");
			assertTrue(200 == code);
			
			testContext.completeNow();
		});


/*
		// check if session is unprocessed
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray quizzes = rates.getJsonArray("elearning");
			assertEquals(1, quizzes.size());
			// assertEquals(false, quizzes.getJsonObject(0).getBoolean("processed"));
			
		});*/


	}

	@Test
	void createStream(VertxTestContext testContext, Vertx vertx) {
		
		
		/*
		 * 
		 * {
				"type": "create",
				"to": "runtime://sharing-cities-dsm/protostub/smart-iot",
				"from": "hyperty://localhost/15b36f88-51d1-4137-9f29-92651558bcbc",
				"identity": {
					"userProfile": {
						"userURL": "user://google.com/lduarte.suil@gmail.com",
						"guid": "user-guid://825bf1a190fea8a9ebe2a5a26aa8ae05961012dfd4abd3b8dcecf5cab63d8450"
					}
				},
				"body": {
					"resource": "stream",
					"description": "device description",
					"platformID": "edp",
					"platformUserId": "luisuserID"
				}
		 * 	}
		 * 
		 * 
		 * */
		
		
		System.out.println("TEST - create new Stream");
		JsonObject message = new JsonObject();

		message.put("identity", identity);
		message.put("from", "hyperty://hyperty-url");
		message.put("type", "create");
		message.put("to", smartIotProtostubUrl);
		JsonObject body = new JsonObject().put("resource", "stream")
				.put("name", "device Name")
				.put("description", "device description")
				.put("platformID", "edp")
				.put("platformUID", "edpLuisUSERid")
				.put("ratingType", "private");
		message.put("body", body);

		
		vertx.eventBus().send(smartIotProtostubUrl, message, reply -> {
			System.out.println("REP: " + reply.result().body().toString());
			int code = new JsonObject(reply.result().body().toString()).getJsonObject("body").getInteger("code");
			assertTrue(200 == code);
			
			testContext.completeNow();
		});


/*
		// check if session is unprocessed
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray quizzes = rates.getJsonArray("elearning");
			assertEquals(1, quizzes.size());
			// assertEquals(false, quizzes.getJsonObject(0).getBoolean("processed"));
			
		});*/


	}
	
	

}