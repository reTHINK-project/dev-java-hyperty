package tokenRating;

import java.util.Date;
import io.vertx.core.Future;
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

	private String dataSource = "elearning";

	@Override
	public void start() {
		super.start();
		
		ratingType = "elearning";
		
		// read config
		tokensPerCompletedQuiz = config().getInteger("tokens_per_completed_quiz");
		tokensPerCorrectAnswer = config().getInteger("tokens_per_correct_answer");

		createStreams();
		
		resumeDataObjects(ratingType);
	}

	private void createStreams() {
		JsonObject streams = config().getJsonObject("streams");

		// elearning stream
		String elearningStreamAddress = streams.getString("elearning");
		create(null, elearningStreamAddress, new JsonObject(), false, subscriptionHandler(), readHandler());
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
	Future<Integer> rate(Object data) {

		// reset latch
		tokenAmount = -5;
		Long currentTimestamp = new Date().getTime();

		// check unprocessed sessions

		JsonObject message = (JsonObject) data;
		logger.debug("ELEARNING MESSAGE " + message.toString());

		String user = message.getString("guid");

		// persist in MongoDB
		message.remove("identity");
		persistData(dataSource, user, currentTimestamp, message.getString("id"), null, message);

		Future<Integer> tokensForAnswer = getTokensForAnswer(message);
		return tokensForAnswer;
	}

	private String elearningsCollection = "elearnings";

	/**
	 * Get amount of tokens for a Quiz.
	 *
	 * @param answer quiz answer object
	 * @return
	 */
	private Future<Integer> getTokensForAnswer(JsonObject answer) {
		logger.debug("getTokensForAnswer: " + answer);
		JsonArray userAnswers = answer.getJsonArray("answers");

		String quizID = answer.getString("id");
		logger.debug("Quiz id: " + quizID);

		// query mongo for quiz info
		Future<Integer> tokens = Future.future();
		// get shop with that ID
		mongoClient.find(elearningsCollection, new JsonObject().put("name", quizID), elearningForIdResult -> {
			JsonObject quizInfo = elearningForIdResult.result().get(0);
			logger.debug("1 - Received elearning info: " + quizInfo.toString());
			// quiz questions
			JsonArray questions = quizInfo.getJsonArray("questions");
			boolean isMiniQuiz = quizInfo.getString("type").equals("mini-quiz");
			tokens.complete(validateUserAnswers(userAnswers, questions, isMiniQuiz));
		});

		return tokens;
	}

	private int validateUserAnswers(JsonArray userAnswers, JsonArray questions, boolean isMiniQuiz) {
		// check same size
		if (questions.size() != userAnswers.size())
			return 0;
		int tokens = 0;
		if (!isMiniQuiz)
			tokens += tokensPerCompletedQuiz;
		for (int i = 0; i < questions.size(); i++) {
			JsonObject currentQuestion = questions.getJsonObject(i);
			int correctAnswer = currentQuestion.getInteger("correctAnswer");
			int userAnswer = userAnswers.getInteger(i);
			logger.debug("correctAnswer:" + correctAnswer + "/" + userAnswer);
			if (userAnswer == correctAnswer)
				tokens += tokensPerCorrectAnswer;
		}

		return tokens;
	}

	@Override
	public void onChanges(String address) {

		final String address_changes = address + "/changes";
		logger.info("[ELEARNING] waiting for changes->" + address_changes);
		eb.consumer(address_changes, message -> {
			logger.info("[Elearning] data");
			logger.debug("elearning data msg-> " + message.body().toString());
			try {
				JsonArray data = new JsonArray(message.body().toString());
				if (data.size() == 1) {
					logger.debug("CHANGES" + data.toString());
					JsonObject messageToRate = data.getJsonObject(0);
					Future<String> userURL = getUserURL(address);
					userURL.setHandler(asyncResult -> {
						logger.debug("URL " + userURL.result());
						messageToRate.put("guid", userURL.result());
						Future<Integer> numTokens = rate(messageToRate);
						numTokens.setHandler(res -> {
							if (res.succeeded()) {
								mine(numTokens.result(), messageToRate, "elearning");
							} else {
								// oh ! we have a problem...
							}
						});
					});

				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

	}

}
