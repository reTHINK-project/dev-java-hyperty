package tokenRating;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import util.InitialData;

/**
 * The ElearningRatingHyperty observes user's activities and rewards with tokens
 * the individual wallet.
 */
public class ElearningRatingHyperty extends AbstractTokenRatingHyperty {

	/**
	 * Number of tokens awarded after completing a quiz.
	 */
	private int tokensPerCompletedQuiz;
	/**
	 * Number of tokens awarded after answer a question correctly.
	 */
	private int tokensPerCorrectAnswer;

	private CountDownLatch getQuizLatch;
	private CountDownLatch findUserID;
	private String userIDToReturn = null;

	private String dataSource = "elearning";

	@Override
	public void start() {
		super.start();
		// read config
		tokensPerCompletedQuiz = config().getInteger("tokens_per_completed_quiz");
		tokensPerCorrectAnswer = config().getInteger("tokens_per_correct_answer");

		createStreams();
	}

	private void createStreams() {
		JsonObject streams = config().getJsonObject("streams");

		// elearning stream
		String elearningStreamAddress = streams.getString("elearning");
		create(elearningStreamAddress, new JsonObject(), false, subscriptionHandler(), readHandler());
	}

	/**
	 * Handler for read requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> readHandler() {
		return msg -> {
			JsonObject response = new JsonObject();
			if (msg.body().getJsonObject("resource") != null) {

			} else {
				mongoClient.find(elearningsCollection, new JsonObject(), res -> {
					response.put("data", new InitialData(new JsonArray(res.result().toString())).getJsonObject())
							.put("identity", this.identity);
					msg.reply(response);
				});
			}
		};
	}

	/**
	 * Handler for subscription requests.
	 * 
	 * @return
	 */
	private Handler<Message<JsonObject>> subscriptionHandler() {
		return msg -> {
			mongoClient.find(elearningsCollection, new JsonObject(), res -> {
				JsonArray quizzes = new JsonArray(res.result());
				// reply with elearning info
				msg.reply(quizzes);
			});
		};

	}

	int tokenAmount;

	int sumSessionsDistance(int start, JsonArray sessions) {
		int count = start;
		for (int i = 0; i < sessions.size(); i++) {
			count += sessions.getJsonObject(i).getDouble("distance");
		}
		return count;
	}

	@Override
	int rate(Object data) {

		// reset latch
		tokenAmount = -3;
		// TODO - timestamp from message?
		Long currentTimestamp = new Date().getTime();

		// check unprocessed sessions

		JsonObject message = (JsonObject) data;
		System.out.println("ELEARNING MESSAGE " + message.toString());

		String user = message.getString("guid");

		// persist in MongoDB
		message.remove("identity");
		persistData(dataSource, user, currentTimestamp, message.getString("id"), null, message);

		tokenAmount = getTokensForAnswer(message);
		return tokenAmount;
	}

	private String elearningsCollection = "elearnings";

	/**
	 * Get amount of tokens for distance/activity.
	 * 
	 * @param activity
	 *            walking/biking
	 * @param distance
	 *            in meters
	 * @return
	 */
	private int getTokensForAnswer(JsonObject answer) {
		System.out.println("getTokensForAnswer: " + answer);
		JsonArray userAnswers = answer.getJsonArray("answers");
		int tokens = 0;

		String quizID = answer.getString("id");
		System.out.println("Quiz id: " + quizID);

		// query mongo for quiz info
		getQuizLatch = new CountDownLatch(1);
		new Thread(() -> {
			// get shop with that ID
			mongoClient.find(elearningsCollection, new JsonObject().put("name", quizID), elearningForIdResult -> {
				JsonObject quizInfo = elearningForIdResult.result().get(0);
				System.out.println("1 - Received elearning info: " + quizInfo.toString());
				// quiz questions
				JsonArray questions = quizInfo.getJsonArray("questions");
				tokenAmount = validateUserAnswers(userAnswers, questions);
				getQuizLatch.countDown();
				return;
			});
		}).start();

		try {
			getQuizLatch.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch quiz info");
			return tokenAmount;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");

		return tokens;
	}

	private int validateUserAnswers(JsonArray userAnswers, JsonArray questions) {
		// check same size
		if (questions.size() != userAnswers.size())
			return 0;
		int tokens = tokensPerCompletedQuiz;
		for (int i = 0; i < questions.size(); i++) {
			JsonObject currentQuestion = questions.getJsonObject(i);
			int correctAnswer = currentQuestion.getInteger("correctAnswer");
			int userAnswer = userAnswers.getInteger(i);
			System.out.println("correctAnswer:" + correctAnswer + "/" + userAnswer);
			if (userAnswer == correctAnswer)
				tokens += tokensPerCorrectAnswer;
		}

		return tokens;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		System.out.println("waiting for changes to user activity on ->" + address_changes);
		eb.consumer(address_changes, message -> {
			System.out.println("User activity on changes msg: " + message.body().toString());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					System.out.println("CHANGES" + data.toString());
					JsonObject messageToRate = data.getJsonObject(0);
					messageToRate.put("guid", getUserURL(address));

					int numTokens = rate(messageToRate);
					System.out.println("rate tokens: " + numTokens);
					mine(numTokens, messageToRate, "elearning");

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

	public String getUserURL(String address) {

		userIDToReturn = null;
		findUserID = new CountDownLatch(1);
		new Thread(() -> {
			mongoClient.find(dataObjectsCollection, new JsonObject().put("url", address), userURLforAddress -> {
				System.out.println("2 - find Dataobjects size->" + userURLforAddress.result().size());
				System.out.println("2 - find Dataobjects size->" + userURLforAddress.result().get(0));
				JsonObject dataObjectInfo = userURLforAddress.result().get(0).getJsonObject("metadata");
				userIDToReturn = dataObjectInfo.getString("guid");
				findUserID.countDown();
			});
		}).start();

		try {
			findUserID.await(5L, TimeUnit.SECONDS);
			System.out.println("3 - return from latch user url");
			return userIDToReturn;
		} catch (InterruptedException e) {
			System.out.println("3 - interrupted exception");
		}
		System.out.println("3 - return other");
		return userIDToReturn;
	}

}
