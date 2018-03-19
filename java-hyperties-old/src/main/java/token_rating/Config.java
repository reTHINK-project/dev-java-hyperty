package token_rating;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Config {

	/**
	 * hyperty address where to setup an handler to process invitations in case the data source is dynamic eg produced by the smart citizen
	 */
	@SerializedName("hyperty")
	@Expose
	private String hyperty;
	
	/**
	 * the stream address to setup the handler in case the address is static e.g. when the stream is produced via the Smart IoT.
	 */
	@SerializedName("stream")
	@Expose
	private String stream;
	
	/**
	 * Wallet Manager Hyperty address.
	 */
	@SerializedName("wallet")
	@Expose
	private String wallet;

	public String getHyperty() {
		return hyperty;
	}

	public void setHyperty(String hyperty) {
		this.hyperty = hyperty;
	}

	public String getStream() {
		return stream;
	}

	public void setStream(String stream) {
		this.stream = stream;
	}

	public String getWalletManagerAddress() {
		return wallet;
	}

	public void setWallet(String wallet) {
		this.wallet = wallet;
	}

}