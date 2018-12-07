package runHyperties;

import io.vertx.core.json.JsonObject;

public class Account {

	/**
	 * name of the account
	 */
	public String name;
	/**
	 * total ammount of tokens of this account
	 */
	public int totalBalance;
	/**
	 * ammount of tokens earned in the last period of time.
	 */
	public int lastBalance;
	/**
	 * total ammount of data that was used to generated the total ammount of tokens
	 */
	public int totalData;
	/**
	 * ammount of data accounted in the last period of time.
	 */
	public int lastData;
	/**
	 * measurement unit used for data.
	 */
	public String dataUnit;
	/**
	 * period of time used to calculate lastBalance and lastData
	 */
	public String lastPeriod;
	public String description;

	public Account(String name, String dataUnit) {
		this.name = name;
		this.totalBalance = 0;
		this.totalData = 0;
		this.lastBalance = 0;
		this.lastData = 0;
		this.dataUnit = dataUnit;
		this.lastPeriod = name.equals("energy-saving") ? "month" : "week";
		this.description = "";
	}

	public JsonObject toJsonObject() {
		JsonObject toJson = new JsonObject();
		toJson.put("name", name);
		toJson.put("totalBalance", totalBalance);
		toJson.put("totalData", totalData);
		toJson.put("lastBalance", lastBalance);
		toJson.put("lastData", lastData);
		toJson.put("dataUnit", dataUnit);
		toJson.put("lastPeriod", lastPeriod);
		toJson.put("description ", description);
		return toJson;
	}

	public static Account toAccount(JsonObject json) {
		Account account = new Account(json.getString("name"), json.getString("dataUnit"));
		account.totalBalance = json.getInteger("totalBalance");
		account.totalData = json.getInteger("totalData");
		account.lastBalance = json.getInteger("lastBalance");
		account.lastData = json.getInteger("lastData");
		account.lastPeriod = json.getString("lastPeriod");
		account.description = json.getString("description");
		return account;
	}

}
