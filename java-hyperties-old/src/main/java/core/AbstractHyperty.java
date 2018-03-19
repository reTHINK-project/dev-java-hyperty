package core;

import java.util.Iterator;

import data_objects.DataObjectReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import util.InitialData;

public class AbstractHyperty extends AbstractVerticle {

	protected String url;
	protected String identity;
	protected EventBus eb;
	protected String collection;
	protected String database;
	protected String mongoHost;
	protected MongoClient mongoClient = null;

	@Override
	public void start() throws Exception {
		this.url = config().getString("url");
		this.identity = config().getString("identity");
		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());
		this.collection = config().getString("collection");
		this.database = config().getString("database");
		this.mongoHost = config().getString("mongoHost");
		
		final String uri = "mongodb://" + mongoHost +":27017";
		
	    final JsonObject mongoconfig = new JsonObject()
	            .put("connection_string", uri)
	            .put("db_name", this.database);

	    mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	public void send(String address, String message, Handler replyHandler) {
		final String type = new JsonObject(message).getString("type");

		final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
				.addHeader("identity", this.identity).addHeader("type", type);

		this.eb.send(address, message, deliveryOptions, replyHandler);
	}

	public void publish(String address, String message) {
		String type = new JsonObject(message).getString("type");

		DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
				.addHeader("identity", this.identity).addHeader("type", type);

		this.eb.publish(address, message, deliveryOptions);
	}

	private Handler<Message<JsonObject>> onMessage() {
		return message -> {
			System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
					+ message.body().toString());

			final JsonObject body = new JsonObject(message.body().toString());
			final String type = body.getString("type");

			JsonObject response = new JsonObject();
			switch (type) {
			case "read":

				/*
				 * return the queried data. If the read message body does not contain any
				 * resource field, all persisted data is returned.
				 */

				if (body.getJsonObject("resource") != null) {

				} else {
					mongoClient.find(this.collection, new JsonObject(), res -> {
			            System.out.println(res.result().size() + " <-value returned" + res.result().toString());
			    	
			            response.put("data", new JsonArray(res.result().toString())).put("identity", this.identity);
			            message.reply(response);
			        });					
				}
				
				break;
			case "create":
				response.put("code", 200);
				message.reply(response);

				onNotification(newmsg -> {
					System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
							+ newmsg.body().toString());
				});
				break;
			default:
				break;
			}

		};
	}

	/**
	 * 
	 * Setup the handler to process invitations to be an Observer or to be notified
	 * some existing DataObjectObserver was deleted.
	 * 
	 */
	private void onNotification(Handler<Message<JsonObject>> handler) {
		this.eb.consumer(this.url, handler);
	}

	/**
	 * 
	 * @param address
	 * @param handler
	 * 
	 *            Send a subscription message towards address with a callback that
	 *            sets the handler at <address>/changes (ie eventBus.sendMessage(
	 *            ..)).
	 */
	private void subscribe(String address) {
		JsonObject toSend = new JsonObject();
		toSend.put("type", "subscribe");

		send(address, toSend.toString(), reply -> {
			// after reply wait for changes

			final String address_changes = address + "/changes";

			eb.consumer(address_changes, message -> {
				System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
						+ message.body().toString());
			});
		});
	}

	/**
	 * create(dataObjectUrl, observers, initialData ) functions.
	 * @return 
	 */
	public DataObjectReporter create(String dataObjectUrl, JsonArray observers, JsonObject initialData) {
		/**
		 * type: "create", from: "dataObjectUrl/subscription", body: { source:
		 * <hypertyUrl>, schema: <catalogueURL>, value: <initialData> }
		 */
		JsonObject toSend = new JsonObject();
		toSend.put("type", "create");
		toSend.put("from", dataObjectUrl + "/subscription");
		JsonObject body = new JsonObject();
		body.put("source", "hyperty://<sp-domain>/<hyperty-instance-identifier>");
		// TODO
		body.put("schema", "hyperty-catalogue://<sp-domain>/dataObjectSchema/<schema-identifier>");
		body.put("value", initialData);
		toSend.put("body", body);

		Iterator it = observers.getList().iterator();
		while (it.hasNext()) {
			JsonObject currentObs = (JsonObject) it.next();
			String observer = currentObs.getString("observer");
			send(observer, toSend.toString(), reply -> {
				System.out.println(
						"[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] " + reply.toString());
			});
		}
		// create Reporter
		return new DataObjectReporter(dataObjectUrl, vertx);

	}
}
