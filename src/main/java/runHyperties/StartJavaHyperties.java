package runHyperties;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.junit5.Checkpoint;
import io.vertx.ext.web.handler.BodyHandler;

import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import protostub.SmartIotProtostub;
import tokenRating.CheckInRatingHyperty;
import tokenRating.ElearningRatingHyperty;
import tokenRating.EnergySavingRatingHyperty;
import tokenRating.UserActivityRatingHyperty;
import walletManager.WalletManagerHyperty;

public class StartJavaHyperties extends AbstractVerticle {

	int toTest;
	private static String from = "tester";
	//private String mongoHost = "172.18.0.64";
	private String mongoHost = "172.20.0.64";
	// private String mongoHost = "localhost";
	private String SIOTurl = "https://iot.alticelabs.com/api";
	// private String SIOTurl = "http://10.112.77.148/api";
	private String pointOfContact = "https://url_contact";
	private MongoClient mongoClient = null;
	private CountDownLatch findDOUrl;

	public static void main(String[] args) {

		// Vertx.clusteredVertx(options, res -> {
		Consumer<Vertx> runner = vertx -> {
			StartJavaHyperties startHyperties = new StartJavaHyperties();
			vertx.deployVerticle(startHyperties);
		};

		final ClusterManager mgr = new HazelcastClusterManager();
		final VertxOptions vertxOptions = new VertxOptions().setClustered(true).setClusterManager(mgr);

		Vertx.clusteredVertx(vertxOptions, res -> {
			Vertx vertx = res.result();
			runner.accept(vertx);
		});

	}

	private static Handler<RoutingContext> eventBusHandler(Vertx vertx) {
		BridgeOptions options = new BridgeOptions().addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"))
				.addInboundPermitted(new PermittedOptions().setAddressRegex(".*"));
		return SockJSHandler.create(vertx).bridge(options, event -> {
			if (BridgeEventType.PUBLISH == event.type() || BridgeEventType.SEND == event.type()) {
				System.out.println("BUS HANDLER:(" + event.type() + ") MESSAGE:" + event.getRawMessage());
			} else {
				System.out.println("BUS HANDLER:(" + event.type() + ")");
			}
			event.complete(true);

		});
	}

