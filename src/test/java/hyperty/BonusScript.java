package hyperty;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class BonusScript extends AbstractVerticle {

	// mongo
	private String mongoHosts = "localhost";
	private String mongoPorts = "27017";
	private MongoClient mongoClient = null;
	private static String bonusCollection = "bonus";

	private CountDownLatch findWalletByCause;
	private JsonArray walletsFound;
	private static String winningCause = "";

	public static void main(String[] args) {

//		Consumer<Vertx> runner = vertx -> {
//			BonusScript runScript = new BonusScript();
//			vertx.deployVerticle(runScript);
//		};

//		final ClusterManager mgr = new HazelcastClusterManager();
//		final VertxOptions vertxOptions = new VertxOptions().setClustered(true).setClusterManager(mgr);

//		Vertx.clusteredVertx(vertxOptions, res -> {
//			Vertx vertx = res.result();
//			runner.accept(vertx);
//		});

	}

	public void start() throws Exception {

//		try {
//			
//			String envHosts = System.getenv("MONGOHOSTS");
//			String envPorts = System.getenv("MONGOPORTS");
//			if (envHosts != null && envPorts != null) {
//				System.out.println("env MONGOHOSTS" + System.getenv("MONGOHOSTS"));
//				System.out.println("env MONGOPORTS" + System.getenv("MONGOPORTS"));
//				mongoHosts = System.getenv("MONGOHOSTS");
//				mongoPorts = System.getenv("MONGOPORTS");
//			}
//			
//		} catch(Exception e) {
//			e.printStackTrace();
//		}

		if (mongoHosts != null && mongoPorts != null) {
			System.out.println("Setting up Mongo to:" + mongoHosts);

			JsonArray hosts = new JsonArray();

			String[] hostsEnv = mongoHosts.split(",");
			String[] portsEnv = mongoPorts.split(",");

			for (int i = 0; i < hostsEnv.length; i++) {
				hosts.add(new JsonObject().put("host", hostsEnv[i]).put("port", Integer.parseInt(portsEnv[i])));
				System.out.println("added to config:" + hostsEnv[i] + ":" + portsEnv[i]);
			}

//			final JsonObject mongoconfig = new JsonObject().put("replicaSet", "testeMongo").put("db_name", "test").put("hosts", hosts);

//			System.out.println("Setting up Mongo with cfg on START:" +  mongoconfig.toString());
			final String uri = "mongodb://" + "localhost" + ":27017";

			final JsonObject mongoconfig = new JsonObject();
			mongoconfig.put("connection_string", uri);
			mongoconfig.put("db_name", "test");
			mongoconfig.put("database", "test");
			mongoClient = MongoClient.createShared(vertx, mongoconfig);

			JsonArray causes = config().getJsonArray("causes");

			JsonArray results = new JsonArray();

			for (int i = 0; i < causes.size(); i++) {

				String cause = causes.getJsonObject(i).getString("cause");

				JsonArray wallets = findWalletsByCause(cause);
				System.out.println("Wallets for cause " + cause + " : " + wallets.toString());

				// sum wallets tokens
				int sum = 0;
				for (Object object : wallets) {
					sum += ((JsonObject) object).getInteger("balance");
				}

				// store
				results.add(new JsonObject().put("cause", cause).put("balance", sum));
			}

			System.out.println("Scores:" + results.toString());

			// check one with more points
			int mostTokens = 0;
			for (Object object : results) {
				JsonObject cause = ((JsonObject) object);
				int causeBalance = cause.getInteger("balance");
				if (causeBalance > mostTokens) {
					mostTokens = causeBalance;
					winningCause = cause.getString("cause");
				}
			}

			System.out.println("Winner:" + winningCause + ", with " + mostTokens + " points!");

			// update bonus
			JsonObject query = new JsonObject().put("id", config().getString("bonusID"));
			mongoClient.find(bonusCollection, query, res -> {
				JsonArray bonuses = new JsonArray(res.result());
				JsonObject bonus = bonuses.getJsonObject(0);
				Date currentDate = new Date();
				SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
				String start = format.format(currentDate);
				// expires date
				int noOfDays = 7;
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(currentDate);
				calendar.add(Calendar.DAY_OF_YEAR, noOfDays);
				String expires = format.format(calendar.getTime());
				bonus.put("start", start);
				bonus.put("expires", expires);
				bonus.put("cause", winningCause);
				JsonObject document = new JsonObject(bonus.toString());
				mongoClient.findOneAndReplace(bonusCollection, query, document, id -> {
					System.out.println("Bonus updated!");
				});

			});
		}

	}

	/**
	 * Get wallets for users of a certain cause.
	 * 
	 * @param cause
	 * @return
	 */
	private JsonArray findWalletsByCause(String cause) {
		walletsFound = new JsonArray();
		findWalletByCause = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find("wallets", new JsonObject().put("profile.cause", cause), res -> {
				if (res.result().size() != 0) {
					walletsFound = new JsonArray(res.result().toString());
				}
				findWalletByCause.countDown();
			});
		}).start();
		try {
			findWalletByCause.await(5L, TimeUnit.SECONDS);
			return walletsFound;
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		return walletsFound;
	}
}
