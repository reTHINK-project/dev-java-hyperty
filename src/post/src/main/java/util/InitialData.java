package util;

import java.util.Random;

import io.vertx.core.json.JsonObject;

public class InitialData {
	
	private String id;
	private JsonObject values;
	
	
	public InitialData(JsonObject values) {
		this.values = values;
		this.id = newID(7);
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public JsonObject getValues() {
		return values;
	}
	public void setValues(JsonObject values) {
		this.values = values;
	}

	private String newID(int length) {
		StringBuilder sb = new StringBuilder();
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
