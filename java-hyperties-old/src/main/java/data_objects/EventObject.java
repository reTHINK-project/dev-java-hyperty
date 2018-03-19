package data_objects;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class EventObject extends AbstractVerticle {

	private String type;
	private String url;
	private String domain;
	private String identity;
	private boolean mutual;

	
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getIdentity() {
		return identity;
	}

	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public boolean getMutual() {
		return mutual;
	}

	public void setMutual(boolean mutual) {
		this.mutual = mutual;
	}

}
