package util;

import java.util.Random;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InitialData {
	
	private String id;
	private JsonArray values;
	
	
	public InitialData(JsonArray values) {
		this.values = values;
		this.id = newID(7);
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public JsonArray getValues() {
		return values;
	}
	public void setValues(JsonArray values) {
		this.values = values;
	}

	private String newID(int length) {
		StringBuilder sb = new StringBuilder();
		sb.append("_");
		Random r = new Random();
		
		for (int x = 0; x<length; x++) {
			if (r.nextDouble() < 0.28) {
				sb.append(r.nextInt(10));
			} else {
				sb.append((char)(r.nextInt(26) + 'a'));
			}
		}
		return sb.toString();
	}

	public JsonObject getJsonObject() {
		return new JsonObject().put("id", this.id).put("values", this.values);
	}

}
