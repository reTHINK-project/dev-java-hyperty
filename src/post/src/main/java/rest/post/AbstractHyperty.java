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


public class AbstractHyperty extends AbstractVerticle{
	
	private Vertx vertx;
	private String url;
	private String identity;
	private EventBus eb;
	private Context context;
	

	public void init(Vertx vertx, Context context) {
		this.vertx = vertx;
		this.context = context;
		this.url = context.get("url");
		this.identity = context.get("identity");
		this.eb = vertx.eventBus();
	}
	
	public void start(Future<Void> startFuture) throws Exception {

		
		start();
	    //startFuture.complete();
	    
	}
	public void start() throws Exception {
	  this.eb.consumer(this.url, onMessage());
		  
	}


	//Set from and identity headers before calling eb.send(..).
	public void send (String address, String message, Handler replyHandler) {
		String type = new JsonObject(message).getString("type");
		
		
		DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
						.addHeader("identity", this.identity)
						.addHeader("type", type);
		
		this.eb.send(address, message, deliveryOptions, replyHandler);
	}
	
	
	/**Set from and identity headers before calling eb.publish(..).
	 * 
	 * from with value config().getString("url"),
	 * identity with value config().getString("identity"),
	 * type with value set by the Hyperty itself e.g. create
	 */
	
	public void publish (String address, String message) {
		String type = new JsonObject(message).getString("type");
		
		
		DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("from", this.url)
						.addHeader("identity", this.identity)
						.addHeader("type", type);
		
		vertx.eventBus().publish(address, message, deliveryOptions);
	}
		
	private Handler<Message<Object>> onMessage() {
		return message -> {
	    	System.out.println("CONSUMER:  message:" + message);
	    	};
	}

}
