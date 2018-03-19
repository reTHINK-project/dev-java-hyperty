package data_objects;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class DataObjectReporter {

	private EventBus eb;
	private Handler<Message<JsonObject>> onSubscriptionHandler = null;
	private Handler<Message<JsonObject>> onReadHandler = null;
	String[] subscriptions;
	private MongoClient mongoClient;

	public DataObjectReporter(String dataObjectUrl, Vertx vertx, String identity) {
		this.eb = vertx.eventBus();
		this.eb.consumer(dataObjectUrl + "/subscription", onSubscribe());
		System.out.println("Reporter listening in " + dataObjectUrl + "/subscription");
		this.eb.consumer(dataObjectUrl, onRead());
	}

	public MongoClient getMongoClient() {
		return mongoClient;
	}

	public void setMongoClient(MongoClient mongoClient) {
		this.mongoClient = mongoClient;
	}

	/**
	 * Setup the handler for incoming subscriptions.
	 */
	public void setSubscriptionHandler(Handler<Message<JsonObject>> handler) {
		onSubscriptionHandler = handler;
	}

	public void setReadHandler(Handler<Message<JsonObject>> handler) {
		// setup the handler for read ops
		onReadHandler = handler;
	}

	/**
	 * Receive subscribe message and pass it on to the handler.
	 * 
	 * @param msg
	 */
	private Handler<Message<JsonObject>> onSubscribe() {
		return msg -> {
			if (onSubscriptionHandler != null) {
				onSubscriptionHandler.handle(msg);
			}
		};

	}

	private Handler<Message<JsonObject>> onRead() {
		return msg -> {
			if (onReadHandler != null) {
				onReadHandler.handle(msg);
			}
		};

	}

}
