/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package rest.post;

import java.util.HashSet;
import java.util.Set;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import util.Runner;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class App extends AbstractVerticle {
  public int lastValue = 0;
  private boolean alreadyRegister = false;
  public static void main(String[] args) {
    Runner.runExample(App.class);
  }


  @Override
  public void start() {
	
	  
	Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add("x-requested-with");
    allowedHeaders.add("Access-Control-Allow-Origin");
    allowedHeaders.add("origin");
    allowedHeaders.add("Content-Type");
    allowedHeaders.add("accept");
    allowedHeaders.add("cache-control");
    allowedHeaders.add("version");
    allowedHeaders.add("Accept-Encoding");
    allowedHeaders.add("USER-AGENT");
    allowedHeaders.add("CONTENT-LENGTH");
    
    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    
    // Create Router object
    Router router = Router.router(vertx);
    
    //router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    
    
    // handle post
    router.route("/requestpub*").handler(BodyHandler.create());
    router.post("/requestpub").handler(this::handleRequestPub);
    // handle get
	router.get("/").handler(this::handleGetRoot);
    
	// web sockets
	router.route("/eventbus/*").handler(eventBusHandler());
    
	// create a periodic event
	/*
	vertx.setPeriodic(3000, _id -> {
			int toSend = lastValue+=10;
			// publish value on event bus
			vertx.eventBus().publish("school://vertx-app/stream", toSend);
	});		
	*/							
	
	
	int BUFF_SIZE = 32 * 1024;
	final JksOptions jksOptions = new JksOptions()
			.setPath("server-keystore.jks")
			.setPassword("rethink2015");
	
	HttpServerOptions options = new HttpServerOptions().setMaxWebsocketFrameSize(6553600)
														.setTcpKeepAlive(true)
														.setSsl(true)
														.setKeyStoreOptions(jksOptions)
														.setReceiveBufferSize(BUFF_SIZE)
														.setAcceptBacklog(10000)
														.setSendBufferSize(BUFF_SIZE);
	
	final HttpServer server = vertx.createHttpServer(options).requestHandler(router::accept).websocketHandler(new Handler<ServerWebSocket>() {
        public void handle(final ServerWebSocket ws) {
        	
			final StringBuilder sb = new StringBuilder();
			System.out.println("RESOURCE-OPEN");
			ws.frameHandler(frame -> {
				sb.append(frame.textData());

				if (frame.isFinal()) {
					System.out.println("RESOURCE isFinal -> Data:" + sb.toString());
					vertx.eventBus().publish("vertx.app.received", sb.toString());
					
					JsonObject received = new JsonObject(sb.toString());
					if(received.getInteger("id").equals(1)) {
						JsonObject obj = new JsonObject().put("from", received.getString("to"))
								                         .put("type", "response")
								                         .put("id", received.getInteger("id"))
								                         .put("to", received.getString("from"))
								                         .put("body", new JsonObject().put("code", 200).put("runtimeToken", "dasdasdas231231asd1"));
						 ws.writeFinalTextFrame(obj.toString());
					} else {
						ws.writeFinalTextFrame("received");
					}
					sb.delete(0, sb.length());
					
				}
			});
			ws.closeHandler(handler -> {
				System.out.println("RESOURCE-CLOSE");
			});
      }
  });
	
    server.listen(9091);
    vertx.eventBus().consumer("school://vertx-app", message -> {
    	System.out.println("CONSUMER: ADDRESS(" + message.address() + ") | message:" + message.body());
    	System.out.println("PUBLISH: ADDRESS(school://vertx-app/stream) | VALUE:" + lastValue );    	
    	vertx.eventBus().publish("school://vertx-app/stream", lastValue);
    	});
    vertx.eventBus().consumer("school://vertx-app/stream", message -> { 
    	System.out.println("CONSUMER: ADDRESS(" + message.address() + ") | message:" + message.body());
    	});
    
    
    boolean alreadyRegister = false;
    vertx.eventBus().consumer("school://vertx-app/announcements", message -> {
    	System.out.println("CONSUMER: ADDRESS(" + message.address() + ") | message:" + message.body());
    	
    	vertx.eventBus().publish("school://vertx-app/subscription", message.body());
    	//vertx.eventBus().consumer()
    	JsonObject receivedObj = new JsonObject(message.body().toString());
    	JsonArray arrayEvents = receivedObj.getJsonArray("events");
    	JsonObject event = arrayEvents.getJsonObject(0);
    	

    	if (!this.alreadyRegister) {
    		this.alreadyRegister  = true;
    		System.out.println("CHANGES-CONSUMER" + event.getString("url"));
            vertx.eventBus().consumer("" + event.getString("url"), message2 -> {
            	System.out.println("CHANGES-OBJ - " + message2.address() + ") | message:" + message2.body());
            	});
    	}


    	});
    vertx.eventBus().consumer("school://vertx-app/location-changes", message -> {
    	System.out.println("CONSUMER: ADDRESS(" + message.address() + ") | message:" + message.body());

    	});
    
    System.out.println("Ready");
  }

	private Handler<RoutingContext> eventBusHandler() {
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


	private void handleRequestPub(RoutingContext routingContext) {
		
		System.out.println("POST");
		System.out.println(routingContext.getBodyAsString());
	

		//System.out.println("ENDPOINT POST RECEIVED DATA -> "+ routingContext.getBodyAsString().toString()); 
		
		
    	System.out.println("PUBLISH: ADDRESS(school://vertx-app/stream) | VALUE:" + lastValue );    	
		vertx.eventBus().publish("school://vertx-app/stream", routingContext.getBodyAsString().toString());
		
		lastValue = Integer.parseInt(routingContext.getBodyAsString().toString());
		HttpServerResponse httpServerResponse = routingContext.response();
		httpServerResponse.setChunked(true);
		MultiMap headers = routingContext.request().headers();
		for (String key : headers.names()) {
			httpServerResponse.write(key + ": ");
			httpServerResponse.write(headers.get(key));
			httpServerResponse.write("<br>");
		}
		httpServerResponse.putHeader("Content-Type", "application/text").end();   
		
	 }
	
	
	private void handleGetRoot(RoutingContext routingContext) {
		HttpServerResponse httpServerResponse = routingContext.response();
		httpServerResponse.setChunked(true);
		MultiMap headers = routingContext.request().headers();
		for (String key : headers.names()) {
			httpServerResponse.write(key + ": ");
			httpServerResponse.write(headers.get(key));
			httpServerResponse.write("<br>");
		}
		httpServerResponse.putHeader("Content-Type", "text/html").end();
		System.out.println("HANDLING GET");
	}
}
