package token_rating;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import core.AbstractHyperty;
import data_objects.DataObjectReporter;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import util.DateUtils;

public class WalletManagerHyperty extends AbstractHyperty {

	private String walletsCollection = "wallets";
	private String dataObjectUrl;
	/**
	 * Array with all vertx hyperty observers to be invited for all wallets.
	 */
	private JsonArray observers;

	@Override
	public void start() {
		super.start();
		
		// read config
		observers = config().getJsonArray("observer");
		dataObjectUrl = config().getString("dataObjectUrl");

		handleRequests();
	}

	/**
	 * Handle requests.
	 */
	private void handleRequests() {

		vertx.eventBus().consumer(config().getString("url"), message -> {
			System.out.println("Message received: " + message.body().toString());

			Gson gson = new Gson();
			WalletManagerMessage msg = gson.fromJson(message.body().toString(), WalletManagerMessage.class);
			System.out.println(msg.getFrom());

			switch (msg.getType()) {
			case "delete":
				walletDelete(msg);
				break;
			case "create":
				if (msg.getFrom() != null) {
					// Wallet creation requests
					walletCreationRequest(msg);
				} else {
					// Wallet transfer
					walletTransfer(msg);
				}
				break;
			case "read":
				JsonObject body = new JsonObject(msg.getBody());
				final String resource = body.getString("resource");
				switch (resource) {
				case "user":
					// Wallet address request
					walletAddressRequest(msg, message);
					break;
				case "wallet":
					// Wallet read
					walletRead(msg, message);
					break;
				default:
					break;
				}

				break;
			default:
				System.out.println("Incorrect message type: " + msg.getType());
				break;
			}
		});
	}

