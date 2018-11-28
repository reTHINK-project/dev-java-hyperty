package hyperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import tokenRating.CheckInRatingHyperty;
import walletManager.WalletManagerHyperty;

@ExtendWith(VertxExtension.class)
@Disabled
class EcommerceTest {

	private static String userID = "test-userID";
	private static String subscriptionsAddress = userID + "/subscription";
	private static String changesAddress = userID + "/changes";
	private static String checkinHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
	private static String shopsInfoStreamAddress = "data://sharing-cities-dsm/shops";
	private static String bonusInfoStreamAddress = "data://sharing-cities-dsm/bonus";
	private static String from = "tester";

	private static String storeID = "test-shopID";
	private static String bonusID = "test-bonusID";
	private static String bonusExpensiveID = "test-bonusExpensiveID";
	private static String bonusUnavailableID = "test-bonusUnavailable";

	// mongo config
	private static MongoClient mongoClient;
	private static String ratesCollection = "rates";
	private static String shopsCollection = "shops";
	private static String bonusCollection = "bonus";
	private static String walletsCollection = "wallets";
	private static String dataobjectsCollection = "dataobjects";
	private static String db_name = "test";
	private static String mongoHost = "localhost";
	private static String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
	private static int walletInitialBalance = 30;
	private static int walletInitialBonusCredit = 30;
	private static int itemCost = 10;
	private static int itemCostExpensive = 100;

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", userID).put("guid", userID));
		JsonObject config = new JsonObject();
		config.put("url", checkinHypertyURL);
		config.put("identity", identity);
		config.put("tokens_per_checkin", 10);
		config.put("checkin_radius", 500);
		config.put("min_frequency", 1);
		config.put("hyperty", "123");
		config.put("stream", "token-rating");
		config.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		config.put("streams",
				new JsonObject().put("shops", shopsInfoStreamAddress).put("bonus", bonusInfoStreamAddress));
		// mongo
		config.put("collection", ratesCollection);
		config.put("db_name", db_name);
		config.put("mongoHost", mongoHost);

		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(false);
		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsLocation, context.succeeding());

		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);
		configWalletManager.put("rankingTimer", 20000);

		configWalletManager.put("observers", new JsonArray().add(""));

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

		vertx.eventBus().send(checkinHypertyURL, msg, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch setupLatch = new CountDownLatch(5);

		new Thread(() -> { // create wallet
			System.out.println("no wallet yet, creating");

			// build wallet document
			JsonObject newWallet = new JsonObject();

			String address = "walletAddress";
			newWallet.put("address", address);
			newWallet.put("identity", new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			newWallet.put("created", new Date().getTime());
			newWallet.put("balance", walletInitialBalance);
			newWallet.put("transactions", new JsonArray());
			newWallet.put("status", "active");
			newWallet.put("ranking", 0);
			newWallet.put("bonus-credit", walletInitialBonusCredit);

			JsonObject document = new JsonObject(newWallet.toString());

			mongoClient.save(walletsCollection, document, id -> {
				System.out.println("New wallet with ID:" + id);
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> {
			// add shop
			JsonObject document = new JsonObject();
			document.put("id", storeID);
			JsonObject storeLocation = new JsonObject();
			storeLocation.put("degrees-latitude", 40);
			storeLocation.put("degrees-longitude", 50);
			document.put("location", storeLocation);
			mongoClient.insert(shopsCollection, document, res -> {
				System.out.println("Setup complete - shops");
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> {
			// add bonus 1
			JsonObject document = new JsonObject();
			document.put("id", bonusID);
			document.put("name", "bonus name");
			document.put("description", "bonus description");
			document.put("cost", itemCost);
			Date startDate = null;
			Date expiresDate = null;
			try {
				startDate = new SimpleDateFormat("yyyy/MM/dd").parse("2000/08/01");
				expiresDate = new SimpleDateFormat("yyyy/MM/dd").parse("3000/08/30");
				System.out.println("DATES" + startDate.toString());
			} catch (ParseException e) {
				e.printStackTrace();
			}
			// this item can be consumed twice a day
			JsonObject constraints = new JsonObject().put("times", 1).put("period", "day");
//			document.put("start", startDate.getTime());
//			document.put("expires", expiresDate.getTime());
			document.put("start", "2000/08/01");
			document.put("expires", "3000/08/30");
			document.put("constraints", constraints);
			document.put("spotID", storeID);
			mongoClient.insert(bonusCollection, document, res -> {
				System.out.println("Setup complete - bonus");
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> {
			// add bonus 2
			JsonObject document = new JsonObject();
			document.put("id", bonusExpensiveID);
			document.put("name", "bonus name");
			document.put("description", "bonus description");
			document.put("cost", itemCostExpensive);
			Date startDate = null;
			Date expiresDate = null;
			try {
				startDate = new SimpleDateFormat("yyyy/MM/dd").parse("2000/08/01");
				expiresDate = new SimpleDateFormat("yyyy/MM/dd").parse("3000/08/30");
				System.out.println("DATES" + startDate.toString());
			} catch (ParseException e) {
				e.printStackTrace();
			}
			// this item can be consumed twice a day
			JsonObject constraints = new JsonObject().put("times", 1).put("period", "day");
			document.put("start", "2000/08/01");
			document.put("expires", "3000/08/30");
			document.put("constraints", constraints);
			document.put("spotID", storeID);
			mongoClient.insert(bonusCollection, document, res -> {
				System.out.println("Setup complete - bonus");
				setupLatch.countDown();
			});
		}).start();

		new Thread(() -> {
			// add bonus 3
			JsonObject document = new JsonObject();
			document.put("id", bonusUnavailableID);
			document.put("name", "bonus name");
			document.put("description", "bonus description");
			document.put("cost", 123);
			document.put("start", "3000/08/01");
			document.put("expires", "3000/08/30");
			document.put("spotID", storeID);
			mongoClient.insert(bonusCollection, document, res -> {
				System.out.println("Setup complete - bonus");
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

		CountDownLatch setupLatch = new CountDownLatch(7);

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

		// remove from shops
		query = new JsonObject();
		query.put("id", storeID);
		mongoClient.removeDocument(shopsCollection, query, res -> {
			System.out.println("Store removed from DB");
			setupLatch.countDown();
		});

		// remove from bonus 1
		query = new JsonObject();
		query.put("id", bonusID);
		mongoClient.removeDocument(bonusCollection, query, res -> {
			System.out.println("Bonus removed from DB");
			setupLatch.countDown();
		});
		// remove from bonus 2
		query = new JsonObject();
		query.put("id", bonusExpensiveID);
		mongoClient.removeDocument(bonusCollection, query, res -> {
			System.out.println("Bonus removed from DB");
			setupLatch.countDown();
		});
		// remove from bonus 3
		query = new JsonObject();
		query.put("id", bonusUnavailableID);
		mongoClient.removeDocument(bonusCollection, query, res -> {
			System.out.println("Bonus removed from DB");
			setupLatch.countDown();
		});

		try {
			setupLatch.await();
			testContext.completeNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	@Test
	void collectBonus1(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - collectBonus1()");
		// bonusID
		JsonObject bonus = new JsonObject().put("name", "bonus").put("value", bonusID);
		JsonObject shop = new JsonObject().put("name", "checkin").put("value", storeID);

		JsonArray toSend = new JsonArray();
		toSend.add(bonus);
		toSend.add(shop);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch assertions = new CountDownLatch(2);

		new Thread(() -> {

			// check if rate was added to user
			JsonObject query = new JsonObject().put("user", userID);
			mongoClient.find(ratesCollection, query, result -> {
				JsonObject rates = result.result().get(0);
				JsonArray checkIns = rates.getJsonArray("checkin");
				assertEquals(1, checkIns.size());
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		new Thread(() -> {

			// check wallet
			JsonObject query = new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			mongoClient.find(walletsCollection, query, result -> {
				JsonObject wallet = result.result().get(0);
				// check bonusCredit
				int bonusCredit = wallet.getInteger("bonus-credit");
				assertEquals(walletInitialBonusCredit - itemCost, bonusCredit);
				// balance
				int balance = wallet.getInteger("balance");
				assertEquals(walletInitialBalance, balance);
				// transactions
				JsonArray transactions = wallet.getJsonArray("transactions");
				// size
				assertEquals(1, transactions.size());
				JsonObject transaction = transactions.getJsonObject(0);
				// is bonus
				assertEquals(true, transaction.getBoolean("bonus"));
				// is valid
				assertEquals("valid", transaction.getString("description"));
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		try {
			assertions.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	void collectBonus2(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - collectBonus2()");
		// bonusID
		JsonObject bonus = new JsonObject().put("name", "bonus").put("value", bonusID);
		JsonObject shop = new JsonObject().put("name", "checkin").put("value", storeID);

		JsonArray toSend = new JsonArray();
		toSend.add(bonus);
		toSend.add(shop);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch assertions = new CountDownLatch(2);

		new Thread(() -> {

			// check if rate was added to user
			JsonObject query = new JsonObject().put("user", userID);
			mongoClient.find(ratesCollection, query, result -> {
				JsonObject rates = result.result().get(0);
				JsonArray checkIns = rates.getJsonArray("checkin");
				assertEquals(2, checkIns.size());
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		new Thread(() -> {

			JsonObject query = new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			mongoClient.find(walletsCollection, query, result -> {
				JsonObject wallet = result.result().get(0);
				// bonus-credit
				int bonusCredit = wallet.getInteger("bonus-credit");
				assertEquals(walletInitialBonusCredit - itemCost, bonusCredit);
				// balance
				int balance = wallet.getInteger("balance");
				assertEquals(walletInitialBalance, balance);
				// transactions
				JsonArray transactions = wallet.getJsonArray("transactions");
				// size
				assertEquals(2, transactions.size());
				JsonObject transaction = transactions.getJsonObject(1);
				// is bonus
				assertEquals(true, transaction.getBoolean("bonus"));
				// is invalid-failed-constraints
				assertEquals("invalid-failed-constraints", transaction.getString("description"));
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		try {
			assertions.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	void collectBonus3(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - collectBonus3()");
		// bonusID
		JsonObject bonus = new JsonObject().put("name", "bonus").put("value", bonusExpensiveID);
		JsonObject shop = new JsonObject().put("name", "checkin").put("value", storeID);

		JsonArray toSend = new JsonArray();
		toSend.add(bonus);
		toSend.add(shop);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch assertions = new CountDownLatch(2);

		new Thread(() -> {

			// check if rate was added to user
			JsonObject query = new JsonObject().put("user", userID);
			mongoClient.find(ratesCollection, query, result -> {
				JsonObject rates = result.result().get(0);
				JsonArray checkIns = rates.getJsonArray("checkin");
				assertEquals(3, checkIns.size());
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		new Thread(() -> {

			JsonObject query = new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			mongoClient.find(walletsCollection, query, result -> {
				JsonObject wallet = result.result().get(0);
				// bonus-credit
				int bonusCredit = wallet.getInteger("bonus-credit");
				assertEquals(walletInitialBonusCredit - itemCost, bonusCredit);
				// balance
				int balance = wallet.getInteger("balance");
				assertEquals(walletInitialBalance, balance);
				// transactions
				JsonArray transactions = wallet.getJsonArray("transactions");
				// size
				assertEquals(3, transactions.size());
				JsonObject transaction = transactions.getJsonObject(2);
				// is bonus
				assertEquals(true, transaction.getBoolean("bonus"));
				// is invalid-constraints
				assertEquals("invalid-insufficient-credits", transaction.getString("description"));
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		try {
			assertions.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	void collectBonus4(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - collectBonus4()");
		// bonusID
		JsonObject bonus = new JsonObject().put("name", "bonus").put("value", bonusUnavailableID);
		JsonObject shop = new JsonObject().put("name", "checkin").put("value", storeID);

		JsonArray toSend = new JsonArray();
		toSend.add(bonus);
		toSend.add(shop);
		vertx.eventBus().send(changesAddress, toSend, reply -> {
			System.out.println("REP: " + reply.toString());
		});

		// wait for op
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		CountDownLatch assertions = new CountDownLatch(2);

		new Thread(() -> {

			// check if rate was added to user
			JsonObject query = new JsonObject().put("user", userID);
			mongoClient.find(ratesCollection, query, result -> {
				JsonObject rates = result.result().get(0);
				JsonArray checkIns = rates.getJsonArray("checkin");
				assertEquals(4, checkIns.size());
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		new Thread(() -> {

			// check wallet
			JsonObject query = new JsonObject().put("identity",
					new JsonObject().put("userProfile", new JsonObject().put("guid", userID)));
			mongoClient.find(walletsCollection, query, result -> {
				JsonObject wallet = result.result().get(0);
				// check bonus-credit
				int bonusCredit = wallet.getInteger("bonus-credit");
				assertEquals(walletInitialBonusCredit - itemCost, bonusCredit);
				// balance
				int balance = wallet.getInteger("balance");
				assertEquals(walletInitialBalance, balance);
				// transactions
				JsonArray transactions = wallet.getJsonArray("transactions");
				// size
				assertEquals(4, transactions.size());
				JsonObject transaction = transactions.getJsonObject(3);
				// is bonus
				assertEquals(true, transaction.getBoolean("bonus"));
				// is invalid
				assertEquals("invalid-not-available", transaction.getString("description"));
				testContext.completeNow();
				assertions.countDown();
			});
		}).start();

		try {
			assertions.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		testContext.completeNow();

	}

	@Test
	void getBonus(VertxTestContext testContext, Vertx vertx) {
		System.out.println("TEST - get bonus");
		JsonObject config = new JsonObject().put("type", "read");
		vertx.eventBus().send(bonusInfoStreamAddress, config, message -> {
			// assert reply not null
			JsonObject bonus = (JsonObject) message.result().body();
			System.out.println("BONUS: " + bonus);
			testContext.completeNow();
		});
	}
}