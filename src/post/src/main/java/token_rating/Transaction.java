package token_rating;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Transaction {

	/**
	 * wallet address of the recipient
	 */
	@SerializedName("recipient")
	@Expose
	private String recipient;
	
	/**
	 * data stream address
	 */
	@SerializedName("source")
	@Expose
	private String source;
	
	/**
	 * ISO 8601 compliant
	 */
	@SerializedName("date")
	@Expose
	private String date;
	
	/**
	 * amount of tokens in the transaction
	 */
	@SerializedName("value")
	@Expose
	private int value;
	
	/**
	 * the count of the number of performed mining transactions,
	 * starting with 0
	 */
	@SerializedName("nonce")
	@Expose
	private String nonce;

	public String getRecipient() {
		return recipient;
	}

	public void setRecipient(String recipient) {
		this.recipient = recipient;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public String getNonce() {
		return nonce;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

}