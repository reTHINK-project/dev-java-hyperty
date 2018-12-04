package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
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
//@Disabled
class ElearningTest {

	public static final String publicWalletsOnChangesAddress = "wallet://public-wallets/changes";
	public static final String publicWalletsAddress = "public-wallets";
	private static String guid = "user-guid://sharing-cities-dsm/guid";
	private static String subscriptionsAddress = guid + "/subscription";
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
	private static JsonObject profileInfo = new JsonObject().put("age", 24).put("cause", "user-guid://school-0")
			.put("balance", 50);

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		// reset DB
		makeMongoConnection(vertx);
		//tearDownDB(context, vertx);

		String streamAddress = "vertx://sharing-cities-dsm/elearning";
		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", guid).put("guid", guid));
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
		configWalletManager.put("onReadMaxTransactions", 10);
		configWalletManager.put("siot_stub_url", "");

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
		observers.add("observing");
		configWalletManager.put("observers", observers);
		configWalletManager.put("rankingTimer", 2000);

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(false);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});


		vertx.eventBus().consumer(subscriptionsAddress, message -> {
//			System.out.println("TEST - sub received: " + message.body().toString());
			// send reply
			JsonObject reply = new JsonObject().put("body", new JsonObject().put("code", 200));
			message.reply(reply);
		});

		// wait for Mongo connection to take place
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		JsonObject msg = new JsonObject();
		msg.put("type", "create");
		msg.put("from", subscriptionsAddress);
		msg.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", guid)));
		vertx.eventBus().send(elearningHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		
		JsonObject quizz = new JsonObject();
		quizz.put("type", "default");
		quizz.put("name", "Energia teste");
		quizz.put("description", "");
		quizz.put("picture", "");
		quizz.put("classification", "Nível 1");
		quizz.put("date", "2018-09-01");
		quizz.put("category", "energia");
		quizz.put("value", 65);
		
		JsonObject question1 = new JsonObject();
		question1.put("id",1);
		question1.put("question","question1?");
		question1.put("answers", new JsonArray().add("answer1").add("answer2").add("answer3"));
		question1.put("correctAnswer", 0);
		question1.put("hint","");
		JsonObject question2 = new JsonObject();
		question2.put("id",2);
		question2.put("question","question2?");
		question2.put("answers", new JsonArray().add("answer1").add("answer2").add("answer3"));
		question2.put("correctAnswer", 0);
		question2.put("hint","");
		JsonObject question3 = new JsonObject();
		question3.put("id",3);
		question3.put("question","question3?");
		question3.put("answers", new JsonArray().add("answer1").add("answer2").add("answer3"));
		question3.put("correctAnswer", 0);
		question3.put("hint","");
		
		JsonArray questions = new JsonArray().add(question1).add(question2).add(question3);
		quizz.put("questions", questions);
		
		mongoClient.insert("elearnings", quizz, result -> {
			checkpoint.flag();
		});

		// create wallet
//		Future<Void> walletCreation = Future.future();
//		msg = new JsonObject();
//		// create identity
//		JsonObject identityNow = new JsonObject().put("userProfile",
//				new JsonObject().put("userURL", userURL).put("guid", guid).put("info", profileInfo));
//		msg.put("type", "create");
//		msg.put("identity", identityNow);
//		msg.put("from", "myself");
//		vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
//			System.out.println("Received reply from wallet!: " + res.result().body().toString());
//			JsonObject newMsg = new JsonObject();
//			JsonObject body = new JsonObject().put("code", 200);
//			newMsg.put("body", body);
//			res.result().reply(newMsg);
//			walletCreation.complete();
//		});

//		walletCreation.setHandler(asyncResult -> {
		
//		});

	}

	static void makeMongoConnection(Vertx vertx) {

		final String uri = "mongodb://" + "localhost" + ":27017";

		final JsonObject mongoconfig = new JsonObject();
		mongoconfig.put("connection_string", uri);
		mongoconfig.put("db_name", db_name);
		mongoconfig.put("collection", ratesCollection);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);
		
		
	}

	@AfterAll
	//@Disabled
	static void tearDownDB(VertxTestContext testContext, Vertx vertx) {

		CountDownLatch setupLatch = new CountDownLatch(3);

		// remove from rates
		JsonObject query = new JsonObject();
//		query.put("user", guid);
		mongoClient.removeDocuments(ratesCollection, query, res -> {
			System.out.println("Rates removed from DB");
			setupLatch.countDown();
		});

		// remove from wallets
		query = new JsonObject();
//		query.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", guid)));
		mongoClient.removeDocuments(walletsCollection, query, res -> {
			System.out.println("Wallets removed from DB");
			setupLatch.countDown();
		});

		// remove from dataobjects
		query = new JsonObject();
//		query.put("url", guid);
		mongoClient.removeDocuments(dataobjectsCollection, query, res -> {
			System.out.println("Dataobjects removed from DB");
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	int numWallets = 200;
	int numWalletsLoop = 3;
	int created = 0;
	int resumed = 0;
	int numQuizzes = 1;
	int transactions = 0;

	@Test
//	@Disabled
	void createExtraWallets(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - createExtraWallets");

		//		createWallet(vertx, guid, null);

		JsonObject msg1 = new JsonObject();
		JsonObject identityNow = new JsonObject().put("userProfile",
				new JsonObject().put("guid", guid).put("info", profileInfo));
		msg1.put("type", "create");
		msg1.put("identity", identityNow);
		msg1.put("from", "myself");
			vertx.eventBus().send(walletManagerHypertyURL, msg1, res -> {
//			System.out.println("Received reply from wallet!: " + res.result().body().toString());
				JsonObject newMsg = new JsonObject();
				JsonObject body = new JsonObject().put("code", 200);
				newMsg.put("body", body);
				res.result().reply(newMsg);
			});


		// create wallets
		for (int i = 0; i < numWalletsLoop; i++) {


			createMany(testContext, vertx, false, i*numWalletsLoop);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		long startTime = System.currentTimeMillis();
		Future<Void> checkQuizzes = Future.future();
		transactions = 0;

			for (int i = 0; i < numQuizzes; i++) {
//				String userID = "user-guid://sharing-cities-dsm/" + i;
				vertx.eventBus().consumer(publicWalletsOnChangesAddress, message -> {
					++transactions;
					if (transactions == numQuizzes) {
						long endTime = System.currentTimeMillis();
						long timeElapsed = endTime - startTime;
						System.out.println("\nQuizzes time: " + timeElapsed + " ms" + " - Num Quizzes: " + transactions);
						checkQuizzes.complete();
					}
				});
				submitQuiz(guid, vertx);
			}

		checkQuizzes.setHandler(asyncResult -> {
				Future<Void> assertWallet = Future.future();
//				String userID = "sharing-cities-dsm/" + i;
				JsonObject query = new JsonObject().put("address", publicWalletsAddress);
				checkWallet(query, assertWallet, 0);

		testContext.completeNow();

		});


	}

	@Test
	@Disabled
	void createWallets(VertxTestContext testContext, Vertx vertx) {
		createMany( testContext,  vertx, true, 0);

	}

	void createMany(VertxTestContext testContext, Vertx vertx, boolean test, int n) {
		created = 0;
		long startTime = System.currentTimeMillis();
		Future<Void> checkNumWallets = Future.future();
		vertx.eventBus().consumer("observing", message -> {
			++created;
			if (created == numWallets) {
				long endTime = System.currentTimeMillis();
				long timeElapsed = endTime - startTime;
				System.out.println("\nCreate time: " + timeElapsed + " ms" + " - Num wallets: " + created);
				checkNumWallets.complete();
			}
		});

		// create wallets
		for (int i = n; i < n+numWallets; i++) {
			String userID = "user-guid://sharing-cities-dsm/" + i;
			createWallet(vertx, userID, null);
		}

		Future<Void> resumedWallets = Future.future();
		checkNumWallets.setHandler(asyncResult -> {
			resumed = 0;
			long startTimeResume = System.currentTimeMillis();
			for (int i = n; i < numWallets+n; i++) {
				String userID = "user-guid://sharing-cities-dsm/" + i;
				createWallet(vertx, userID, res -> {
					++resumed;
					JsonObject newMsg = new JsonObject();
					JsonObject body = new JsonObject().put("code", 200);
					newMsg.put("body", body);
					res.result().reply(newMsg);
					if (resumed == numWallets) {
						long endTime = System.currentTimeMillis();
						long timeElapsed = endTime - startTimeResume;
						System.out.println("Resume time: " + timeElapsed + " ms" + " - Num wallets: " + resumed);
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						resumedWallets.complete();

					}
				});
			}
		});

		resumedWallets.setHandler(asyncResult -> {
			
//			for (int i = 0; i < numWallets; i++) {
//				String userID = "user-guid://sharing-cities-dsm/" + i;
//				submitQuiz(userID, vertx);
//			}
/*			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}*/

//			for (int i = 0; i < numWallets; i++) {
//				Future<Void> assertWallet = Future.future();
//				String userID = "sharing-cities-dsm/" + i;
//				JsonObject query = new JsonObject().put("address", userID);
//				checkWallet(query, assertWallet);
//			}
			
			// TODO - assertion
			JsonObject query = new JsonObject().put("address", "public-wallets");
			mongoClient.find(walletsCollection, query, result -> {
				JsonObject wallet = result.result().get(0).getJsonArray("wallets").getJsonObject(0);
				JsonArray transactions = wallet.getJsonArray("transactions");
				JsonArray accounts = wallet.getJsonArray("accounts");
				// check accounts
				List<Object> res = accounts.stream()
						.filter(account -> ((JsonObject) account).getString("name").equals("created"))
						.collect(Collectors.toList());
				JsonObject accountCreated = (JsonObject) res.get(0);
				
				assertTrue((int) accountCreated.getInteger("totalBalance") >= numWallets * 50);
				assertTrue((int) accountCreated.getInteger("lastData") >= 100);
				assertTrue(transactions.size() >= numWallets );
				assertTrue((int) wallet.getInteger("balance") >= numWallets * 50 );

/*				assertEquals(numWallets * 50, (int) accountCreated.getInteger("totalBalance"));
				assertTrue((int) accountCreated.getInteger("lastData") >= 100);
				assertEquals(numWallets, transactions.size());
				assertEquals(numWallets * 50 , (int) wallet.getInteger("balance"));*/
				if (test) testContext.completeNow();
			});

		});

	}

	private void createWallet(Vertx vertx, String userID, Handler<AsyncResult<Message<Object>>> handler) {
		JsonObject msg = new JsonObject();
		JsonObject identityNow = new JsonObject().put("userProfile",
				new JsonObject().put("guid", userID).put("info", profileInfo));
		msg.put("type", "create");
		msg.put("identity", identityNow);
		msg.put("from", "myself");
		if (handler != null) {
			vertx.eventBus().send(walletManagerHypertyURL, msg, handler);
		} else {
			vertx.eventBus().send(walletManagerHypertyURL, msg, res -> {
//			System.out.println("Received reply from wallet!: " + res.result().body().toString());
				JsonObject newMsg = new JsonObject();
				JsonObject body = new JsonObject().put("code", 200);
				newMsg.put("body", body);
				res.result().reply(newMsg);
			});

		}
	}

	void submitQuiz(String userID, Vertx vertx) {
		
		JsonObject message = new JsonObject();
		JsonArray answers = new JsonArray().add(0).add(0).add(0);
		message.put("id", "Energia teste");
		message.put("date", "2018-12-04T15:03:34.145Z");
		message.put("answers", answers);
		JsonArray toSend = new JsonArray();
		toSend.add(message);
		String changesAddress = userID + "/changes";
		vertx.eventBus().consumer(changesAddress, msg -> {
			System.out.println("changesAddress" + msg.body().toString());
		});

		vertx.eventBus().send(userID + "/changes", toSend, reply -> {
			System.out.println("quiz submited: " + reply.toString());
		});
	}

	@Test
	@Disabled
	void correctQuizz(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - correct quizz");
		submitQuiz(guid, vertx);

		// wait for op
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Future<Void> assertRates = Future.future();
		JsonObject query = new JsonObject().put("user", guid);
		mongoClient.find(ratesCollection, query, result -> {
			JsonObject rates = result.result().get(0);
			JsonArray quizzes = rates.getJsonArray("elearning");
			assertEquals(1, quizzes.size());
			assertRates.complete();
		});
		Future<Void> assertWallet = Future.future();
		query = new JsonObject().put("address", "sharing-cities-dsm/0");
		checkWallet(query, assertWallet, 0);
		List<Future> futures = new ArrayList<>();
		futures.add(assertRates);
		futures.add(assertWallet);
		CompositeFuture.all(futures).setHandler(done -> {
			testContext.completeNow();
		});
	}

	private void checkWallet(JsonObject query, Future<Void> assertWallet, int walletNum) {
		mongoClient.find(walletsCollection, query, result -> {
			JsonObject wallets = result.result().get(0);
			JsonArray pubWallets = wallets.getJsonArray("wallets");
			JsonObject wallet = pubWallets.getJsonObject(walletNum);
			int balance = wallet.getInteger("balance");
			JsonArray accounts = wallet.getJsonArray("accounts");
			List<Object> res = accounts.stream()
					.filter(account -> ((JsonObject) account).getString("name").equals("elearning"))
					.collect(Collectors.toList());
			JsonObject accountElearning = (JsonObject) res.get(0);
			assertEquals(40*numQuizzes, (int) accountElearning.getInteger("totalBalance"));
			assertEquals(numQuizzes, (int) accountElearning.getInteger("lastData"));

			assertEquals((numWallets*numWalletsLoop+1)*50+40*numQuizzes, balance);
//			assertEquals(2, quizzes.size());
			assertWallet.complete();
		});
	}

	@Test
	@Disabled
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
		msg.put("from", guid);
		vertx.eventBus().publish("token-rating", msg);
	}

	}
