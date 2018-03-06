package rest.post;

import java.util.function.Consumer;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class LocationHyperty extends AbstractHyperty {
	
	/*public static void main(String[] args) {
		
		Consumer<Vertx> runner = vertx -> {

			
			JsonObject config = new JsonObject().put("url", "urlstring").put("identity", "identitystring");
			DeploymentOptions optionsLocation = new DeploymentOptions().setConfig(config).setWorker(true);
			
			LocationHyperty location = new LocationHyperty();
			vertx.deployVerticle(location, optionsLocation );
		};
		
		final ClusterManager mgr = new HazelcastClusterManager();
		final VertxOptions vertxOptions = new VertxOptions().setClustered(true).setClusterManager(mgr);
		
		
		Vertx.clusteredVertx(vertxOptions, res -> {
			vertx = res.result();
			runner.accept(vertx);
		});
		
	
		
		
	}
	@Override
	public void start() throws Exception {
	  
	  vertx.setPeriodic(5000, _id -> {

		    vertx.eventBus().send(
		            "urlstring",
		            "hello vert.x",
		            r -> {
		              System.out.println("[Main] Receiving reply ' " + r.result().body()
		                  + "' in " + Thread.currentThread().getName());
		            }
		        );
		});	
	  	  
	}*/
	
	

}
