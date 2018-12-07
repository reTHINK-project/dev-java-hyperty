package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

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

@ExtendWith(VertxExtension.class)
@Disabled
class BonusTest {

	private static final String log = "[BonusTest] ";

	private static MongoClient mongoClient;
	private static String walletsCollection = "wallets";
	private static String bonusCollection = "bonus";
	private static String db_name = "test";

	// schools
	private static String cause0ID = "0";
	private static String cause1ID = "1";
	private static String cause2ID = "2";

	// bonus
	private static String storeID = "storeID";
	private static String bonusID = "bonusID";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {
		Checkpoint checkpoint = context.checkpoint();
		System.out.println(log + "before");
		makeMongoConnection(vertx);

		// wait for Mongo connection to take place
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		CountDownLatch setupLatch = new CountDownLatch(3);

		new Thread(() -> {
			// add bonus
			JsonObject document = new JsonObject();
			document.put("id", bonusID);
			document.put("name", "bonus name");
			document.put("description", "bonus description");
			document.put("cost", 0);
			JsonObject constraints = new JsonObject().put("times", 1).put("period", "day");
			document.put("start", "2000/08/01");
			document.put("expires", "2000/08/30");
			document.put("constraints", constraints);
			document.put("spotID", storeID);
			mongoClient.insert(bonusCollection, document, res -> {
				System.out.println("Setup complete - bonus");
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> { // create wallets

			// build wallet document
			JsonObject newWallet = new JsonObject();

			String address = "walletAddress";
			newWallet.put("address", address);
			JsonObject info = new JsonObject().put("cause", cause0ID);
			newWallet.put("profile", new JsonObject().put("cause", cause0ID).put("code",
					new JsonObject().put("guid", "123").put("info", info)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", 10);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");
			newWallet.put("ranking", 0);

			JsonObject document = new JsonObject(newWallet.toString());

			mongoClient.save(walletsCollection, document, id -> {
				System.out.println("New wallet with ID:" + id);
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> { // create wallets

			// build wallet document
			JsonObject newWallet = new JsonObject();

			String address = "walletAddress";
			newWallet.put("address", address);
			JsonObject info = new JsonObject().put("cause", cause1ID);
			newWallet.put("profile", new JsonObject().put("cause", cause1ID).put("code",
					new JsonObject().put("guid", "123").put("info", info)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", 20);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");
			newWallet.put("ranking", 0);

			JsonObject document = new JsonObject(newWallet.toString());

			mongoClient.save(walletsCollection, document, id -> {
				System.out.println("New wallet with ID:" + id);
				setupLatch.countDown();
			});
		}).start();

		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject config = new JsonObject();
		JsonArray causes = new JsonArray();
		causes.add(new JsonObject().put("cause", cause0ID));
		causes.add(new JsonObject().put("cause", cause1ID));
		causes.add(new JsonObject().put("cause", cause2ID));
		config.put("causes", causes);
		config.put("bonusID", bonusID);
		DeploymentOptions options = new DeploymentOptions().setConfig(config).setWorker(false);
		vertx.deployVerticle(BonusScript.class.getName(), options, context.succeeding());
		System.out.println(log + "deployed");

		checkpoint.flag();
	}

	@AfterAll
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(2);

		JsonObject query = new JsonObject();

		// remove from wallets
		query = new JsonObject();
		mongoClient.removeDocuments(walletsCollection, query, res -> {
			System.out.println("Wallets removed from DB");
			setupLatch.countDown();
		});

		// remove from bonus
		query = new JsonObject();
		mongoClient.removeDocuments(bonusCollection, query, res -> {
			System.out.println("Bonuses removed from DB");
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("database", db_name);
		mongoconfig.put("collection", walletsCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	@Test
	public void testRunScript(VertxTestContext context, Vertx vertx) {
		System.out.println(log + "testRunScript()");

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// update bonus
		JsonObject query = new JsonObject().put("id", bonusID);
		mongoClient.find(bonusCollection, query, res -> {
			JsonArray bonuses = new JsonArray(res.result());
			JsonObject bonus = bonuses.getJsonObject(0);
			assertEquals(cause1ID, bonus.getString("cause"));
			assertEquals(0, (int) bonus.getInteger("cost"));
			Date currentDate = new Date();
			SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
			String start = format.format(new Date());
			// expires date
			int noOfDays = 7;
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentDate);
			calendar.add(Calendar.DAY_OF_YEAR, noOfDays);
			String expires = format.format(calendar.getTime());
			assertEquals(start, bonus.getString("start"));
			assertEquals(expires, bonus.getString("expires"));
			context.completeNow();
		});
	}
}