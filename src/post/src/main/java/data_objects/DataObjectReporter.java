package data_objects;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class DataObjectReporter extends DataObject {

	private EventBus eb;
	private Handler<Message<JsonObject>> onSubscriptionHandler;
	String[] subscriptions;

	public DataObjectReporter(String dataObjectUrl) {
		this.eb = vertx.eventBus();
		this.eb.consumer(dataObjectUrl + "/subscription", onSubscription());
	}

	/**
	 * Setup the handler for incoming subscriptions.
	 */
	private Handler<Message<JsonObject>> onSubscription(Handler<Message<JsonObject>> handler) {
		return message -> {
			System.out.println("Reporter is handling subscriptions");
			// setup the handler for subscriptions
			onSubscriptionHandler = handler;
		};
	}

	/**
	 * Receive subscribe message and pass it on to the handler.
	 * 
	 * @param msg
	 */
	private void onSubscribe(JsonObject msg) {

		String hypertyUrl = msg.getString("from");
		JsonObject dividedURL = divideURL(hypertyUrl);
		String domain = dividedURL.getString("domain");
		boolean mutual = true;

		if (msg.containsKey("mutual") && !msg.containsKey("mutual"))
			mutual = false;

		System.out.println("[DataObjectReporter._onSubscribe]" + msg + domain + dividedURL);

		// create event
		EventObject event = new EventObject();
		event.setType(msg.getString("type"));
		event.setUrl(hypertyUrl);
		event.setDomain(domain);
		event.setIdentity(msg.getString("identity"));
		event.setMutual(mutual);

		if (onSubscriptionHandler != null) {
			System.out.println("SUBSCRIPTION-EVENT: " + event);
			onSubscriptionHandler(event);
		}
	}

	public static Object deepClone(Object object) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(object);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private void accept(){
        //create new subscription
        JsonObject sub = new JsonObject();
        sub.put("url", hypertyUrl);
        sub.put("status","live");
        	
        subscriptions[hypertyUrl] = sub;
        
        if (metadata.containsKey("subscriptions")) {
        	metadata.getJsonArray("subscriptions").add(sub.getString("url")); 
        	}

        String msgValue = (String) deepClone(metadata);
        msgValue.data = deepClone(data);
        msgValue.version = _this._version;
        
        JsonObject sendMsg = new JsonObject();
        sendMsg.put("id", msg.getString("id"));
        sendMsg.put("type", "response");
        sendMsg.put("from",  msg.getString("to");
        sendMsg.put("to",  msg.getString("from");
        JsonObject sendMsgBody = new JsonObject();
        sendMsgBody.put("code", 200);
        sendMsgBody.put("schema", schema);
        sendMsgBody.put("value", msgValue);
    

        //TODO: For Further Study
        if (msg.body.hasOwnProperty("mutual") && !msg.body.mutual) {
          sendMsg.body.mutual = msg.body.mutual;// TODO: remove?
          _this.data.mutual = false;
        }

        //send ok response message
        _this._bus.postMessage(sendMsg);

        return sub;
      }

	private void reject(String reason){
		
        //send reject response message
        _this._bus.postMessage({
          id: msg.id, type: 'response', from: msg.to, to: msg.from,
          body: { code: 403, desc: reason }
        });
      }

	private String[] recurse(String value) {
		String regex = "/([a-zA-Z-]*)(:\\/\\/(?:\\.)?|:)([-a-zA-Z0-9@:%._\\+~#=]{2,256})([-a-zA-Z0-9@:%._\\+~#=\\/]*)/gi";
		String subst = "$1,$3,$4";
		String[] parts = value.replace(regex, subst).split(",");
		return parts;
	}

	private JsonObject divideURL(String url) {
		String[] parts = recurse(url);

		// If the url has no scheme
		if (parts[0].equals(url) && !parts[0].contains("@")) {
			JsonObject result = new JsonObject();
			result.put("type", "");
			result.put("domain", url);
			result.put("identity", "");

			System.out.println(
					"[DivideURL] DivideURL don't support url without scheme. Please review your url address" + url);

			return result;
		}

		// check if the url has the scheme and includes an @
		if (parts[0].equals(url) && parts[0].contains("@")) {
			String scheme = (parts[0].equals(url)) ? "smtp" : parts[0];
			parts = recurse(scheme + "://" + parts[0]);
		}

		// if the domain includes an @, divide it to domain and identity respectively
		if (parts[1].contains("@")) {
			parts[2] = parts[0] + "://" + parts[1];
			parts[1] = parts[1].substring(parts[1].indexOf('@') + 1);
		}

		JsonObject result = new JsonObject();
		result.put("type", parts[0]);
		result.put("domain", parts[1]);
		result.put("identity", parts[2]);

		return result;
	}

}
