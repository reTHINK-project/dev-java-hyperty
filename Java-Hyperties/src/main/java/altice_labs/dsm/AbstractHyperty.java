package altice_labs.dsm;


import java.util.Iterator;

import data_objects.DataObjectReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class AbstractHyperty extends AbstractVerticle {

	protected JsonObject identity;
	protected JsonArray streams;
	protected String url;
	protected String collection;
	protected String database;
	protected String mongoHost;
	protected EventBus eb;
	protected MongoClient mongoClient = null;

	@Override
	public void start(){
		this.url = config().getString("url");
		this.identity = config().getJsonObject("identity");
		this.collection = config().getString("collection");
		this.database = config().getString("database");
		this.mongoHost = config().getString("mongoHost");
		this.streams = config().getJsonArray("streams");
		
		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());

		final String uri = "mongodb://" + mongoHost +":27017";
		
	    final JsonObject mongoconfig = new JsonObject()
	            .put("connection_string", uri)
	            .put("db_name", this.database)
	            .put("database", this.database)
	            .put("collection", this.collection);

	    mongoClient = MongoClient.createShared(vertx, mongoconfig);
	}

	public void send(String address, String message, Handler replyHandler) {

		this.eb.send(address, message, getDeliveryOptions(message), replyHandler);
	}

	public void publish(String address, String message) {

		this.eb.publish(address, message, getDeliveryOptions(message));
	}

	private Handler<Message<JsonObject>> onMessage() {
		return message -> {
			System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
					+ message.body());

			final JsonObject body = new JsonObject(message.body().toString());
			final String type = body.getString("type");
			final String from = body.getString("from");
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

				if (from.contains("/subscription")) {
					response.put("code", 200);
					message.reply(response);

					onNotification(newmsg -> {
						System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
								+ newmsg.body().toString());
					});
				} else {
					message.reply(response);
				}

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
		// TODO
		body.put("source", this.url);
		//TODO should be passed on config?
		body.put("schema", "hyperty-catalogue://catalogue.localhost/.well-known/dataschema/Context");
		body.put("value", initialData);
		toSend.put("body", body);

		Iterator it = observers.getList().iterator();
		while (it.hasNext()) {
			String observer = (String) it.next();
			send(observer, toSend.toString(), reply -> {
				System.out.println(
						"[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] " + reply.toString());
			});
		}
		// create Reporter
		return new DataObjectReporter(dataObjectUrl, vertx, identity);

	}
	
	public DeliveryOptions getDeliveryOptions(String message) {
		final String type = new JsonObject(message).getString("type");
		final JsonObject userProfile = this.identity.getJsonObject("userProfile");
		return new DeliveryOptions().addHeader("from", this.url)
				.addHeader("identity", userProfile.getString("userURL")).addHeader("type", type);
	}
}
