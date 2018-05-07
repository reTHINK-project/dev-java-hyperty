package hyperty;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import data_objects.DataObjectReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;


public class AbstractHyperty extends AbstractVerticle {

	protected JsonObject identity;

	protected String url;
	protected String collection;
	protected String database;
	protected String mongoHost;
	protected String schemaURL;
	protected EventBus eb;
	protected MongoClient mongoClient = null;
	private CountDownLatch findWallet;
	protected boolean acceptSubscription;
	private CountDownLatch dataPersisted;
	private boolean dataPersistedFlag;
	protected String dataObjectsCollection = "dataobjects";
	/**
	 * Array with all vertx hyperty observers to be invited for all wallets.
	 */
	protected JsonArray observers;

	@Override
	public void start() {
		this.url = config().getString("url");
		this.identity = config().getJsonObject("identity");
		this.collection = config().getString("collection");
		this.database = config().getString("db_name");
		this.mongoHost = config().getString("mongoHost");
		this.schemaURL = config().getString("schemaURL");
		this.observers = config().getJsonArray("observers");

		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());

		if (mongoHost != null) {
			System.out.println("Setting up Mongo to:" + this.url);
			final String uri = "mongodb://" + mongoHost + ":27017";

			final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", this.database);

			mongoClient = MongoClient.createShared(vertx, mongoconfig);
		}

	}

	public void send(String address, JsonObject message, Handler<AsyncResult<Message<JsonObject>>> replyHandler) {

		this.eb.send(address, message, getDeliveryOptions(message), replyHandler);
	}

	public void publish(String address, JsonObject message) {

		this.eb.publish(address, message, getDeliveryOptions(message));
	}

	private Handler<Message<JsonObject>> onMessage() {
		
		return message -> {
			
			System.out.println("New message -> " +  message.body().toString());
			if (mandatoryFieldsValidator(message)) {

				System.out.println(
						"[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] " + message.body());

				final JsonObject body = new JsonObject(message.body().toString()).getJsonObject("body");
				final String type = new JsonObject(message.body().toString()).getString("type");
				final String from = new JsonObject(message.body().toString()).getString("from");
				JsonObject response = new JsonObject();
				switch (type) {
				case "read":

					/*
					 * return the queried data. If the read message body does not contain any
					 * resource field, all persisted data is returned.
					 */
					
					if (body != null && body.getString("resource") != null) {
						System.out.println("Abstract");
						System.out.println("Getting wallet address  msg:" + body.toString());

						JsonObject identity = new JsonObject().put("userProfile", new JsonObject().put("userURL", body.getString("value")));
						
						JsonObject toSearch = new JsonObject().put("identity", identity);
						
					
						System.out.println("Search on " + this.collection + "  with data" + toSearch.toString());
						
							
						mongoClient.find(this.collection, toSearch, res -> {
							if (res.result().size() != 0) {
								JsonObject walletInfo = res.result().get(0);
								// reply with address
								System.out.println("Returned wallet: " + walletInfo.toString());
								message.reply(walletInfo);
							}
						});
						
						

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
						
						onNotification(new JsonObject(message.body().toString()));
					} else {
						JsonObject msg = new JsonObject(message.body().toString());
						
						if (body == null) {
							
							// handle creation requests, like wallet
							handleCreationRequest(msg, message);
						} else {
							//  handle transfer, from wallet for example
							handleTransfer(msg);
						}
						
					}

					break;
				default:
					break;
				}

			}
		};
	}

	public void handleTransfer(JsonObject msg) {
		// TODO Auto-generated method stub
		
	}

	public void handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * 
	 * Setup the handler to process invitations to be an Observer or to be notified
	 * some existing DataObjectObserver was deleted.
	 * 
	 */
	private void onNotification(JsonObject body) {
		System.out.println("HANDLING" + body.toString());
		String from = body.getString("from");
		String userURL = body.getJsonObject("identity").getJsonObject("userProfile").getString("userURL");
		

		
		subscribe(from, userURL);
		
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
	private void subscribe(String address, String userURL) {
	
		String ObjURL= address.split("/subscription")[0];
		JsonObject subscribeMessage = new JsonObject();
		subscribeMessage.put("from", this.url);
		subscribeMessage.put("to", address);
		subscribeMessage.put("type", "subscribe");
		JsonObject subscribeMessageBody = new JsonObject();
		subscribeMessageBody.put("identity", this.identity);
		subscribeMessage.put("body", subscribeMessageBody);
		
		System.out.println("SUBSCRIBE Message Sent" +  subscribeMessage.toString());
		send(address, subscribeMessage, reply -> {
			// after reply wait for changes
			System.out.println("subscribe reply ->" + reply.result().body().toString());
			
			
			JsonObject resultBody = new JsonObject(reply.result().body().toString());
			int code = resultBody.getJsonObject("body").getInteger("code");
			if (code == 200) {
				
				//TODO: associate DataObjectURL to an identity of invite
				

				if (checkIfCanHandleData(userURL) && persistDataObjUserURL(ObjURL, userURL, "observer")) {
					onChanges(ObjURL);
				}
							
				
			}

			
			
		});
	}
	
	
	public boolean checkIfCanHandleData(String objURL) {
		// TODO Auto-generated method stub
		return true;
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
	public void onChanges(String address) {
		System.out.println("ADDRESS TO PROCESS CHANGES" + address);
		final String address_changes = address + "/changes";

		eb.consumer(address_changes, message -> {
			System.out.println("New Change Received ->"
					+ message.body().toString());
		});
		
	}
	
	
	public boolean persistDataObjUserURL(String address, String userURL, String type) {
		
		
		dataPersistedFlag = false;
		
		dataPersisted = new CountDownLatch(1);
		
		JsonObject document = new JsonObject();
		document.put("userURL", userURL);
		document.put("type", type);
		
		JsonObject toInsert = new JsonObject().put("url", address).put("metadata", document);
		System.out.println("Creating DO entry -> " + toInsert.toString());
		new Thread(() -> {

			
			mongoClient.save(dataObjectsCollection, toInsert, res2 -> {
				System.out.println("Setup complete - dataobjects + Insert" + res2.result().toString());
				dataPersistedFlag = true;
				dataPersisted.countDown();
			});
				
		}).start();
		

		try {
			dataPersisted.await(5L, TimeUnit.SECONDS);
				return dataPersistedFlag;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return dataPersistedFlag;	
		
	}

	/**
	 * create(dataObjectUrl, observers, initialData ) functions.
	 * 
	 * @return
	 */
	public DataObjectReporter create(String dataObjectUrl, JsonObject initialData, boolean toInvite,
			Handler<Message<JsonObject>> subscriptionHandler, Handler<Message<JsonObject>> readHandler) {
		/**
		 * type: "create", from: "dataObjectUrl/subscription", body: { source:
		 * <hypertyUrl>, schema: <catalogueURL>, value: <initialData> }
		 */
		JsonObject toSend = new JsonObject();
		toSend.put("type", "create");
		toSend.put("from", dataObjectUrl + "/subscription");

		JsonObject body = new JsonObject();
		body.put("source", this.url);
		body.put("schema", this.schemaURL);
		body.put("value", initialData);
		toSend.put("body", body);
		if (toInvite) {
			Iterator it = observers.getList().iterator();
			while (it.hasNext()) {
				String observer = (String) it.next();
				send(observer, toSend, reply -> {
					System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
							+ reply.toString());
				});
			}
		}
		// create Reporter
		return new DataObjectReporter(dataObjectUrl, vertx, identity, subscriptionHandler, readHandler);

	}

	public DeliveryOptions getDeliveryOptions(JsonObject message) {
		final String type = message.getString("type");
		final JsonObject userProfile = this.identity.getJsonObject("userProfile");
		return new DeliveryOptions().addHeader("from", this.url).addHeader("identity", userProfile.getString("userURL"))
				.addHeader("type", type);
	}

	/**
	 * Validate the source (from) of a request.
	 * 
	 * @param from
	 * @return
	 */
	public boolean validateSource(String from, String address, JsonObject identity, String collection) {
		// allow wallet creator
		System.out.println("validating source ... from:" + from +  "\nobservers:" + observers.getList().toString() + "\nourUserURL:" 
								+ this.identity.getJsonObject("userProfile").getString("userURL") + "\nCOLLECTION:" + collection);
		
		if (observers.getList().contains(from)) {
			System.out.println("VALID");
			return true;
		} else {
			JsonObject toFind = new JsonObject().put("identity", identity);
			System.out.println("toFIND" + toFind.toString());
			
			acceptSubscription = false;
			findWallet = new CountDownLatch(1);
			
			
			new Thread(() -> {
				mongoClient.find(collection, toFind, res -> {
					if (res.result().size() != 0) {
						JsonObject wallet = res.result().get(0);
						System.out.println("to subscribe add:" + address + " wallet to compare" + wallet);
						
						if(address.equals(wallet.getString("address")) ) {
							System.out.println("RIGHT WALLET");
							if(wallet.getJsonObject("identity").equals(identity)) {
								System.out.println("RIGHT IDENTITY");
								acceptSubscription = true;
								findWallet.countDown();
								return;
							}
							findWallet.countDown();
							return;
							
						} else {
							System.out.println("OTHER WALLET");
							findWallet.countDown();
							return;
						
						}
					}
					
				});
			}).start();
			
			

			try {
				findWallet.await(5L, TimeUnit.SECONDS);
					return acceptSubscription;
			} catch (InterruptedException e) {
				System.out.println("3 - interrupted exception");
			}
			System.out.println("3 - return other");
			return acceptSubscription;
			
		}
		
		
		
		
		
		
		//return false;
	}

	/**
	 * 
	 * @param message
	 * @return true when mandatory fields are defined
	 */
	public boolean mandatoryFieldsValidator(Message<JsonObject> message) {
		// header validation...
		final JsonObject json = new JsonObject(message.body().toString());
		JsonObject response = new JsonObject();
		response.put("code", 406);

		final String type = json.getString("type");
		if (type == null) {
			response.put("description", "No mandatory field 'type'");
			message.reply(response);
			return false;
		}

		final String from = json.getString("from");
		if (from == null) {
			response.put("description", "No mandatory field 'from'");
			message.reply(response);
			return false;
		}

		final JsonObject identity = json.getJsonObject("identity");
		if (identity == null) {
			response.put("description", "No mandatory field 'identity'");
			message.reply(response);
			return false;
		}

		return true;

	}
}
