package rest.post;

import java.util.function.Consumer;

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

public class StartJavaHyperties extends AbstractVerticle {

	int toTest;
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
	    	if (BridgeEventType.PUBLISH == event.type()) {
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
		String locationHypertyURL = "vertx://location-hyperty-url/";
		String locationHypertyIdentity = "vertx://location-hyperty-identity/";
		JsonObject config = new JsonObject().put("url", locationHypertyURL).put("identity", locationHypertyIdentity);
		DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);
		vertx.deployVerticle("rest.post.LocationHyperty", optionsLocation);
		
		
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
		
		final HttpServer server = vertx.createHttpServer(httpOptions).requestHandler(router::accept).listen(9091);    
	    
	    toTest = 0;
	    vertx.setPeriodic(5000, _id -> {
		    try {
		    	
					toTest++;
					vertx.eventBus().publish("urlstring", "" + toTest);
				
		    } catch(Exception e) {
		    	System.out.println("Error->");
		    	e.printStackTrace();
		    	
		    }
	    });	
	}
	
	
}