	public void start() throws Exception {

		String checkINHypertyURL = "hyperty://sharing-cities-dsm/checkin-rating";
		String userActivityHypertyURL = "hyperty://sharing-cities-dsm/user-activity";
		String walletManagerHypertyURL = "hyperty://sharing-cities-dsm/wallet-manager";
		String elearningHypertyURL = "hyperty://sharing-cities-dsm/elearning";
		String energySavingRatingHypertyURL = "hyperty://sharing-cities-dsm/energy-saving-rating";
		String smartIotProtostubUrl = "runtime://sharing-cities-dsm/protostub/smart-iot";
		// Create Router object
		Router router = Router.router(vertx);

		// web sockets

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

		router.route("/requestpub*").handler(BodyHandler.create());
		router.post("/requestpub").handler(this::handleRequestPub);

		// web sockets
		router.route("/eventbus/*").handler(eventBusHandler(vertx));

		/*
		 * router.route().handler(BodyHandler.create());
		 * router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders)
		 * .allowedMethods(allowedMethods));
		 * 
		 * router.post("/requestpub").handler(this::handleRequestPub);
		 * 
		 * 
		 * router.route("/eventbus/*").handler(eventBusHandler(vertx));
		 * router.post("/requestpub").handler(this::handleRequestPub);
		 */

		// deploy check-in rating hyperty

		JsonObject identity = new JsonObject().put("userProfile",
				new JsonObject().put("userURL", "user://sharing-cities-dsm/identity"));

		/*
		 * JsonObject userProfile = new JsonObject(); userProfile.put("sub",
		 * "103980270434194733076"); userProfile.put("name", "openid test");
		 * userProfile.put("given_name", "openid"); userProfile.put("family_name",
		 * "test"); userProfile.put("picture",
		 * "https://lh5.googleusercontent.com/-FJv-j36pucE/AAAAAAAAAAI/AAAAAAAAAAo/DpjIXp9VOAw/photo.jpg"
		 * ); userProfile.put("email", "openidtest30@gmail.com");
		 * userProfile.put("email_verified", true); userProfile.put("locale", "en-GB");
		 * userProfile.put("userURL", "user://google.com/openidtest30@gmail.com");
		 * userProfile.put("preferred_username", "openidtest30");
		 * 
		 * JsonObject idp = new JsonObject(); idp.put("domain", "google.com");
		 * idp.put("protocol", "OIDC");
		 * 
		 * 
		 * JsonObject identityCheckIN = new JsonObject();
		 * identityCheckIN.put("userProfile", userProfile); identityCheckIN.put("idp",
		 * idp);
		 * 
		 * identityCheckIN.put("assertion",
		 * "eyJ0b2tlbklEIjoiZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNkltVTVZalUyWTJaak5qUXdaREV5WW1abU5EVTBNRFUxTXpRd01tTTNaakUxTjJRME9ERTRNRFlpZlEuZXlKaGVuQWlPaUk0TURnek1qazFOall3TVRJdGRIRnlPSEZ2YURFeE1UazBNbWRrTW10bk1EQTNkREJ6T0dZeU56ZHliMmt1WVhCd2N5NW5iMjluYkdWMWMyVnlZMjl1ZEdWdWRDNWpiMjBpTENKaGRXUWlPaUk0TURnek1qazFOall3TVRJdGRIRnlPSEZ2YURFeE1UazBNbWRrTW10bk1EQTNkREJ6T0dZeU56ZHliMmt1WVhCd2N5NW5iMjluYkdWMWMyVnlZMjl1ZEdWdWRDNWpiMjBpTENKemRXSWlPaUl4TURNNU9EQXlOekEwTXpReE9UUTNNek13TnpZaUxDSmxiV0ZwYkNJNkltOXdaVzVwWkhSbGMzUXpNRUJuYldGcGJDNWpiMjBpTENKbGJXRnBiRjkyWlhKcFptbGxaQ0k2ZEhKMVpTd2lZWFJmYUdGemFDSTZJakYxZDI1alJVTlNNRVJ4UkhCSlYzZHNhRU0wWTFFaUxDSnViMjVqWlNJNklsczBPQ3d4TXpBc01Td3pOQ3cwT0N3eE15dzJMRGtzTkRJc01UTTBMRGN5TERFek5Dd3lORGNzTVRNc01Td3hMREVzTlN3d0xETXNNVE13TERFc01UVXNNQ3cwT0N3eE16QXNNU3d4TUN3eUxERXpNQ3d4TERFc01Dd3lNemtzT0RJc01DdzNPU3d4TkRVc01qVXNPRElzTVRBNExESXlOQ3d5TXpZc01qSXpMREl5TWl3eE5UZ3NNQ3d5TVRZc01UWXpMREl5TUN3eE1UTXNNalF4TERreUxEVTRMRGswTERJek9Dd3hNVE1zTVRjNUxEYzFMRGd3TERJME15d3hOVGtzTWpFc01qRXdMRE0yTERFNE15dzJNU3d4TWpjc01UY3hMREV4Tnl3NE1Dd3lPQ3d4TkN3eU5EUXNOalVzTVRjc01qTXdMRGd4TERFd05Td3hPRElzTWpJM0xEWTJMREk0TERFMU9Dd3lNak1zTVRRd0xERXlOU3d5TXpnc01qTTBMREUzTVN3eU5URXNNVGswTERJd05Dd3hNallzT1RNc01UazRMREU0TERFNE9TdzBMRGc1TERJd05pd3hOREVzTWpRNExESXlNU3d5TXpBc05qRXNNVGN6TERFNU55d3hNRFVzTmpBc09EVXNOU3d4TWl3eU16WXNNakVzT0RJc01qRTFMREUzT1N3eU1URXNPVGtzTWpJMExEUXdMRE00TERJM0xERTFPQ3d5TVN3d0xESXpNQ3d4TUN3eE1qWXNORFFzT1RFc056UXNOREVzTkRJc01UYzJMREUzTnl3eE1URXNNak0xTERJek5pd3hNRGtzTVRjNUxETXpMREl6TkN3eE15dzFOaXcxTERJeE5Td3hPVElzTWpRMExERXlNU3d4TURVc01qTXNOelFzTWpNNExERTFOaXd4TlRJc016a3NPRElzTWpVd0xEUTRMREV6T1N3eE56TXNNVEkyTERFM01Dd3hOemdzTmpBc01USTFMRFUzTERJM0xESXdNeXd5TXpjc01UYzVMRFF3TERFd05pdzFOQ3d4TVRrc01UYzVMRGM0TERJeE5Dd3hORGdzTkRnc056RXNNVGc0TERJek1Dd3lOVElzT1Rrc09UTXNORFFzTVRRNExERTRNU3d4TWpJc01URTVMREl5TERFMk1TdzRNeXcwT1N3eE5qUXNORGNzTWpJNExEVTBMRGM0TERFeU9Dd3hPRFlzTVRFMkxERXhNaXczTnl3eU1EY3NNVFl3TERJNUxERTJNQ3d4TWpBc09UVXNNVEk0TERVd0xERTNNeXd5TXpFc01UWTFMRFkwTERrMUxESXpOeXc0TlN3eE56a3NOVElzTVRNMExERTFOaXd5TWpFc01URXlMRGd3TERJd01Dd3hNRFFzT1RBc09TdzFNeXcxTkN3eU5pd3hNamdzTmpFc01qQXdMREl6TlN3eE1ESXNNVGc1TERJMU1DdzJMREV3TVN3eExESXdMREkwTnl3eE5qVXNORGtzTWpRc01URTVMREV4T0N3eE16Y3NNakE0TERRMExEazNMREl4Tml3eE16WXNNakEyTERFeE5Dd3hOVFVzTWpJeUxESXhPQ3d5TURZc09EWXNNakU1TERFMUxERTJOU3c0TVN3NU1Td3hNemtzTVRrMExESXpNeXd5TVRVc01UWTBMREUzT1N3NE9Td3hPVE1zT1RZc01UUTBMRFl3TERFME1Td3lORFVzTVRneExEZ3hMRE01TERJeExESXpOU3d5TERNc01Td3dMREZkSWl3aVpYaHdJam94TlRJek1EQTFOakkxTENKcGMzTWlPaUpoWTJOdmRXNTBjeTVuYjI5bmJHVXVZMjl0SWl3aWFuUnBJam9pWm1VM1pqTmtNREF5TW1Zek0ySXhPREE1TTJNeU9UTTRaVEZoT1dOaFpEbGtPREF6WTJRME1TSXNJbWxoZENJNk1UVXlNekF3TWpBeU5YMC5pSHI4d1JPa2pscjBMaWVCWU9ET2lkU3JRdnBHYklwZVY4Qk5lV285dEhCOHpiMDBKVWF2ZC1Wam1QNFpfYVllYXBGZDVfZ0p5N1NwaGQ4cnV4dGl1aXgtaHl5NzJZejVwYTNwODRLbWZvZEZ1WHpWWnVaaDRBY1JnZ2djSlFFSHVDS3pjTWNrMmxNSDkwYVhKUmZiUHJUVndSLWVBbkR2V0xUNURlaURkSGNvVi0zNERIVkVKTXJZZDJScEZOeEtEeFQ5OXpqNzJXU2dJcmhuRG1KV3NiWk9CTUpuTExycXU3czBuTEVLRXhNUGpLRmQ4ZGFldWRWMXlSajFmaEplTVRpVmdYbU9jZW5MVE9pVTVHS0x4WWlTRFQ0Z1Y0enNlbkduYVgtazhmOExoUXRwU0tjUWtlbWtSclJhUXRHSkFCakppU2RvaUVpWURUNzUyTm9abFEiLCJ0b2tlbklESlNPTiI6eyJhenAiOiI4MDgzMjk1NjYwMTItdHFyOHFvaDExMTk0MmdkMmtnMDA3dDBzOGYyNzdyb2kuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI4MDgzMjk1NjYwMTItdHFyOHFvaDExMTk0MmdkMmtnMDA3dDBzOGYyNzdyb2kuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDM5ODAyNzA0MzQxOTQ3MzMwNzYiLCJlbWFpbCI6Im9wZW5pZHRlc3QzMEBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6InRydWUiLCJhdF9oYXNoIjoiMXV3bmNFQ1IwRHFEcElXd2xoQzRjUSIsIm5vbmNlIjoiWzQ4LDEzMCwxLDM0LDQ4LDEzLDYsOSw0MiwxMzQsNzIsMTM0LDI0NywxMywxLDEsMSw1LDAsMywxMzAsMSwxNSwwLDQ4LDEzMCwxLDEwLDIsMTMwLDEsMSwwLDIzOSw4MiwwLDc5LDE0NSwyNSw4MiwxMDgsMjI0LDIzNiwyMjMsMjIyLDE1OCwwLDIxNiwxNjMsMjIwLDExMywyNDEsOTIsNTgsOTQsMjM4LDExMywxNzksNzUsODAsMjQzLDE1OSwyMSwyMTAsMzYsMTgzLDYxLDEyNywxNzEsMTE3LDgwLDI4LDE0LDI0NCw2NSwxNywyMzAsODEsMTA1LDE4MiwyMjcsNjYsMjgsMTU4LDIyMywxNDAsMTI1LDIzOCwyMzQsMTcxLDI1MSwxOTQsMjA0LDEyNiw5MywxOTgsMTgsMTg5LDQsODksMjA2LDE0MSwyNDgsMjIxLDIzMCw2MSwxNzMsMTk3LDEwNSw2MCw4NSw1LDEyLDIzNiwyMSw4MiwyMTUsMTc5LDIxMSw5OSwyMjQsNDAsMzgsMjcsMTU4LDIxLDAsMjMwLDEwLDEyNiw0NCw5MSw3NCw0MSw0MiwxNzYsMTc3LDExMSwyMzUsMjM2LDEwOSwxNzksMzMsMjM0LDEzLDU2LDUsMjE1LDE5MiwyNDQsMTIxLDEwNSwyMyw3NCwyMzgsMTU2LDE1MiwzOSw4MiwyNTAsNDgsMTM5LDE3MywxMjYsMTcwLDE3OCw2MCwxMjUsNTcsMjcsMjAzLDIzNywxNzksNDAsMTA2LDU0LDExOSwxNzksNzgsMjE0LDE0OCw0OCw3MSwxODgsMjMwLDI1Miw5OSw5Myw0NCwxNDgsMTgxLDEyMiwxMTksMjIsMTYxLDgzLDQ5LDE2NCw0NywyMjgsNTQsNzgsMTI4LDE4NiwxMTYsMTEyLDc3LDIwNywxNjAsMjksMTYwLDEyMCw5NSwxMjgsNTAsMTczLDIzMSwxNjUsNjQsOTUsMjM3LDg1LDE3OSw1MiwxMzQsMTU2LDIyMSwxMTIsODAsMjAwLDEwNCw5MCw5LDUzLDU0LDI2LDEyOCw2MSwyMDAsMjM1LDEwMiwxODksMjUwLDYsMTAxLDEsMjAsMjQ3LDE2NSw0OSwyNCwxMTksMTE4LDEzNywyMDgsNDQsOTcsMjE2LDEzNiwyMDYsMTE0LDE1NSwyMjIsMjE4LDIwNiw4NiwyMTksMTUsMTY1LDgxLDkxLDEzOSwxOTQsMjMzLDIxNSwxNjQsMTc5LDg5LDE5Myw5NiwxNDQsNjAsMTQxLDI0NSwxODEsODEsMzksMjEsMjM1LDIsMywxLDAsMV0iLCJleHAiOiIxNTIzMDA1NjI1IiwiaXNzIjoiYWNjb3VudHMuZ29vZ2xlLmNvbSIsImp0aSI6ImZlN2YzZDAwMjJmMzNiMTgwOTNjMjkzOGUxYTljYWQ5ZDgwM2NkNDEiLCJpYXQiOiIxNTIzMDAyMDI1IiwiYWxnIjoiUlMyNTYiLCJraWQiOiJlOWI1NmNmYzY0MGQxMmJmZjQ1NDA1NTM0MDJjN2YxNTdkNDgxODA2In19"
		 * ); identityCheckIN.put("expires", "1523005625");
		 * 
		 * 
		 * identityCheckIN.put("userURL", "user://google.com/openidtest30@gmail.com");
		 * identityCheckIN.put("status", "created");
		 */

		JsonObject configCheckIN = new JsonObject();
		configCheckIN.put("url", checkINHypertyURL);
		configCheckIN.put("identity", identity);
		// mongo
		configCheckIN.put("db_name", "test");
		configCheckIN.put("collection", "rates");
		configCheckIN.put("mongoHost", mongoHost);

		configCheckIN.put("tokens_per_checkin", 10);
		configCheckIN.put("checkin_radius", 500);
		configCheckIN.put("min_frequency", 1);
		configCheckIN.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configCheckIN.put("hyperty", "123");
		configCheckIN.put("stream", "vertx://sharing-cities-dsm/token-rating-checkin");

		configCheckIN.put("streams", new JsonObject().put("shops", "data://sharing-cities-dsm/shops").put("bonus",
				"data://sharing-cities-dsm/bonus"));

		DeploymentOptions optionsCheckIN = new DeploymentOptions().setConfig(configCheckIN).setWorker(true);
		vertx.deployVerticle(CheckInRatingHyperty.class.getName(), optionsCheckIN, res -> {
			System.out.println("CheckInRatingHyperty Result->" + res.result());
		});

		// deploy user activity rating hyperty
		JsonObject configUserActivity = new JsonObject();
		configUserActivity.put("url", userActivityHypertyURL);
		configUserActivity.put("identity", identity);
		// mongo
		configUserActivity.put("db_name", "test");
		configUserActivity.put("collection", "rates");
		configUserActivity.put("mongoHost", mongoHost);

		configUserActivity.put("tokens_per_walking_km", 10);
		configUserActivity.put("tokens_per_biking_km", 10);
		configUserActivity.put("tokens_per_bikesharing_km", 10);
		configUserActivity.put("tokens_per_evehicle_km", 5);
		configUserActivity.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configUserActivity.put("hyperty", "123");
		configUserActivity.put("stream", "vertx://sharing-cities-dsm/user-activity");

		DeploymentOptions optionsUserActivity = new DeploymentOptions().setConfig(configUserActivity).setWorker(true);
		vertx.deployVerticle(UserActivityRatingHyperty.class.getName(), optionsUserActivity, res -> {
			System.out.println("UserActivityRatingHyperty Result->" + res.result());
		});

		String streamAddress = "vertx://sharing-cities-dsm/elearning";
		JsonObject configElearning = new JsonObject();
		configElearning.put("url", elearningHypertyURL);
		configElearning.put("identity", identity);

		// mongo
		configElearning.put("db_name", "test");
		configElearning.put("collection", "rates");
		configElearning.put("mongoHost", mongoHost);

		configElearning.put("tokens_per_completed_quiz", 50);
		configElearning.put("tokens_per_correct_answer", 5);
		configElearning.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		configElearning.put("streams", new JsonObject().put("elearning", "data://sharing-cities-dsm/elearning"));
		configElearning.put("hyperty", "123");
		configElearning.put("stream", streamAddress);
		DeploymentOptions optionsElearning = new DeploymentOptions().setConfig(configElearning).setWorker(true);
		vertx.deployVerticle(ElearningRatingHyperty.class.getName(), optionsElearning, res -> {
			System.out.println("ElearningRatingHyperty Result->" + res.result());
		});

		JsonObject configEnergySaving = new JsonObject();
		configEnergySaving.put("url", energySavingRatingHypertyURL);
		configEnergySaving.put("identity", identity);
		// config
		configEnergySaving.put("hyperty", "123");
		configEnergySaving.put("stream", "token-rating");
		configEnergySaving.put("wallet", "hyperty://sharing-cities-dsm/wallet-manager");
		// mongo
		configEnergySaving.put("collection", "rates");
		configEnergySaving.put("db_name", "test");
		configEnergySaving.put("mongoHost", mongoHost);

		DeploymentOptions optionsEnergy = new DeploymentOptions().setConfig(configEnergySaving).setWorker(true);
		vertx.deployVerticle(EnergySavingRatingHyperty.class.getName(), optionsEnergy, res -> {
			System.out.println("EnergySavingRatingHyperty Result->" + res.result());
		});

		// Rest service

		// wallet manager hyperty deploy

		JsonObject configWalletManager = new JsonObject();
		configWalletManager.put("url", walletManagerHypertyURL);
		configWalletManager.put("identity", identity);
		configWalletManager.put("db_name", "test");
		configWalletManager.put("collection", "wallets");
		configWalletManager.put("mongoHost", mongoHost);

		configWalletManager.put("observers", new JsonArray().add(""));
		configWalletManager.put("siot_stub_url", smartIotProtostubUrl);
		configWalletManager.put("rankingTimer", 2000);

		// public wallets
		String wallet0Address = "school0-wallet";
		String wallet1Address = "school1-wallet";
		String wallet2Address = "school2-wallet";
		String school0ID = "user-guid://school-0";
		String school1ID = "user-guid://school-1";
		String school2ID = "user-guid://school-2";
		JsonObject feed0 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet0userID");
		JsonObject feed1 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet1userID");
		JsonObject feed2 = new JsonObject().put("platformID", "edp").put("platformUID", "wallet2userID");

		// publicWallets
		JsonArray publicWallets = new JsonArray();
		JsonObject walletCause0 = new JsonObject();
		walletCause0.put("address", wallet0Address);
		walletCause0.put("identity", school0ID);
		walletCause0.put("externalFeeds", new JsonArray().add(feed0));
		publicWallets.add(walletCause0);

		JsonObject walletCause1 = new JsonObject();
		walletCause1.put("address", wallet1Address);
		walletCause1.put("identity", school1ID);
		walletCause1.put("externalFeeds", new JsonArray().add(feed1));
		publicWallets.add(walletCause1);
		
		JsonObject walletCause2 = new JsonObject();
		walletCause2.put("address", wallet2Address);
		walletCause2.put("identity", school2ID);
		walletCause2.put("externalFeeds", new JsonArray().add(feed2));
		publicWallets.add(walletCause2);
		
		configWalletManager.put("publicWallets", publicWallets);

		DeploymentOptions optionsconfigWalletManager = new DeploymentOptions().setConfig(configWalletManager)
				.setWorker(true);
		vertx.deployVerticle(WalletManagerHyperty.class.getName(), optionsconfigWalletManager, res -> {
			System.out.println("WalletManagerHyperty Result->" + res.result());
		});

		// deploy smart Iot protostub

		JsonObject configSmartIotStub = new JsonObject();
		configSmartIotStub.put("url", smartIotProtostubUrl);
		configSmartIotStub.put("db_name", "test");
		configSmartIotStub.put("collection", "siotdevices");
		configSmartIotStub.put("mongoHost", mongoHost);
		configSmartIotStub.put("smart_iot_url", SIOTurl);
		configSmartIotStub.put("point_of_contact", pointOfContact);

		DeploymentOptions optionsconfigSmartIotStub = new DeploymentOptions().setConfig(configSmartIotStub)
				.setWorker(true);
		vertx.deployVerticle(SmartIotProtostub.class.getName(), optionsconfigSmartIotStub, res -> {
			System.out.println("SmartIOTProtustub Result->" + res.result());
		});

		// Configure HttpServer and set it UP
		System.out.println("Setting up httpserver");
		int BUFF_SIZE = 32 * 1024;
		final JksOptions jksOptions = new JksOptions().setPath("server-keystore.jks").setPassword("rethink2015");

		HttpServerOptions httpOptions = new HttpServerOptions().setMaxWebsocketFrameSize(6553600).setTcpKeepAlive(true)
				.setSsl(true).setKeyStoreOptions(jksOptions).setReceiveBufferSize(BUFF_SIZE).setAcceptBacklog(10000)
				.setSendBufferSize(BUFF_SIZE);

		final HttpServer server = vertx.createHttpServer(httpOptions).requestHandler(router::accept)
				.websocketHandler(new Handler<ServerWebSocket>() {
					public void handle(final ServerWebSocket ws) {

						final StringBuilder sb = new StringBuilder();
						System.out.println("RESOURCE-OPEN");
						ws.frameHandler(frame -> {
							sb.append(frame.textData());

							if (frame.isFinal()) {
								System.out.println("RESOURCE isFinal -> Data:" + sb.toString());
								ws.writeFinalTextFrame("received");
								sb.delete(0, sb.length());
							}
						});
						ws.closeHandler(handler -> {
							System.out.println("RESOURCE-CLOSE");
						});
					}
				});

		server.listen(9091);

		if (mongoHost != null) {
			System.out.println("Setting up Mongo to:" + mongoHost);
			final String uri = "mongodb://" + mongoHost + ":27017";

			final JsonObject mongoconfig = new JsonObject().put("connection_string", uri).put("db_name", "test");

			mongoClient = MongoClient.createShared(vertx, mongoconfig);
		}

	}

