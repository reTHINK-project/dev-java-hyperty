package data_objects;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class DataObjectReporter {

	private EventBus eb;
	private Handler<Message<JsonObject>> onSubscriptionHandler = null;
	private Handler<Message<JsonObject>> onReadHandler = null;
	String[] subscriptions;

	public DataObjectReporter(String dataObjectUrl, Vertx vertx) {
		this.eb = vertx.eventBus();
		this.eb.consumer(dataObjectUrl + "/subscription", onSubscribe());
		this.eb.consumer(dataObjectUrl, onRead());
	}

	/**
	 * Setup the handler for incoming subscriptions.
	 */
	public Handler<Message<JsonObject>> setSubscriptionHandler(Handler<Message<JsonObject>> handler) {
		return message -> {
			System.out.println("Reporter is handling subscriptions");
			// setup the handler for subscriptions
			onSubscriptionHandler = handler;
		};
	}

	public Handler<Message<JsonObject>> setReadHandler(Handler<Message<JsonObject>> handler) {
		return message -> {
			System.out.println("Reporter is reading subscriptions");
			// setup the handler for read ops
			onReadHandler = handler;
		};
	}

	/**
	 * Receive subscribe message and pass it on to the handler.
	 * 
	 * @param msg
	 */
	private Handler<Message<JsonObject>> onSubscribe() {
		return msg -> {

			if (onSubscriptionHandler != null) {
				// TODO pass event
				onSubscriptionHandler.handle(msg);
			}
		};

	}

	private Handler<Message<JsonObject>> onRead() {
		return msg -> {

			if (onReadHandler != null) {
				// TODO pass event
				onReadHandler.handle(msg);
			}
		};

	}

}
