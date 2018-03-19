package token_rating;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class WalletManagerMessage {
	
	public static String TYPE_CREATE = "create";
	public static String TYPE_READ = "read";
	public static String TYPE_DELETE = "delete";


	@SerializedName("type")
	@Expose
	private String type;
	
	@SerializedName("identity")
	@Expose
	private String identity;
	

	@SerializedName("from")
	@Expose
	private String from;
	

	@SerializedName("body")
	@Expose
	private String body;


	public String getType() {
		return type;
	}


	public void setType(String type) {
		this.type = type;
	}


	public String getFrom() {
		return from;
	}


	public void setFrom(String from) {
		this.from = from;
	}


	public String getBody() {
		return body;
	}


	public void setBody(String body) {
		this.body = body;
	}


	public String getIdentity() {
		return identity;
	}


	public void setIdentity(String identity) {
		this.identity = identity;
	}

	
	
	

}