package test;

import java.io.IOException;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.gson.Gson;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import token_rating.WalletManagerHyperty;
import token_rating.WalletManagerMessage;
import util.DateUtils;

/*
 * Example of an asynchronous JUnit test for a Verticle.
 */
@ExtendWith(VertxExtension.class)
class WalletManagerTest {

	private static String walletManagerHypertyURL;
	private static String walletManagerHypertyIdentity;
	private static String userID = "identity";

	@BeforeAll
	static void before(VertxTestContext context, Vertx vertx) throws IOException {

		walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
		walletManagerHypertyIdentity = "school://sharing-cities-dsm/wallet-manager";
		JsonObject config = new JsonObject().put("url", walletManagerHypertyURL).put("identity",
				walletManagerHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);

		Checkpoint checkpoint = context.checkpoint();
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsLocation, context.succeeding());
		System.out.println("-> Wallet Manager deployed");

		// wait for Mongo connection to take place
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		checkpoint.flag();
	}

	@Test
	void createWallet(VertxTestContext testContext, Vertx vertx) {
		// create wallet message
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType(WalletManagerMessage.TYPE_CREATE);
		msg.setIdentity("identity-2");
		msg.setFrom("url");

		Gson gson = new Gson();
		vertx.eventBus().publish(walletManagerHypertyURL, gson.toJson(msg));

		// TODO wait for response
		// testContext.completeNow();
	}

	@Test
	void getWalletAddress(VertxTestContext testContext, Vertx vertx) {
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType(WalletManagerMessage.TYPE_READ);
		String body = new JsonObject().put("resource", "user").put("value", userID).toString();
		msg.setBody(body);

		Gson gson = new Gson();
		vertx.eventBus().send(walletManagerHypertyURL, gson.toJson(msg), reply -> {
			// System.out.println(reply.result().toString());
			testContext.completeNow();
		});
	}

	@Test
	void getWallet(VertxTestContext testContext, Vertx vertx) {
		WalletManagerMessage msg = new WalletManagerMessage();
		String walletAddress = "123";
		msg.setType(WalletManagerMessage.TYPE_READ);
		String body = new JsonObject().put("resource", "wallet").put("value", walletAddress).toString();
		msg.setBody(body);

		Gson gson = new Gson();
		vertx.eventBus().send(walletManagerHypertyURL, gson.toJson(msg), reply -> {
			System.out.println(reply.result().toString());
			testContext.completeNow();
		});
	}

	void deleteWallet(VertxTestContext testContext, Vertx vertx) {
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType(WalletManagerMessage.TYPE_DELETE);
		msg.setIdentity("identity");
		msg.setFrom("from");

		Gson gson = new Gson();
		vertx.eventBus().publish(walletManagerHypertyURL, gson.toJson(msg));

		// TODO wait for response
		// testContext.completeNow();
	}

	void transferToWallet(VertxTestContext testContext, Vertx vertx) {
		String walletAddress = "123";
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType(WalletManagerMessage.TYPE_CREATE);

		// create transaction object
		JsonObject transaction = new JsonObject();
		transaction.put("address", walletAddress);
		transaction.put("recipient", walletAddress);
		transaction.put("source", "source");
		transaction.put("date", DateUtils.getCurrentDateAsISO8601());
		transaction.put("value", 15);
		transaction.put("nonce", 1);
		String body = new JsonObject().put("resource", "wallet/" + walletAddress).put("value", transaction).toString();
		msg.setBody(body);

		Gson gson = new Gson();
		vertx.eventBus().publish(walletManagerHypertyURL, gson.toJson(msg));

		// TODO wait for response
		// testContext.completeNow();
	}

}