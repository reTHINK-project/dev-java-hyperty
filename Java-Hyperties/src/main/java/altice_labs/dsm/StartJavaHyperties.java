package altice_labs.dsm;



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
import io.vertx.core.json.JsonArray;
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
import token_rating.WalletManagerHyperty;
import token_rating.WalletManagerMessage;
import unused.LocationHyperty;

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
		
		String checkINHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
		
		// Create Router object
	    Router router = Router.router(vertx);
	    
		// web sockets
		router.route("/eventbus/*").handler(eventBusHandler(vertx));

		
		/*
		String testHypertyURL = "hyperty://sharing-cities-dsm/test";
		JsonObject identityTest  = new JsonObject().put("userProfile", new JsonObject().put("userURL", "user://sharing-cities-dsm/test-identity"));
		JsonObject configTest = new JsonObject().put("url", testHypertyURL).put("identity", identityTest);

		configTest.put("collection", "location_data");
		configTest.put("db_name", "test");
		configTest.put("mongoHost", "localhost");
		configTest.put("streams", new JsonArray());
		configTest.put("schemaURL", "schemaurl");

		
		//									.put("collection", "location_data").put("database", "test").put("mongoHost", "localhost")
		//									.put("schemaURL", "hyperty-catalogue://catalogue.localhost/.well-known/dataschema/Context");
		DeploymentOptions optionsTest= new DeploymentOptions().setConfig(configTest).setWorker(true);

		vertx.deployVerticle(LocationHyperty.class.getName(), optionsTest, res -> {
			System.out.println("LocationHyperty Result->" + res.result());
		});
		*/
		
		// deploy check-in rating hyperty
		JsonObject identityCheckIN  = new JsonObject().put("userProfile", new JsonObject().put("userURL", "user://sharing-cities-dsm/checkin-identity"));
		JsonObject configCheckIN = new JsonObject();
		configCheckIN.put("url", checkINHypertyURL);
		configCheckIN.put("identity", identityCheckIN);
		configCheckIN.put("db_name", "test");
		configCheckIN.put("collection", "rates");
		configCheckIN.put("mongoHost", "localhost");
		
		configCheckIN.put("tokens_per_checkin", 10);
		configCheckIN.put("checkin_radius", 500);
		configCheckIN.put("min_frequency", 1);
		configCheckIN.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");		
		configCheckIN.put("hyperty", "123");
		configCheckIN.put("stream", "token-rating");
		
		
		DeploymentOptions optionsCheckIN= new DeploymentOptions().setConfig(configCheckIN).setWorker(true);
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsCheckIN, res -> {
			System.out.println("CheckInRatingHyperty Result->" + res.result());
		});
		
	
		// wallet manager hyperty deploy
		
		JsonObject identityWalletManager  = new JsonObject().put("userProfile", new JsonObject().put("userURL", "user://sharing-cities-dsm/wallet-manager"));

		JsonObject configWalletManager  = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identityWalletManager);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", "localhost");
		
		configWalletManager.put("observers", new JsonArray().add(""));
		
		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager).setWorker(true);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
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
	
	
	
	
	
	
	
	
	
	
/*	public  void sendCreateMessage() {
		WalletManagerMessage msg = new WalletManagerMessage();
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
		WalletManagerMessage msg = new WalletManagerMessage();
		msg.setType("delete");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}*/

	
}
