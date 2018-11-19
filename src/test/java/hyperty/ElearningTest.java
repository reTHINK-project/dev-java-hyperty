package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import tokenRating.ElearningRatingHyperty;
import walletManager.WalletManagerHyperty;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
@Disabled
class ElearningTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	// mongo config
	private static MongoClient mongoClient;
	// collections
	private static String ratesCollection = "rates";
	private static String walletsCollection = "wallets";
	private static String dataobjectsCollection = "dataobjects";
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String elearningHypertyURL = "hyperty://sharing-cities-dsm/elearning";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	private static String quizzesInfoAddress = "data://sharing-cities-dsm/elearning";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		String streamAddress = "vertx://sharing-cities-dsm/elearning";
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", userID).put("guid", userID));
		JsonObject configElearning = new JsonObject();
		configElearning.put("url", elearningHypertyURL);
		configElearning.put("identity", identity);

		// mongo
		configElearning.put("db_name", "test");
		configElearning.put("collection", ratesCollection);
		configElearning.put("mongoHost", mongoHost);
		configElearning.put("mongoPorts", "27017");
		configElearning.put("mongoCluster", "NO");

		configElearning.put("tokens_per_completed_quiz", 10);
		configElearning.put("tokens_per_correct_answer", 10);
		configElearning.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configElearning.put("streams", new JsonObject().put("elearning", quizzesInfoAddress));
		configElearning.put("hyperty", "123");
		configElearning.put("stream", streamAddress);
		DeploymentOptions optionsElearning = new DeploymentOptions().setConfig(configElearning).setWorker(false);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(ElearningRatingHyperty.class.getName(), optionsElearning, context.succeeding());

		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);
		configWalletManager.put("mongoPorts", "27017");
		configWalletManager.put("mongoCluster", "NO");

		configWalletManager.put("observers", new JsonArray().add(""));

		// public wallets
		String wallet0Address = "school0-wallet";
		String wallet1Address = "school1-wallet";
		String wallet2Address = "school2-wallet";
		String school0ID = "user-guid://school-0";
		String school1ID = "user-guid://school-1";
		String school2ID = "user-guid://school-2";
		JsonObject feed0 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet0userID");
		JsonObject feed1 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet1userID");
		JsonObject feed2 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet2userID");

		// publicWallets
		JsonArray publicWallets = new JsonArray();
		JsonObject walletCause0 = new JsonObject();
		walletCause0.put("address", wallet0Address);
		walletCause0.put("identity", school0ID);
		walletCause0.put("externalFeeds", new JsonArray().add(feed0));
		publicWallets.add(walletCause0);

		JsonObject walletCause1 = new JsonObject();
		walletCause1.put("address", wallet1Address);
		walletCause1.put("identity", school1ID);
		walletCause1.put("externalFeeds", new JsonArray().add(feed1));
		publicWallets.add(walletCause1);

		JsonObject walletCause2 = new JsonObject();
		walletCause2.put("address", wallet2Address);
		walletCause2.put("identity", school2ID);
		walletCause2.put("externalFeeds", new JsonArray().add(feed2));
		publicWallets.add(walletCause2);

		configWalletManager.put("publicWallets", publicWallets);
//		configWalletManager.put("siot_stub_url", smartIotProtostubUrl);

		// pass observers
		JsonArray observers = new JsonArray();
		observers.add("");
		configWalletManager.put("observers", observers);
		configWalletManager.put("rankingTimer", 2000);

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(false);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});

		makeMongoConnection(vertx);

		vertx.eventBus().consumer(subscriptionsAddress, message -> {
			System.out.println("TEST - sub received: " + message.body().toString());
			// send reply
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(6000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));

		vertx.eventBus().send(elearningHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch setupLatch = new CountDownLatch(1);

		new Thread(() -> { // create wallet
			System.out.println("no wallet yet, creating");

			// build wallet document
			JsonObject newWallet = new JsonObject();

			String address = "walletAddress";
			newWallet.put("address", address);
			newWallet.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", 0);
			newWallet.put("bonus-credit", 0);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");

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

		checkpoint.flag();
	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("database", db_name);
		mongoconfig.put("collection", ratesCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	@AfterAll
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(3);

		// remove from rates
		JsonObject query = new JsonObject();
		query.put("user", userID);
		mongoClient.removeDocument(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			setupLatch.countDown();
		});

		// remove from wallets
		query = new JsonObject();
		query.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
		mongoClient.removeDocument(walletsCollection, query, res -> {
			System.out.println("Wallet removed from DB");
			setupLatch.countDown();
		});

		// remove from dataobjects
		query = new JsonObject();
		query.put("url", userID);
		mongoClient.removeDocument(dataobjectsCollection, query, res -> {
			System.out.println("Dataobject removed from DB");
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public Future<Integer> futureMethod() {
		Future<Integer> future = Future.future();

		JsonObject query = new JsonObject();
		mongoClient.find("elearnings", query, result -> {
			future.complete(result.result().size());
		});

		return future;
	}

	@Test
//	@Disabled
	void testFutures(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST -  futures");
		Future<Integer> numQuizzes = futureMethod();
		numQuizzes.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				System.out.println("n quizzes: " + numQuizzes.result());
			} else {
				// oh ! we have a problem...
			}
			testContext.completeNow();
		});

	}

	@Test
//	@Disabled
	void correctQuizz(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - correct quizz");
		JsonObject message = new JsonObject();
		JsonArray answers = new JsonArray().add(2).add(2).add(2);
		message.put("identity", new JsonObject());
		message.put("userID", userID);
		message.put("id", "Energias Renováveis");
		message.put("date", "2018-05-24");
		message.put("answers", answers);
		//
		JsonArray toSend = new JsonArray();
		toSend.add(message);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// check if quiz is valid
		JsonObject query = new JsonObject().put("user", userID);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray quizzes = rates.getJsonArray("elearning");
			assertEquals(1, quizzes.size());
			testContext.completeNow();
		});
	}

	@Test
//	@Disabled
	void getQuizzesInfo(VertxTestContext testContext, Vertx vertx) {

		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(quizzesInfoAddress, config, message -> {
			// assert reply not null
			JsonObject quizzes = (JsonObject) message.result().body();
			assertNotNull(quizzes);
			testContext.completeNow();
		});
	}

	void tearDownStream(VertxTestContext testContext, Vertx vertx) {
		JsonObject msg = new JsonObject();
		msg.put("type", "delete");
		msg.put("from", userID);
		vertx.eventBus().publish("token-rating", msg);
	}
}
