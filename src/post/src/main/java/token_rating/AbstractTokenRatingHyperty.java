package token_rating;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.Gson;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import rest.post.AbstractHyperty;

public class AbstractTokenRatingHyperty extends AbstractHyperty {

	private Config config;

	@Override
	public void start() {
//		System.out.println("Configuration: " + config().getString("name"));

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

		// vertx.eventBus().consumer(this.url, onMessage());
	}

	/*
	 * An empty rating engine function (separate class?) when the data evaluation in
	 * tokens is implemented according to a certain algorithm.
	 */
	private int rate(Object data) {
		// TODO use rating algorithm
		System.out.println("Rating message...");
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

		// TODO store transaction
		System.out.println("Storing transaction...");

		transfer(tr);
	}

	/**
	 * Performs the transaction to Wallet Address.
	 * 
	 * @param transaction
	 */
	private void transfer(Transaction transaction) {
		// fetch wallet address

		// TODO send transaction to wallet address (message?)
		System.out.println("Transfering transaction to Wallet Address...");

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
		TokenMessage msg = new TokenMessage();
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
			TokenMessage msg = gson.fromJson(message.body().toString(), TokenMessage.class);

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

			System.out.println("Received message from " + from);

			int numTokens = rate(message);
			if (numTokens != -1) {
				mine(numTokens, message);
			}
		});
	}

	private void removeStreamHandler(String from) {
		System.out.println("Removing stream handler from " + from); 
	}

}
