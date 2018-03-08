package rest.post;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import util.InitialData;


public class AbstractHyperty extends AbstractVerticle{
	
	private String url;
	private String identity;
	private EventBus eb;
	private JsonObject data;

	
	@Override
	public void start() throws Exception {
		this.url = config().getString("url");
		this.identity = config().getString("identity");
		this.eb = vertx.eventBus();
		this.eb.<JsonObject>consumer(this.url, onMessage());
		this.data = new InitialData(new JsonObject()).getJsonObject();
	}

	public void send (String address, String message, Handler replyHandler) {
		final String type = new JsonObject(message).getString("type");
			
		final DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
						.addHeader("identity", this.identity)
						.addHeader("type", type);
		
		this.eb.send(address, message, deliveryOptions, replyHandler);
	}
	
	
	public void publish (String address, String message) {
		String type = new JsonObject(message).getString("type");
		
		
		DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
						.addHeader("identity", this.identity)
						.addHeader("type", type);
		
		this.eb.publish(address, message, deliveryOptions);
	}
		
	
	private Handler<Message<JsonObject>> onMessage() {
		return message -> {
		        System.out.println("[NewData] -> [Worker]-" + Thread.currentThread().getName() + "\n[Data] " + message.body().toString());
		        
		        JsonObject body = new JsonObject(message.body().toString());
		        if (body.getString("type").equals("read")) {
		        	JsonObject response = new JsonObject().put("data", this.data).put("identity", this.identity);
		        	message.reply(response);
		        }
		        
		      };
	}

}
