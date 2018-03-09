package rest.post;

import java.util.function.Consumer;

import com.google.gson.Gson;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import token_rating.CheckInRatingHyperty;
import token_rating.TokenMessage;

public class StartJavaHyperties extends AbstractVerticle {

	int toTest;
	private static String from = "tester";
	
	public static void main(String[] args) {
	
		//Vertx.clusteredVertx(options, res -> {
		Consumer<Vertx> runner = vertx -> {
			StartJavaHyperties startHyperties = new StartJavaHyperties();
			vertx.deployVerticle(startHyperties);
		};
		
		final ClusterManager mgr = new HazelcastClusterManager();
		final VertxOptions vertxOptions = new VertxOptions().setClustered(true).setClusterManager(mgr);
		
		Vertx.clusteredVertx(vertxOptions, res -> {
			Vertx vertx = res.result();
			runner.accept(vertx);
		});
		
	}


	private static Handler<RoutingContext> eventBusHandler(Vertx vertx) {
		BridgeOptions options = new BridgeOptions()
	            .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"))
	            .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"));
	    return SockJSHandler.create(vertx).bridge(options, event -> {
	    	if (BridgeEventType.PUBLISH == event.type() || BridgeEventType.SEND == event.type()) {
	    		System.out.println("BUS HANDLER:(" + event.type() + ") MESSAGE:" + event.getRawMessage());
	    	} else {
	    		System.out.println("BUS HANDLER:(" + event.type() + ")");
	    	}
	        event.complete(true);
	        
	    });
	}
	
	public void start() throws Exception { 
		// Create Router object
	    Router router = Router.router(vertx);
	    
		// web sockets
		router.route("/eventbus/*").handler(eventBusHandler(vertx));
		

		//Deploy extra Verticles
		String locationHypertyURL = "school://sharing-cities-dsm/location-url";
		String locationHypertyIdentity = "school://sharing-cities-dsm/location-identity";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);
		
		// deply location hyperty
		vertx.deployVerticle(LocationHyperty.class.getName(), optionsLocation, res -> {
			System.out.println("Location Deploy Result->" + res.result());
		});
		
		// deply check-in rating hyperty
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), res -> {
			System.out.println("CheckInRatingHyperty Result->" + res.result());
			sendCreateMessage();
			sendToStream();
		});
		
		
		
		//Configure HttpServer and set it UP
		int BUFF_SIZE = 32 * 1024;
		final JksOptions jksOptions = new JksOptions()
				.setPath("server-keystore.jks")
				.setPassword("rethink2015");
		
		HttpServerOptions httpOptions = new HttpServerOptions().setMaxWebsocketFrameSize(6553600)
															.setTcpKeepAlive(true)
															.setSsl(true)
															.setKeyStoreOptions(jksOptions)
															.setReceiveBufferSize(BUFF_SIZE)
															.setAcceptBacklog(10000)
															.setSendBufferSize(BUFF_SIZE);
		final HttpServer server = vertx.createHttpServer(httpOptions).requestHandler(router::accept).websocketHandler(new Handler<ServerWebSocket>() {
	        public void handle(final ServerWebSocket ws) {
	        	
				final StringBuilder sb = new StringBuilder();
				System.out.println("RESOURCE-OPEN");
				ws.frameHandler(frame -> {
					sb.append(frame.textData());

					if (frame.isFinal()) {
						System.out.println("RESOURCE isFinal -> Data:" + sb.toString());
						ws.writeFinalTextFrame("received");
						sb.delete(0, sb.length());
					}
				});
				ws.closeHandler(handler -> {
					System.out.println("RESOURCE-CLOSE");
				});
	      }
	  });
				
		server.listen(9091);    

	    
	    /*toTest = 0;
	    vertx.setPeriodic(5000, _id -> {
		    try {
		    	
					toTest++;
					vertx.eventBus().publish(locationHypertyURL, "" + toTest);
				
		    } catch(Exception e) {
		    	System.out.println("Error->");
		    	e.printStackTrace();
		    	
		    }
	    });*/	
	}
	
	public  void sendCreateMessage() {
		TokenMessage msg = new TokenMessage();
		msg.setType("create");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}

	static int msgID;

	public void sendToStream() {
		String message = "12";

		msgID = 0;

		vertx.setPeriodic(2000, _id -> {
			msgID++;
			vertx.eventBus().publish(from, message);

			if (msgID >= 5) {
				tearDownStream();
				vertx.cancelTimer(_id);
			}
		});

	}

	public void tearDownStream() {
		TokenMessage msg = new TokenMessage();
		msg.setType("delete");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}

	
}