	private void handleRequestPub(RoutingContext routingContext) {

		System.out.println("ENDPOINT POST RECEIVED DATA -> " + routingContext.getBodyAsString().toString());

		JsonObject dataReceived = new JsonObject(routingContext.getBodyAsString().toString());
		JsonArray values = dataReceived.containsKey("values") ? dataReceived.getJsonArray("values") : null;

		if (values != null) {
			int x;
			for (x = 0; x < values.size(); x++) {
				JsonObject currentObj = values.getJsonObject(x);
				String value = currentObj.getString("data");
				
				JsonObject valueData = new JsonObject().put("value", Integer.parseInt(value));
				JsonObject valueObject = new JsonObject().put("type", "POWER")
														.put("value", valueData);
				
				JsonArray valuesArray = new JsonArray().add(valueObject);
				
				JsonObject newObjToSend = new JsonObject().put("unit", "WATT_PERCENTAGE")
															.put("values", valuesArray);
				
				String objURL = findStream(currentObj.getString("streamId"));
				System.out.println("publishin on " + objURL + "/changes");

				if (objURL != null) {
					String changesObj = objURL + "/changes";
					vertx.eventBus().publish(changesObj, new JsonArray().add(newObjToSend));
				}
			}
		}
		HttpServerResponse httpServerResponse = routingContext.response();
		httpServerResponse.setChunked(true);

		httpServerResponse.putHeader("Content-Type", "application/text").end();
	}

	private String findStream(String streamID) {
		System.out.println("find stream:" + streamID);
		final String stream[] = new String[1];
		findDOUrl = new CountDownLatch(1);

		new Thread(() -> {
			mongoClient.find("dataobjects", new JsonObject().put("url", streamID), res -> {
				if (res.result().size() != 0) {
					String objURL = res.result().get(0).getString("objURL");

					stream[0] = objURL;
					System.out.println("3,9" + stream[0]);
				}
				findDOUrl.countDown();
			});

		}).start();

		try {
			findDOUrl.await(5L, TimeUnit.SECONDS);
			return stream[0];
		} catch (InterruptedException e) {
			System.out.println(e);
		}
		return stream[0];
	}

}
