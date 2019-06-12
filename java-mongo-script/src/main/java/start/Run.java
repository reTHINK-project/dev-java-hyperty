	package start;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.hazelcast.map.impl.query.Result;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import util.DateUtilsHelper;


public class Run extends AbstractVerticle {

	private String mongoHosts = "localhost";
	private String mongoPorts = "27017";
	private MongoClient mongoClient = null;
	private CountDownLatch findWalletByCode;
	private CountDownLatch findWallets;
	private JsonArray walletsFound;
	private HashMap<String, Integer> report = new HashMap<>();
	
	private final JsonArray accountsDefault = new JsonArray().add(new Account("elearning", "quizzes").toJsonObject())
			.add(new Account("walking", "km").toJsonObject()).add(new Account("biking", "km").toJsonObject())
			.add(new Account("checkin", "checkin").toJsonObject())
			.add(new Account("energy-saving", "%").toJsonObject())
			.add(new Account("created", "points").toJsonObject());

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



		
		
		
		JsonArray hosts = new JsonArray();
		hosts.add(new JsonObject().put("host", "localhost").put("port", 27017));
		
		//final String uri = "mongodb://" + "localhost" + ":27017";
		final JsonObject mongoconfig = new JsonObject().put("replicaSet", "testeMongo").put("db_name", "test").put("hosts",
				hosts);

		mongoClient = MongoClient.createShared(vertx, mongoconfig);
		
		JsonArray wallets = findWallets().getJsonObject(0).getJsonArray("wallets");
		
		
		getWalletsInfo(wallets);
		
