package hyperty;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.core.Logger;

import data_objects.DataObjectReporter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import runHyperties.LoggerFactory;

public class AbstractHyperty extends AbstractVerticle {

	private static final String logMessage = "[AbstractHyperty] ";

	protected JsonObject identity;

	protected String url;
	protected String collection;
	protected String database;
	protected String mongoHost;
	protected String mongoPorts;
	protected String mongoCluster;
	protected String schemaURL;
	protected EventBus eb;
	protected MongoClient mongoClient = null;
	protected boolean acceptSubscription;
	protected String dataObjectsCollection = "dataobjects";
	protected Logger logger;
	/**
	 * Array with all vertx hyperty observers to be invited for all wallets.
	 */
	protected JsonArray observers;

	protected String siotStubUrl;

	@Override
	public void start() {
		this.url = config().getString("url");
		this.identity = config().getJsonObject("identity");
		this.collection = config().getString("collection");
		this.database = config().getString("db_name");
		this.mongoHost = config().getString("mongoHost");
		this.mongoPorts = config().getString("mongoPorts");
		this.mongoCluster = config().getString("mongoCluster");
		this.schemaURL = config().getString("schemaURL");
		this.observers = config().getJsonArray("observers");
		this.siotStubUrl = config().getString("siot_stub_url");
		this.logger =  LoggerFactory.getInstance().getLogger(); 

		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());

