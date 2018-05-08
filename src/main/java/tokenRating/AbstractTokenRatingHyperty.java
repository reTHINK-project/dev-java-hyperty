package tokenRating;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import hyperty.AbstractHyperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.DateUtils;

public class AbstractTokenRatingHyperty extends AbstractHyperty {

	/**
	 * hyperty address where to setup an handler to process invitations in case the
	 * data source is dynamic eg produced by the smart citizen
	 */
	private String hyperty;
	/**
	 * the stream address to setup the handler in case the address is static e.g.
	 * when the stream is produced via the Smart IoT.
	 */
	private String streamAddress;
	/**
	 * Wallet Manager Hyperty address.
	 */
	private String walletManagerAddress;
	
	private String walletAddress;

	private CountDownLatch checkUser;
	boolean addHandler = false;
	
	@Override
	public void start() {
		super.start();
		System.out.println("Abstract started");

		// read config
		hyperty = config().getString("hyperty");
		streamAddress = config().getString("stream");
		walletManagerAddress = config().getString("wallet");
		
		addMyHandler();
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
	void mine(int numTokens, JsonObject msgOriginal, String source) {
		System.out.println("Mining " + numTokens + " tokens...");
		String userId = msgOriginal.getString("userID");
		System.out.println("MINING: " + msgOriginal);


		// store transaction by sending it to wallet through wallet manager
		String walletAddress = getWalletAddress(userId);

		System.out.println("WAlletADDRESS " + walletAddress + "\nFROM " + userId);
		JsonObject msgToWallet = new JsonObject();
		msgToWallet.put("type", "create");
		msgToWallet.put("identity", this.identity);

		// create transaction object
		JsonObject transaction = new JsonObject();
		//transaction.put("address", walletAddress);
		transaction.put("recipient", walletAddress);
		transaction.put("source", source);
		transaction.put("date", DateUtils.getCurrentDateAsISO8601());
		
		if (numTokens == -1)  {
			transaction.put("description", "invalid-timestamp");
		} else if (numTokens == -2) {
			transaction.put("description", "invalid-location");
		} else {
			transaction.put("description", "valid");
		}
		
		
		transaction.put("value", numTokens);
		transaction.put("nonce", 1);
		JsonObject body = new JsonObject().put("resource", "wallet/" + walletAddress).put("value", transaction);
		
		msgToWallet.put("body", body);
		
		transfer(msgToWallet);
	}

	/**
	 * Performs the transaction to Wallet Address.
	 * 
	 * @param transaction
	 */
	private void transfer(JsonObject msg) {
		System.out.println("Sending transaction to Wallet Manager..." + msg.toString());


		vertx.eventBus().publish(walletManagerAddress, msg);
	}

	/**
	 * Send message to Wallet Manager address with callback to return the value
	 * returned in case it is found.
	 * 
	 * @param userId
	 * @return
	 */
	String getWalletAddress(String userId) {
		System.out.println("Getting WalletAddress to:" + userId);
		// send message to Wallet Manager address
		/*
		 * type: read, from: <rating address>, body: { resource: 'user/<userId>'}
		 */
		// build message and convert to JSON string
		JsonObject msg = new JsonObject();
		msg.put("type", "read");
		msg.put("from", hyperty);
		msg.put("body", new JsonObject().put("resource", "user").put("value",userId));
		msg.put("identity", new JsonObject());

		CountDownLatch setupLatch = new CountDownLatch(1);

		new Thread(() -> {
			send(walletManagerAddress, msg, reply -> {

				System.out.println("sending reply from getwalletAddress" + reply.result().body().toString());
				walletAddress = reply.result().body().getString("address");
				setupLatch.countDown();
			});
		}).start();
		
		try {
			setupLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("WALLET ADDRESS returning" + walletAddress);
		return walletAddress;

	}

	/**
	 * Add an handler in the Rating Hyperty address specified in the config file and
	 * calls addStreamHandler() for valid received invitations (create messages) or
	 * removeStreamHandler for valid received delete messages.
	 */
	private void addMyHandler() {
		System.out.println("..." + streamAddress);
		vertx.eventBus().<JsonObject>consumer(streamAddress, message -> {
			System.out.println("Abstract REC" + message.body().toString());
			mandatoryFieldsValidator(message);
			
			JsonObject body = new JsonObject(message.body().toString());
			String type = body.getString("type");
			String handleCheckInUserURL = body.getJsonObject("identity").getJsonObject("userProfile").getString("userURL");
			
			JsonObject response = new JsonObject();
			// check message type
			switch (type) {
			case "create":
				// valid received invitations (create messages)
				System.out.println("Abstract ADD STREAM");
				if(checkIfCanHandleData(handleCheckInUserURL)) {
					addStreamHandler(handleCheckInUserURL);
					response.put("body",new JsonObject().put("code", 200));
					message.reply(response);
					System.out.println("Replied with" + response.toString());
				} else {
					response.put("body",new JsonObject().put("code", 406));
					message.reply(response);
				}
				break;
			case "delete":
				removeStreamHandler(handleCheckInUserURL);
				break;

			default:
				System.out.println("Incorrect message type: " + type);
				break;
			}
		});
	}


	public boolean checkIfCanHandleData(String userURL) {
		System.out.println("CHECK IF CAN BE ADDED:" + userURL);
		addHandler = false;
		
		checkUser = new CountDownLatch(1);
		
		JsonObject toFind = new JsonObject().put("user", userURL);
		
		new Thread(() -> {
			mongoClient.find(collection, toFind, res -> {
				if (res.result().size() != 0) {
					addHandler = true;
					checkUser.countDown();
				} else {
					JsonObject document = new JsonObject();
					document.put("user", userURL);
					document.put("checkin", new JsonArray());
					mongoClient.insert(collection, document, res2 -> {
						System.out.println("Setup complete - rates");
						addHandler = true;
						checkUser.countDown();
					});
				}
				
			});
		}).start();
		

		try {
			checkUser.await(5L, TimeUnit.SECONDS);
				return addHandler;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return addHandler;		
		
		
	}
	
	
	/**
	 * Add stream handlers and forwards it to rate() if rate returns a valid uint it
	 * calls mine() and transfers it to associated address
	 * 
	 * 
	 */
	private void addStreamHandler(String from) {
	// add a stream handler
		System.out.println("Adding stream handler from " + from);
		vertx.eventBus().<JsonObject>consumer(from, message -> {
			mandatoryFieldsValidator(message);

			System.out.println("Received message " + message.body() + " from " + from);

			int numTokens = rate(message.body());
			if (numTokens == -1) {
				System.out.println("ABSTRACT TOKEN: User is not inside any shop");
			} else {
				mine(numTokens, message.body(), "source");
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
		JsonArray entryArray = null;

		if (userRates != null) {
			entryArray = userRates.getJsonArray(dataSource);
		} else {
			entryArray = new JsonArray();
		}
		// add a new entry to the data source
		
		// build JSON to send to Mongo
		JsonObject checkinInfo = new JsonObject();
		checkinInfo.put("user", user);

		JsonObject entry = new JsonObject();
		entry.put("timestamp", timestamp);
		entry.put("id", shopID);
		entryArray.add(entry);
		checkinInfo.put(dataSource, entryArray);

		JsonObject document = new JsonObject(checkinInfo.toString());

		JsonObject query = new JsonObject().put("user", user);
		mongoClient.findOneAndReplace(collection, query, document, id -> {
			System.out.println("Document with ID:" + id + " was updated");
		});
	}

}
