package token_rating;

import org.junit.Before;
import org.junit.runner.RunWith;

import com.google.gson.Gson;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class StartTokenRating {

	private static Vertx vertx;

	private static String from = "tester";

	@Before
	public static void main(String[] args) {
		vertx = Vertx.vertx();

		// pass configuration to vertical
		JsonObject config = new JsonObject().put("name", "tim").put("directory", "/blah");
		DeploymentOptions options = new DeploymentOptions().setConfig(config);
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), stringAsyncResult -> {
			System.out.println("BasicVerticle deployment complete");
			sendCreateMessage();
			sendToStream();
		});

	}

	public static void sendCreateMessage() {
		TokenMessage msg = new TokenMessage();
		msg.setType("create");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));

	}

	static int msgID;

	public static void sendToStream() {
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

	public static void tearDownStream() {
		TokenMessage msg = new TokenMessage();
		msg.setType("delete");
		msg.setFrom(from);
		Gson gson = new Gson();
		vertx.eventBus().publish("token-rating", gson.toJson(msg));
	}
}
