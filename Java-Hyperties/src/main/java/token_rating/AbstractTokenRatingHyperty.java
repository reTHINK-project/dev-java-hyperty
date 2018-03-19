package token_rating;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.Gson;

import altice_labs.dsm.AbstractHyperty;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import util.DateUtils;

public class AbstractTokenRatingHyperty extends AbstractHyperty {

	protected MongoClient mongoClient = null;
	String uri = "mongodb://localhost:27017";
	String db = "test";
	String ratesCollection = "rates";
	private Config config;

	@Override
	public void start() {
		// System.out.println("Configuration: " + config().getString("name"));

		// parse config file
		Gson gson = new Gson();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("./src/main/java/token_rating/config.json"));
			Config config = (Config) gson.fromJson(br, Config.class);
			if (config != null) {
				this.config = config;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();

		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		addMyHandler();

		// make connection with MongoDB
		JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", db);
		mongoClient = MongoClient.createShared(vertx, mongoconfig);

		// vertx.eventBus().consumer(this.url, onMessage());
	}

	/*
	 * An empty rating engine function (separate class?) when the data evaluation in
	 * tokens is implemented according to a certain algorithm.
	 */
	int rate(Object data) {
		return 10;
	}

	/*
	 * A Token miner function that generates numTokens as uint type as well as an
	 * associated transaction that is stored in a DB (or the transaction is only
	 * stored in the recipient wallet ?) (future in a blockchain?):
	 */
	private void mine(int numTokens, Object data) {
		System.out.println("Mining " + numTokens + " tokens...");
		// create transaction
		Transaction tr = new Transaction();
		tr.setValue(numTokens);
		tr.setRecipient(config.getWalletManagerAddress());
		tr.setSource(config.getStream());

		// store transaction by sending it to wallet through wallet manager
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

		transfer(msg);
	}

	/**
	 * Performs the transaction to Wallet Address.
	 * 
	 * @param transaction
	 */
	private void transfer(WalletManagerMessage msg) {
		System.out.println("Sending transaction to Wallet Manager...");

		Gson gson = new Gson();
		vertx.eventBus().publish(config.getWalletManagerAddress(), gson.toJson(msg));
	}

	/**
	 * Send message to Wallet Manager address with callback to return the value
	 * returned in case it is found.
	 * 
	 * @param userId
	 * @return
	 */
	private String getWalletAddress(String userId) {
		// send message to Wallet Manager address
		/*
		 * type: read, from: <rating address>, body: { resource: 'user/<userId>'}
		 */
		// build message and convert to JSON string
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType("read");
		msg.setFrom(config.getHyperty());
		msg.setBody("{ resource: 'user/" + userId + "'}");

		send(config.getWalletManagerAddress(), new Gson().toJson(msg), onMessage());

		return "123";

	}

	private Handler<Message<String>> onMessage() {
		return reply -> {
			// with callback to return the value returned in case it is found.
			// return reply;
		};
	}

	/**
	 * Add an handler in the Rating Hyperty address specified in the config file and
	 * calls addStreamHandler() for valid received invitations (create messages) or
	 * removeStreamHandler for valid received delete messages.
	 */
	private void addMyHandler() {

		// add a stream handler
		// vertx.eventBus().consumer(config.getStream(), onMessage());
		vertx.eventBus().consumer(config.getStream(), message -> {

			Gson gson = new Gson();
			WalletManagerMessage msg = gson.fromJson(message.body().toString(), WalletManagerMessage.class);

			// check message type
			switch (msg.getType()) {
			case "create":
				// valid received invitations (create messages)
				addStreamHandler(msg.getFrom());
				break;
			case "delete":
				removeStreamHandler(msg.getFrom());
				break;

			default:
				System.out.println("Incorrect message type: " + msg.getType());
				break;
			}
		});
	}

	/**
	 * Add stream handlers and forwards it to rate() if rate returns a valid uint it
	 * calls mine() and transfers it to associated address
	 * 
	 * 
	 */
	private void addStreamHandler(String from) {
		System.out.println("Adding stream handler from " + from);
		// add a stream handler
		// vertx.eventBus().consumer(config.getStream(), onMessage());
		vertx.eventBus().consumer(from, message -> {

			System.out.println("Received message " + message.body() + " from " + from);

			int numTokens = rate(message.body());
			if (numTokens == -1) {
				System.out.println("User is not inside any shop");
			} else {
				mine(numTokens, message);
			}
		});
	}

	private void removeStreamHandler(String from) {
		System.out.println("Removing stream handler from " + from);
	}

	/**
	 * Save data to MongoDB.
	 * 
	 * @param user
	 *            user ID
	 * @param timestamp
	 *            time in millis since epoch
	 * @param shopID
	 *            shopID
	 */
	void persistData(String dataSource, String user, long timestamp, String shopID, JsonObject userRates) {

		// add a new entry to the data source
		JsonArray entryArray = userRates.getJsonArray(dataSource);

		// build JSON to send to Mongo
		JsonObject checkinInfo = new JsonObject();
		checkinInfo.put("user", user);

		JsonObject entry = new JsonObject();
		entry.put("timestamp", timestamp);
		entry.put("id", shopID);
		entryArray.add(entry);
		checkinInfo.put(dataSource, entryArray);

		System.out.println("Persisting");
		JsonObject document = new JsonObject(checkinInfo.toString());

		JsonObject query = new JsonObject().put("user", user);
		mongoClient.findOneAndReplace(ratesCollection, query, document, id -> {
			System.out.println("Document with ID:" + id + " was updated");
		});
	}

}
