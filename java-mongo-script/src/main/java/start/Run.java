package start;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class Run extends AbstractVerticle {

	private String mongoHosts = "localhost";
	private String mongoPorts = "27017";
	private MongoClient mongoClient = null;
	private CountDownLatch findWalletByCode;
	private CountDownLatch findWallets;
	private JsonArray walletsFound;
	private HashMap<String, Integer> report = new HashMap<>();

	public static void main(String[] args) {


		Consumer<Vertx> runner = vertx -> {
			Run runScript = new Run();
			vertx.deployVerticle(runScript);
		};
		
		final ClusterManager mgr = new HazelcastClusterManager();
		final VertxOptions vertxOptions = new VertxOptions().setClustered(true).setClusterManager(mgr);
		
		Vertx.clusteredVertx(vertxOptions, res -> {
			Vertx vertx = res.result();
			runner.accept(vertx);
		});

	}


	public void start() throws Exception {

		
		try {
			
			String envHosts = System.getenv("MONGOHOSTS");
			String envPorts = System.getenv("MONGOPORTS");
			if (envHosts != null && envPorts != null) {
				System.out.println("env MONGOHOSTS" + System.getenv("MONGOHOSTS"));
				System.out.println("env MONGOPORTS" + System.getenv("MONGOPORTS"));
				mongoHosts = System.getenv("MONGOHOSTS");
				mongoPorts = System.getenv("MONGOPORTS");
			}
			
		} catch(Exception e) {
			e.printStackTrace();
		}


		if (mongoHosts != null && mongoPorts != null) {
			System.out.println("Setting up Mongo to:" + mongoHosts);
			
			JsonArray hosts = new JsonArray();
			
			String [] hostsEnv = mongoHosts.split(",");
			String [] portsEnv = mongoPorts.split(",");
			
			for (int i = 0; i < hostsEnv.length ; i++) {
				hosts.add(new JsonObject().put("host", hostsEnv[i]).put("port", Integer.parseInt(portsEnv[i])));
				System.out.println("added to config:" + hostsEnv[i] + ":" + portsEnv[i]);
			}
			
			final JsonObject mongoconfig = new JsonObject().put("replicaSet", "testeMongo").put("db_name", "test").put("hosts", hosts);
			
			System.out.println("Setting up Mongo with cfg on START:" +  mongoconfig.toString());
			mongoClient = MongoClient.createShared(vertx, mongoconfig);
				
			
			JsonArray wallets = findWallets();
			
				
			for (int i = 0; i< wallets.size(); i++) {
				
				JsonObject wallet = wallets.getJsonObject(i);
				
				if (wallet.containsKey("profile")) {
					
					JsonObject walletProfile = wallet.getJsonObject("profile");
					
					if(walletProfile.containsKey("code")) {
						
						String code = walletProfile.getString("code");
						if(report.containsKey(code)) {
							int value = report.get(code);
							report.put(code, value+1);
						} else {
							report.put(code, 1);
						}
						
					}
				}		
			}
			
			BufferedWriter writer = new BufferedWriter(new FileWriter("resultFile"));
			BufferedWriter writerCSV = new BufferedWriter(new FileWriter("report.csv"));
			System.out.println("***Report Result***");
			writer.write("*******************************\n");
			writer.write("*        Report Result        *\n");
			writer.write("*******************************\n");
			writerCSV.write("Code,Value\n");
			for (String code: report.keySet()){
				String toWrite = code + ": " + report.get(code) + "\n";
	            System.out.println(toWrite);
	            writerCSV.write(code + "," + report.get(code) + "\n");
	            writer.write(String.format("* %-20s | %-5s*\n", code, report.get(code)));
	            //writer.write(toWrite);
			} 
			writer.write("*******************************\n");
			writer.close();
			writerCSV.close();
			vertx.close();

			
		}

	}
	
	private JsonArray findWalletsByCode(String code) {
		System.out.println("find wallets with code:" + code);
		walletsFound = null;
		findWalletByCode = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find("wallets", new JsonObject().put("profile.code", code), res -> {
				if (res.result().size() != 0) {
					walletsFound = new JsonArray(res.result().toString());
				}
				findWalletByCode.countDown();
			});

		}).start();

		try {
			findWalletByCode.await(5L, TimeUnit.SECONDS);
			return walletsFound;
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		return walletsFound;
	}
	
	private JsonArray findWallets() {
		walletsFound = null;
		findWallets= new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find("wallets", new JsonObject(), res -> {
				if (res.result().size() != 0) {
					walletsFound = new JsonArray(res.result().toString());
				}
				findWallets.countDown();
			});
		}).start();

		try {
			findWallets.await(5L, TimeUnit.SECONDS);
			return walletsFound;
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		return walletsFound;
	}
}
