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

package unused;

import java.util.HashSet;
import java.util.Set;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import util.Runner;
import io.vertx.ext.mongo.MongoClient;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class AppDBIntegration extends AbstractVerticle {
  private MongoClient mongoClient = null;
  public static void main(String[] args) {
    Runner.runExample(AppDBIntegration.class);
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
    allowedMethods.add(HttpMethod.DELETE);
    
    // Create Router object
    Router router = Router.router(vertx);
    
    //router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
    
    
    // handle post
    router.route("/requestpub*").handler(BodyHandler.create());
    router.post("/requestpub/dataIN").handler(this::handleDataIN);
    // handle get
	router.get("/requestpub/dataOUT").handler(this::handleDataOUT);
	router.delete("/requestpub/dataDELETE").handler(this::handleDataDELETE);
	router.post("/requestpub/dataUPDATE").handler(this::handleDataUPDATE);
	

	
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
	
	final HttpServer server = vertx.createHttpServer(options).requestHandler(router::accept);
	server.listen(9092);

	
	String uri = "mongodb://localhost:27017";
	
	String db = "test";
	
    JsonObject mongoconfig = new JsonObject()
            .put("connection_string", uri)
            .put("db_name", db);

    mongoClient = MongoClient.createShared(vertx, mongoconfig);
	
    System.out.println("Ready DATA INTEGRATION");
  }


	private void handleDataIN(RoutingContext routingContext) {
		
		System.out.println("POST to in");
		System.out.println(routingContext.getBodyAsString());
	
		JsonObject document = new JsonObject(routingContext.getBodyAsString());
		
	    mongoClient.save("collection", document, id -> { System.out.println("New Document with ID:" + id);
	    
		
		HttpServerResponse httpServerResponse = routingContext.response();
		httpServerResponse.putHeader("Content-Type", "application/text").setStatusMessage("OK").end();   
	    
    
	    });

		
	 }
	
	
	private void handleDataOUT(RoutingContext routingContext) {
		System.out.println("get to out");
        //mongoClient.find("collection", new JsonObject().put("attr1", "value1"), res -> {
		mongoClient.find("collection", new JsonObject(), res -> {
            System.out.println(res.result().size() + " <-value returned" + res.result().toString());
    		HttpServerResponse httpServerResponse = routingContext.response();

    		httpServerResponse.putHeader("Content-Type", "application/json").setStatusMessage("OK").end(res.result().toString());
            
        });

	}
	
	private void handleDataDELETE(RoutingContext routingContext) {
		System.out.println("DELETE");
		System.out.println(routingContext.getBodyAsString());
	
		JsonObject document = new JsonObject(routingContext.getBodyAsString());
		
		//mongoClient.removeDocument("collection", document, rs -> {
		mongoClient.removeDocuments("collection", document, rs -> {
	        if (rs.succeeded()) {
	          System.out.println("Product removed ");
	        }
	        
	        HttpServerResponse httpServerResponse = routingContext.response();

    		httpServerResponse.putHeader("Content-Type", "application/json").setStatusMessage("OK").end();
	      });
		
	}
	
	private void handleDataUPDATE(RoutingContext routingContext) {
		System.out.println("UPDATE");
		System.out.println(routingContext.getBodyAsString());
	
		JsonObject document = new JsonObject(routingContext.getBodyAsString());
		JsonObject query = document.getJsonObject("query");
		JsonObject update= document.getJsonObject("update");
		String colletion = document.getString("collection");
	
		mongoClient.updateCollection(colletion, query, update, res -> {

	        HttpServerResponse httpServerResponse = routingContext.response();

    		httpServerResponse.putHeader("Content-Type", "application/json").setStatusMessage("OK").end();
	      });
		
	}
}