	/**
	 * It checks there is wallet for the identity and deletes from the storage.
	 * 
	 * @param msg
	 */
	private void walletDelete(WalletManagerMessage msg) {
		System.out.println("Deleting wallet");
		/**
		 * type: delete, identity: <compliant with reTHINK identity model>, from:
		 * <wallet observer hyperty address>
		 */

		// get wallet
		mongoClient.find(walletsCollection, new JsonObject().put("identity", msg.getIdentity()), res -> {
			JsonObject wallet = res.result().get(0);
			wallet.put("status", "deleted");
			JsonObject document = new JsonObject(wallet.toString());

			JsonObject query = new JsonObject().put("identity", msg.getIdentity());
			mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
				System.out.println("Document with ID:" + id + " was updated");
			});
		});

	}

	/**
	 * Add new transfer to a wallet.
	 * 
	 * @param msg
	 */
	private void walletTransfer(WalletManagerMessage msg) {
		/*
		 * type: create, body: { resource: 'wallet/<wallet-address>', value:
		 * <transaction JSON Object>}
		 */
		System.out.println("Transfer op");
		JsonObject body = new JsonObject(msg.getBody());
		String walletAddress = body.getString("resource").split("/")[1];
		JsonObject transaction = body.getJsonObject("value");

		if (validateTransaction(transaction)) {
			// get wallet document
			mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
				JsonObject walletInfo = res.result().get(0);

				int currentBalance = walletInfo.getInteger("balance");
				int transactionValue = transaction.getInteger("value");

				// store transaction
				JsonArray transactions = walletInfo.getJsonArray("transactions");
				transactions.add(transaction);
				// update balance
				walletInfo.put("balance", currentBalance + transactionValue);
				JsonObject document = new JsonObject(walletInfo.toString());

				JsonObject query = new JsonObject().put("address", walletAddress);
				mongoClient.findOneAndReplace(walletsCollection, query, document, id -> {
					System.out.println("Transaction added to wallet");

					// publish transaction in the event bus using the wallet address.
					publish(walletAddress, transaction.toString());
				});
			});

		}

	}

	CompletableFuture<Boolean> result = new CompletableFuture<>();

	private boolean validateTransaction(JsonObject transaction) {

		System.out.println("Validating " + transaction.toString());

		// check the fields themselves
		if (!transaction.containsKey("recipient") || !transaction.containsKey("source")
				|| !transaction.containsKey("date") || !transaction.containsKey("value")
				|| !transaction.containsKey("nonce")) {
			System.out.println("Invalid");
			return false;
		}

		// check date validity
		if (!DateUtils.validateDate(transaction.getString("date"))) {
			System.out.println("Invalid date format");
			return false;
		}

		// check tokens amount
		if (transaction.getInteger("value") <= 0) {
			return false;
		}

		// check if wallet address exists
		mongoClient.find(walletsCollection, new JsonObject().put("address", transaction.getString("address")), res -> {
			result.complete(true);
			if (!res.succeeded()) {
				System.out.println("Wallet does not exist");
				// return false
				result.complete(false);
			} else {
				System.out.println("Wallet exists");
				result.complete(true);

				// check if nonce is repeated
				JsonObject wallet = res.result().get(0);
				JsonArray transactions = wallet.getJsonArray("transactions");

				ArrayList<JsonObject> a = (ArrayList<JsonObject>) transactions.getList();
				List<JsonObject> repeatedNonces = (List<JsonObject>) a.stream() // convert list to stream
						.filter(element -> transaction.getInteger("nonce") == element.getInteger("nonce"))
						.collect(Collectors.toList());

				if (repeatedNonces.size() > 0) {
					// nonce is repeated

				}
			}

		});

		try {
			return result.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Return wallet address for a user.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletAddressRequest(WalletManagerMessage msg, Message<Object> message) {
		System.out.println("Getting wallet address");
		JsonObject body = new JsonObject(msg.getBody());
		String userID = body.getString("value");

		mongoClient.find(walletsCollection, new JsonObject().put("identity", userID), res -> {
			JsonObject walletInfo = res.result().get(0);
			// reply with address
			System.out.println("Returned wallet: " + walletInfo.toString());
			message.reply(walletInfo.getString("address"));
		});

	}

	/**
	 * Return wallet.
	 * 
	 * @param msg
	 * @param message
	 */
	private void walletRead(WalletManagerMessage msg, Message<Object> message) {
		System.out.println("Getting wallet by address");
		JsonObject body = new JsonObject(msg.getBody());
		String walletAddress = body.getString("value");

		JsonObject walletInfo = new JsonObject();

		mongoClient.find(walletsCollection, new JsonObject().put("address", walletAddress), res -> {
			JsonObject wallet = res.result().get(0);
			System.out.println(wallet);
			message.reply(wallet.toString());
		});

	}

	/**
	 * Create a new wallet.
	 * 
	 * @param msg
	 */
	private void walletCreationRequest(WalletManagerMessage msg) {
		System.out.println("Creating wallet");
		/*
		 * Before the wallet is created, it checks there is no wallet yet for the
		 * identity.
		 */
		mongoClient.find(walletsCollection, new JsonObject().put("identity", msg.getIdentity()), res -> {
			if (res.result().size() == 0) {
				System.out.println("no wallet yet, creating");

				// build wallet document
				JsonObject newWallet = new JsonObject();

				String address = generateWalletAddress(msg.getIdentity());
				newWallet.put("address", address);
				newWallet.put("identity", msg.getIdentity());
				newWallet.put("created", new Date().getTime());
				newWallet.put("balance", 0);
				newWallet.put("transactions", new JsonArray());
				newWallet.put("status", "active");
				JsonObject document = new JsonObject(newWallet.toString());

				mongoClient.save(walletsCollection, document, id -> {
					System.out.println("New wallet with ID:" + id);

					// An invitation is sent to config.observers
					DataObjectReporter reporter = create(address, observers, new JsonObject());
					reporter.setMongoClient(mongoClient);
					// pass handler function that will handle subscription events
					reporter.setSubscriptionHandler(requestsHandler());
					reporter.setReadHandler(readHandler());
				});

			} else {
				System.out.println("wallet already exists");
			}
		});

	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> requestsHandler() {
		return msg -> {
			// accept ? reject by handler
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", msg.body().getString("to"));
			response.put("to", msg.body().getString("from"));
			JsonObject sendMsgBody = new JsonObject();
			if (validateSource(from)) {
				sendMsgBody.put("code", 200);
				msg.reply(response);
			} else {
				sendMsgBody.put("code", 403);
				msg.reply(response);
			}

		};

	}

	/**
	 * Handler for read requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readHandler() {
		return msg -> {
			// get wallet
			String from = msg.body().getString("from");
			JsonObject response = new JsonObject();
			response.put("type", "response");
			response.put("from", msg.body().getString("to"));
			response.put("to", msg.body().getString("from"));
			JsonObject sendMsgBody = new JsonObject();
			if (validateSource(from)) {
				sendMsgBody.put("code", 200);
				msg.reply(response);
			} else {
				sendMsgBody.put("code", 403);
				msg.reply(response);
			}
		};

	}

	private boolean validateSource(String from) {
		// TODO reject the entities who shouldn't have access to wallet
		return true;
	}

	/**
	 * TODO The Wallet Address is generated by using some crypto function that uses
	 * the identity GUID as seed and returned.
	 * 
	 * @param identity
	 * @return wallet address
	 */
	private String generateWalletAddress(String identity) {
		return "wallet-address";
	}

}