		if (mongoHost != null && mongoPorts != null && mongoCluster != null) {
			logger.debug("Setting up Mongo to:" + this.url);

			logger.debug("Setting up Mongo to:" + this.mongoHost);

			logger.debug("Setting up Mongo to:" + this.mongoCluster);

			JsonObject mongoconfig = null;

			if (mongoCluster.equals("NO")) {

				final String uri = "mongodb://" + mongoHost + ":27017";
				mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", "test");

			} else {
				JsonArray hosts = new JsonArray();

				String[] hostsEnv = mongoHost.split(",");
				String[] portsEnv = mongoPorts.split(",");

				for (int i = 0; i < hostsEnv.length; i++) {
					hosts.add(new JsonObject().put("host", hostsEnv[i]).put("port", Integer.parseInt(portsEnv[i])));
					logger.debug("added to config:" + hostsEnv[i] + ":" + portsEnv[i]);
				}

				mongoconfig = new JsonObject().put("replicaSet", "testeMongo").put("db_name", "test").put("hosts",
						hosts);

			}

			logger.debug("Setting up Mongo with cfg on ABS:" + mongoconfig.toString());
			mongoClient = MongoClient.createShared(vertx, mongoconfig);

		}

	}

	public void send(String address, JsonObject message, Handler<AsyncResult<Message<JsonObject>>> replyHandler) {

		this.eb.send(address, message, getDeliveryOptions(message), replyHandler);
	}

	public void publish(String address, JsonObject message) {

		this.eb.publish(address, message, getDeliveryOptions(message));
	}

	public Handler<Message<JsonObject>> onMessage() {

		return message -> {

			logger.debug(logMessage + "New message -> " + message.body().toString());
			if (mandatoryFieldsValidator(message)) {

				logger.debug(logMessage + "[NewData] -> [Worker]-" + Thread.currentThread().getName()
						+ "\n[Data] " + message.body());

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
						logger.debug(logMessage + "Getting wallet address msg:" + body.toString());

						JsonObject identity = new JsonObject().put("userProfile",
								new JsonObject().put("guid", body.getString("value")));

						JsonObject toSearch = new JsonObject().put("identity", identity);

						logger.debug(
								logMessage + "Search on " + this.collection + " with data" + toSearch.toString());

						mongoClient.find(this.collection, toSearch, res -> {
							if (res.result().size() != 0) {
								JsonObject walletInfo = res.result().get(0);
								// reply with address
								logger.debug("Returned wallet: " + walletInfo.toString());
								message.reply(walletInfo);
							}
						});

					} else {
						mongoClient.find(this.collection, new JsonObject(), res -> {
							logger.debug(
									logMessage + res.result().size() + " <-value returned" + res.result().toString());

							response.put("data", new JsonArray(res.result().toString())).put("identity", this.identity);
							message.reply(response);
						});
					}

					break;
				case "create":
					if (from.contains("/subscription")) {
						logger.debug("TO INVITE");
						onNotification(new JsonObject(message.body().toString()));
					} else {
						JsonObject msg = new JsonObject(message.body().toString());
						if (body == null) {
							// handle creation requests, like wallet
							handleCreationRequest(msg, message);
						} else {
							// handle transfer, from wallet for example
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

	}

	public Future<Void> handleCreationRequest(JsonObject msg, Message<JsonObject> message) {
		Future<Void> handleCreationRequest = Future.future();
		handleCreationRequest.complete();
		return handleCreationRequest;
	}

	public Future<String> findDataObjectStream(String objURL, String guid) {

		logger.debug("{{AbstractHyperty}} find do:" + objURL);
		Future<String> doStream = Future.future();

		mongoClient.find(this.dataObjectsCollection, new JsonObject().put("objURL", objURL), res -> {
			for (int i = 0; i < res.result().size(); i++) {
				String currentGuid = res.result().get(i).getJsonObject("metadata").getString("guid");
				if (currentGuid.equals(guid)) {
					String streamID = res.result().get(0).getString("url");
					doStream.complete(streamID);
					return;
				}
			}
			doStream.complete(null);
		});

		return doStream;
	}

	/**
	 *
	 * Setup the handler to process invitations to be an Observer or to be notified
	 * some existing DataObjectObserver was deleted.
	 *
	 */
	public void onNotification(JsonObject body) {
		logger.debug("HANDLING" + body.toString());
		String from = body.getString("from");
		String guid = body.getJsonObject("identity").getJsonObject("userProfile").getString("guid");

		if (body.containsKey("external") && body.getBoolean("external")) {
			logger.debug("EXTERNAL INVITE");
			String streamID = body.getString("streamID");
			String objURL = from.split("/subscription")[0];
			Future<String> CheckURL = findDataObjectStream(objURL, guid);
			CheckURL.setHandler(asyncResult -> {
				if (asyncResult.succeeded()) {
					if (CheckURL == null) {
						Future<Boolean> persisted = persistDataObjUserURL(streamID, guid, objURL, "reporter");
						persisted.setHandler(res -> {
							if (res.succeeded()) {
								if (persisted.result()) {
									onChanges(objURL);
								}
							} else {
								// oh ! we have a problem...
							}
						});

					} else {
						onChanges(objURL);
					}
				} else {
					// oh ! we have a problem...
				}
			});

		} else {
			subscribe(from, guid);
		}
	}

	/**
	 *
	 * @param address
	 * @param handler
	 *
	 *                Send a subscription message towards address with a callback
	 *                that sets the handler at <address>/changes (ie
	 *                eventBus.sendMessage( ..)).
	 */
	public void subscribe(String address, String guid) {

		String ObjURL = address.split("/subscription")[0];
		JsonObject subscribeMessage = new JsonObject();
		subscribeMessage.put("from", this.url);
		subscribeMessage.put("to", address);
		subscribeMessage.put("type", "subscribe");
		JsonObject subscribeMessageBody = new JsonObject();
		subscribeMessageBody.put("identity", this.identity);
		subscribeMessage.put("body", subscribeMessageBody);

		logger.debug(logMessage + "SUBSCRIBE Message Sent" + subscribeMessage.toString());
		send(address, subscribeMessage, reply -> {
			// after reply wait for changes
			logger.debug(logMessage + "subscribe reply ->" + reply.result().body().toString());

			JsonObject resultBody = new JsonObject(reply.result().body().toString());
			int code = resultBody.getJsonObject("body").getInteger("code");
			if (code == 200) {
				// TODO: associate DataObjectURL to an identity of invite
				Future<Boolean> canHandleData = checkIfCanHandleData(guid);
				Future<Boolean> persisted = persistDataObjUserURL(ObjURL, guid, "observer");
				List<Future> futures = new ArrayList<>();
				futures.add(canHandleData);
				futures.add(persisted);
				CompositeFuture.all(futures).setHandler(done -> {
					if (done.succeeded()) {
						boolean res1 = done.result().resultAt(0);
						boolean res2 = done.result().resultAt(1);
						if (res1 && res2) {
							onChanges(ObjURL);
						}
					} else {

					}
				});

			}

		});
	}

	public Future<Boolean> checkIfCanHandleData(String objURL) {
		Future<Boolean> checkIfCanHandleData = Future.future();
		checkIfCanHandleData.complete(true);
		return checkIfCanHandleData;
	}

	/**
	 *
	 * @param address
	 * @param handler
	 *
	 *                Send a subscription message towards address with a callback
	 *                that sets the handler at <address>/changes (ie
	 *                eventBus.sendMessage( ..)).
	 */
	public void onChanges(String address) {
		logger.debug(logMessage + "onChanges() -> ADDRESS TO PROCESS CHANGES" + address);
		final String address_changes = address + "/changes";

		eb.consumer(address_changes, message -> {
			logger.debug(logMessage + "New Change Received ->" + message.body().toString());
		});

	}

	public Future<Boolean> persistDataObjUserURL(String streamID, String guid, String objURL, String type) {

		Future<Boolean> dataPersisted = Future.future();

		JsonObject document = new JsonObject();
		document.put("guid", guid);
		document.put("type", type);

		JsonObject toInsert = new JsonObject().put("url", streamID).put("objURL", objURL).put("metadata", document);
		logger.debug("Creating DO entry -> " + toInsert.toString());

		mongoClient.save(dataObjectsCollection, toInsert, res2 -> {
			logger.debug("Setup complete - dataobjects + Insert" + res2.result().toString());
			dataPersisted.complete(res2.succeeded());
		});

		return dataPersisted;

	}

	public Future<Boolean> persistDataObjUserURL(String address, String guid, String type) {

		Future<Boolean> dataPersisted = Future.future();

		JsonObject document = new JsonObject();
		document.put("guid", guid);
		document.put("type", type);

		JsonObject toInsert = new JsonObject().put("url", address).put("metadata", document);
		logger.debug("Creating DO entry -> " + toInsert.toString());
		new Thread(() -> {

			mongoClient.save(dataObjectsCollection, toInsert, res2 -> {
				logger.debug("Setup complete - dataobjects + Insert" + res2.result().toString());
				dataPersisted.complete(res2.succeeded());
			});

		}).start();

		return dataPersisted;

	}

	/**
	 * create(dataObjectUrl, observers, initialData ) functions.
	 *
	 * @return
	 */
	public DataObjectReporter create(JsonObject identity, String dataObjectUrl, JsonObject initialData,
			boolean toInvite, Handler<Message<JsonObject>> subscriptionHandler,
			Handler<Message<JsonObject>> readHandler) {
		/**
		 * type: "create", from: "dataObjectUrl/subscription", body: { source:
		 * <hypertyUrl>, schema: <catalogueURL>, value: <initialData> }
		 */
		logger.debug("[AbstractHyperty] " + observers);
		JsonObject toSend = new JsonObject();
		toSend.put("type", "create");
		toSend.put("from", dataObjectUrl + "/subscription");

		JsonObject body = new JsonObject();
		body.put("source", this.url);
		body.put("schema", this.schemaURL);
		body.put("value", initialData);
		toSend.put("body", body);
		if (identity != null) {
			toSend.put("identity", identity);
		}

		logger.debug("[AbstractHyperty] data to send to observers->" + toSend.toString());

		if (toInvite) {
			Iterator it = observers.getList().iterator();
			while (it.hasNext()) {
				String observer = (String) it.next();
				send(observer, toSend, reply -> {
					logger.debug("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] "
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
		logger.debug("validating source ... from:" + from + "\nobservers:" + observers.getList().toString()
				+ "\nourUserURL:" + this.identity.getJsonObject("userProfile").getString("userURL") + "\nCOLLECTION:"
				+ collection);

		if (observers.getList().contains(from)) {
			logger.debug("VALID");
			return true;
		} else {
			JsonObject toFind = new JsonObject().put("identity", identity);
			logger.debug("toFIND" + toFind.toString());

			Future<Boolean> findWallet = Future.future();

			new Thread(() -> {
				mongoClient.find(collection, toFind, res -> {
					if (res.result().size() != 0) {
						JsonObject wallet = res.result().get(0);
						logger.debug("to subscribe add:" + address + " wallet to compare" + wallet);

						if (address.equals(wallet.getString("address"))) {
							logger.debug("RIGHT WALLET");
							if (wallet.getJsonObject("identity").equals(identity)) {
								logger.debug("RIGHT IDENTITY");
								findWallet.complete(true);
							}
							findWallet.complete(false);
							return;

						} else {
							logger.debug("OTHER WALLET");
							findWallet.complete(false);
							return;

						}
					}

				});
			}).start();

			logger.debug("3 - return other");
			return acceptSubscription;

		}

		// return false;
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