		//fillTransactionsCollection();
		//fillTransactionsCollectionByPub();
		//updatePrivAccounts();
		//updatePubAccounts();
		
			
	}
		
	private void getWalletsInfo(JsonArray wallets) {
		for (int i = 0; i< wallets.size(); i++) {
			
			JsonObject wallet = wallets.getJsonObject(i);
			
			
			Future<JsonArray> transactionsFuture = getPubWalletTransactions(wallet.getString("address"));
			
			transactionsFuture.setHandler(transactionsResult -> {
				if (transactionsResult.succeeded()) { 
					
					System.out.println("-----------------------------------------");
					System.out.println("-----------------------------------------");
					JsonArray transactions = transactionsResult.result();	
					System.out.println("address:" + wallet.getString("address"));
					System.out.println("transactions:" + transactions.size());
					int created = 0;
					int checkin = 0;
					int elearning = 0;
					int useractivity = 0;
					int walk = 0;
					int bike = 0;
					int totalEl = 0;
						
					for (int j = 0; j < transactions.size(); j++) {
						
						JsonObject transaction = transactions.getJsonObject(j);
						String source = transaction.getString("source");
						
						if (source.equals("checkin")) {
							checkin++;
						} else if (source.equals("user-activity")) {
							useractivity++;
							JsonObject data = transaction.getJsonObject("data");
							if (data.getString("activity").equals("user_walking_context")) {
								walk++;
							} else {
								bike++;
							}
									
						} else if (source.equals("elearning")) {
							elearning++;
							totalEl = totalEl + transaction.getInteger("value");
						} else if (source.equals("created")) {
							created++;
						}
					}
					System.out.println("checkin:" + checkin);
					System.out.println("user-activity:" + useractivity);
					System.out.println("walking:" + walk);
					System.out.println("biking:" + bike);
					System.out.println("elearning:" + elearning);
					System.out.println("created:" + created);
					System.out.println("totalele:" + totalEl);

				}
			});

			/*
			System.out.println("-----------------------------------------");
			System.out.println("-----------------------------------------");
			JsonArray transactions = wallet.getJsonArray("transactions");
			System.out.println("address:" + wallet.getString("address"));
			System.out.println("transactions:" + transactions.size());
			int created = 0;
			int checkin = 0; 
			int elearning = 0;
			int totalEl = 0;
			int useractivity = 0;
				
			for (int j = 0; j < transactions.size(); j++) {
				
				JsonObject transaction = transactions.getJsonObject(j);
				String source = transaction.getString("source");
				
				if (source.equals("checkin")) {
					checkin++;
				} else if (source.equals("user-activity")) {
					useractivity++;
				} else if (source.equals("elearning")) {
					elearning++;
					totalEl = totalEl + transaction.getInteger("value");
				} else if (source.equals("created")) {
					created++;
				}
				
			}
			System.out.println("checkin:" + checkin);
			System.out.println("user-activity:" + useractivity);
			System.out.println("elearning:" + elearning);
			System.out.println("created:" + created);
			System.out.println("totalele:" + totalEl);
			
			*/
		}
	}
	
	
	private void fillTransactionsCollection() {	
		Future<JsonArray> walletsFuture = getWallets();
		walletsFuture.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				JsonArray allWallets = asyncResult.result();
				String causeWalletAddress = "wallet2bGranted";
				for (int x=0; x < allWallets.size(); x++) {	
					JsonObject wallet = allWallets.getJsonObject(x);
					String address = wallet.getString("address");
							
					// in case of private wallet
					if (!address.equals("public-wallets")) {
						String pubCause = wallet.getString(causeWalletAddress);
						String walletID = (String) wallet.getValue("_id");

						JsonArray transactions = wallet.getJsonArray("transactions");
								
						for (int y = 0; y < transactions.size(); y++) {
							
							JsonObject currentTransaction = transactions.getJsonObject(y);
							currentTransaction.put(causeWalletAddress, pubCause);
							currentTransaction.remove("recipient");
							currentTransaction.put("recipient", walletID);
							
							mongoClient.insert("transactions", currentTransaction, r -> {});					
						}							
					} 
				}
			}
		});
	}
	
	private void countTransactionsCollectionByPub() {	
		Future<JsonArray> walletsFuture = getWallets();
		walletsFuture.setHandler(asyncResult -> {
			if (asyncResult.succeeded()) {
				JsonArray allWallets = asyncResult.result();
				String causeWalletAddress = "wallet2bGranted";
				int contPriv = 0;
				int contPub = 0;
				for (int x=0; x < allWallets.size(); x++) {	
					JsonObject wallet = allWallets.getJsonObject(x);
					String address = wallet.getString("address");
							
					// in case of private wallet
					if (!address.equals("public-wallets")) {
						String pubCause = wallet.getString(causeWalletAddress);
						String walletID = (String) wallet.getValue("_id");

						JsonArray transactions = wallet.getJsonArray("transactions");
						contPriv += transactions.size();		
													
					} else {
						JsonArray pubWallets = wallet.getJsonArray("wallets");
						
						for (int z = 0; z < pubWallets.size(); z++) {
							JsonObject pubwallet = pubWallets.getJsonObject(z);
							String pubCause = pubwallet.getString("address");
							int size = pubwallet.getJsonArray("transactions").size();
							contPub += size;
						}
						
					} 
				}
				System.out.println("transactions pub" + contPub);
				System.out.println("transactions priv" + contPriv);
			}
		});
	}

	private void updatePrivAccounts() {
		Future<JsonArray> walletsFuture = getWallets();
		walletsFuture.setHandler(asyncResult -> {

			if (asyncResult.succeeded()) {
				JsonArray allWallets = asyncResult.result();

				for (int x=0; x < allWallets.size(); x++) {	
					JsonObject wallet = allWallets.getJsonObject(x);
					String address = wallet.getString("address");
							
					// in case of private wallet
					if (!address.equals("public-wallets")) {
						String walletID = (String) wallet.getValue("_id");
						System.out.println("walletid:" + walletID);
						
						Future<JsonArray> transactionsFuture = getWalletTransactions(walletID);
						transactionsFuture.setHandler(transactionsResult -> {
							System.out.println("result ("+walletID+"):" + transactionsResult.result().size());
							if (transactionsResult.succeeded()) { 
								
								JsonArray transactions = transactionsResult.result();
								
														
								JsonArray accounts = buildAccountWallet(wallet, transactions, false);
								System.out.println("accounts"+ accounts.toString());
								wallet.put("accounts", accounts);
								mongoClient.findOneAndReplace("wallets", new JsonObject().put("_id", walletID), wallet, r -> {});								
							}
						});
					}
				}
			}
		});
	}
	
	private void updatePubAccounts() {
		Future<JsonArray> pubWalletsFuture = getPubWallets();
		pubWalletsFuture.setHandler(asyncResult -> {

			if (asyncResult.succeeded()) {
				JsonObject walletDocument = asyncResult.result().getJsonObject(0);
				String walletID = (String) walletDocument.getValue("_id");
				
				JsonArray pubWallets = walletDocument.getJsonArray("wallets");

				for (int x=0; x < pubWallets.size(); x++) {	
					
					JsonObject wallet = pubWallets.getJsonObject(x);
					
					String address = wallet.getString("address");				
					Future<JsonArray> transactionsFuture = getPubWalletTransactions(address);
					
					transactionsFuture.setHandler(transactionsResult -> {
						if (transactionsResult.succeeded()) { 
							System.out.println("res");
							JsonArray transactions = transactionsResult.result();	

							JsonArray accounts = buildAccountWallet(wallet, transactions, true);
							System.out.println("accounts"+ accounts.toString());
							wallet.put("accounts", accounts);
											
							mongoClient.findOneAndReplace("wallets", new JsonObject().put("_id", walletID), walletDocument, r -> {});
						}
					});
				}	
			}
		});
	}
	

	private JsonArray buildAccountWallet(JsonObject wallet, JsonArray transactions, boolean isPubWallet) {
		// default value

		// for each activity
		// TODO - get from transactions collection
		JsonObject oldAccountsData = new JsonObject();
		if (wallet.containsKey("accounts")) {
			for (Object entry : wallet.getJsonArray("accounts")) {
				JsonObject account = (JsonObject) entry;
				String source = account.getString("name");
				
				JsonObject data = new JsonObject().put("totalBalance", account.getInteger("totalBalance"))
												.put("totalData", account.getInteger("totalData"));
				oldAccountsData.put(source, data);
			}
		}

		
		if (wallet.containsKey("accounts")) {
			wallet.remove("accounts");
		}
		
		wallet.put("accounts", accountsDefault.copy());
		JsonArray accounts = wallet.getJsonArray("accounts");

		List<String> activities = new ArrayList<>();
		activities.add("walking");
		activities.add("biking");
		activities.add("elearning");
		activities.add("checkin");
		activities.add("energy-saving");
		if(isPubWallet) {
			activities.add("created");
		}
		for (String source : activities) {
			// get transactions of that source (watch out for user-activity!)
			List<Object> transactionsForSource = getTransactionsForSource(transactions, source, false);
			// get account for source
			JsonArray accountsDefCopy = accountsDefault.copy();
			//System.out.println("[WalletManager] buildAccounts copy: " + accountsDefCopy);
			System.out.println("source" + source);
			List<Object> res = accountsDefCopy.stream()
					.filter(account -> ((JsonObject) account).getString("name").equals(source))
					.collect(Collectors.toList());
			JsonObject accountJson = (JsonObject) res.get(0);
			Account account = Account.toAccount(accountJson);
			account.totalBalance = 0;
			// sum all transactions value
			account.totalBalance = sumTransactionsField(transactionsForSource, "value");
			if (!source.equals("walking") && !source.equals("biking")) {
				account.totalData = transactionsForSource.size();
			} else {
				account.totalData = sumTransactionsField(transactionsForSource, "distance");
			}

			JsonArray lastTransactions = (account.lastPeriod.equals("month"))
					? lastMonthTransactions(transactionsForSource)
					: lastWeekTransactions(transactionsForSource);
					
			System.out.println("walletid(" + wallet.getString("address") + " - LASTSIZE" + lastTransactions.size());
			account.lastTransactions = getTransactionIds(lastTransactions);
			account.lastBalance = sumTransactionsField(lastTransactions.getList(), "value");
			if (!source.equals("walking") && !source.equals("biking")) {
				account.lastData = lastTransactions.size();
			} else {
				account.lastData = sumTransactionsField(lastTransactions.getList(), "distance");
			}
			accountJson = account.toJsonObject();
			
			for (Object entry : accounts) {
				JsonObject js = (JsonObject) entry;
				if (js.getString("name").equals(source)) {
					int totalBalance;
					int totalData;
					if (oldAccountsData.containsKey(source)) {
						totalBalance = oldAccountsData.getJsonObject(source).getInteger("totalBalance");
						totalData = oldAccountsData.getJsonObject(source).getInteger("totalData");
						accountJson.remove("totalBalance");
						accountJson.remove("totalData");
						accountJson.put("totalBalance", totalBalance);
						accountJson.put("totalData", totalData);
						System.out.println("old bal " + totalBalance + "\nold Data" + totalData + "\non on source" + source);
					} else {
						System.out.println("dont have old bal");
					}
					
					
					accounts.remove(js);
					accounts.add(accountJson);
					
					break;
				}
			}

		}

		//System.out.println("[WalletManager] buildAccounts result:" + accounts);
		return accounts;
		
	}
	
	private JsonArray getTransactionIds(JsonArray lastTransactions) {
		JsonArray ids = new JsonArray();
		for (int q = 0; q < lastTransactions.size(); q++) {
			JsonObject transaction = lastTransactions.getJsonObject(q);
			String currentID = (String) transaction.getValue("_id");
			ids.add(currentID);
			if(q==0) {
				System.out.println(transaction.getString("recipient"));
			}
			
		}
		return ids;
	}


	private List<Object> getTransactionsForSource(JsonArray transactions, String source, boolean fromLast) {
		if (fromLast) {
			int num = 100;
			JsonArray trAux = new JsonArray();
			for (int i = transactions.size() - 1; i > transactions.size() - 1 - num && i >= 0; i--) {
				trAux.add(transactions.getJsonObject(i));
			}
			transactions = trAux;
		}
		if (source.equals("walking") || source.equals("biking")) {
			String newSource = "user_" + source + "_context";
			return transactions.stream()
					.filter(transaction -> isUserActivityTransaction((JsonObject) transaction, newSource))
					.collect(Collectors.toList());
		} else {
			return transactions.stream()
					.filter(transaction -> ((JsonObject) transaction).getString("source").equals(source))
					.collect(Collectors.toList());
		}
	}
	
	private boolean isUserActivityTransaction(JsonObject transaction, String source) {
		if (!transaction.getString("source").equals("user-activity")) {
			return false;
		} else
			return transaction.getJsonObject("data").getString("activity").equals(source);
	}
	
	private int sumTransactionsField(List<Object> transactions, String field) {
		int sum = 0;
		for (Object transaction : transactions) {
			if (field.equals("distance")) {
				sum += ((JsonObject) transaction).getJsonObject("data").getInteger("distance");
			} else
				sum += ((JsonObject) transaction).getInteger(field);
		}
		return sum;
	}
	
	private JsonArray lastMonthTransactions(List<Object> transactions) {
		JsonArray lastMonth = new JsonArray();
		for (Object object : transactions) {
			JsonObject transaction = (JsonObject) object;
			if (DateUtilsHelper.isDateInCurrentMonth(DateUtilsHelper.stringToDate(transaction.getString("date")))) {
				lastMonth.add(transaction);
			}
		}
		return lastMonth;
	}
	
	private JsonArray lastWeekTransactions(List<Object> transactions) {
		JsonArray lastWeek = new JsonArray();
		for (Object object : transactions) {
			JsonObject transaction = (JsonObject) object;
			if (DateUtilsHelper.isDateInCurrentWeek(DateUtilsHelper.stringToDate(transaction.getString("date")))) {
				lastWeek.add(transaction);
			}
		}
		return lastWeek;
	}	
	
	private JsonArray findWallets() {
		walletsFound = null;
		findWallets= new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find("wallets", new JsonObject().put("address","public-wallets"), res -> {
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
	
	Future<JsonArray> getPubWallets() {
		Future<JsonArray> pubWallets = Future.future();
		mongoClient.find("wallets", new JsonObject().put("address", "public-wallets"), res -> {
			if (res.result().size() != 0) {
				pubWallets.complete(new JsonArray(res.result().toString()));
			}
			
		});
		return pubWallets;
	}
	
	
	Future<JsonArray> getWallets() {
		Future<JsonArray> allWallets = Future.future();
		mongoClient.find("wallets", new JsonObject(), res -> {
			if (res.result().size() != 0) {
				allWallets.complete(new JsonArray(res.result().toString()));
			}		
		});
		return allWallets;
	}
	
	Future<JsonArray> getWalletTransactions(String walletID) {

		Future<JsonArray> allTransactions = Future.future();

		mongoClient.find("transactions", new JsonObject().put("recipient", walletID), res -> {

			if (res.result().size() != 0) {
				allTransactions.complete(new JsonArray(res.result().toString()));
			}
			
		});

		return allTransactions;

	}
	
	Future<JsonArray> getPubWalletTransactions(String address) {

		Future<JsonArray> allTransactions = Future.future();

		mongoClient.find("transactions", new JsonObject().put("wallet2bGranted", address), res -> {

			if (res.result().size() != 0) {
				allTransactions.complete(new JsonArray(res.result().toString()));
			}
			
		});

		return allTransactions;

	}
}
