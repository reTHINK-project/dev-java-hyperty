package runHyperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.xml.XmlConfigurationFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class LoggerFactory {

	private static Logger logger;

	private static LoggerFactory single_instance = null;

	private LoggerFactory() throws FileNotFoundException, IOException {

		// Get instance of configuration factory; your options are default
		// ConfigurationFactory, XMLConfigurationFactory,
		// YamlConfigurationFactory & JsonConfigurationFactory
		ConfigurationFactory factory = XmlConfigurationFactory.getInstance();

		// Locate the source of this configuration, this located file is dummy file
		// contains just an empty configuration Tag
		ConfigurationSource configurationSource = new ConfigurationSource(
				new FileInputStream(new File("configuration.xml")));

		// Get context instance
		LoggerContext context = new LoggerContext("JournalDevLoggerContext");
		// Get a reference from configuration
		Configuration configuration = factory.getConfiguration(context, configurationSource);

		// Create default console appender
		ConsoleAppender appender = ConsoleAppender.createDefaultAppenderForLayout(PatternLayout.createDefaultLayout());

		// Add console appender into configuration
		configuration.addAppender(appender);

		Level logLevel;
		String envLogLevel = System.getenv("LOG_LEVEL");
		if (envLogLevel != null) {
			switch (envLogLevel) {
			case "error":
				logLevel = Level.ERROR;
				break;
			default:
				logLevel = Level.ALL;
				break;
			}
		} else {
			logLevel = Level.ALL;
		}

		// Create loggerConfig
		LoggerConfig loggerConfig = new LoggerConfig("com", logLevel, false);

		// Add appender
		loggerConfig.addAppender(appender, null, null);

		// Add logger and associate it with loggerConfig instance
		configuration.addLogger("com", loggerConfig);

		// Start logging system
		context.start(configuration);

		logger = context.getLogger("com");
	}

	public static LoggerFactory getInstance() {
		if (single_instance == null)
			try {
				single_instance = new LoggerFactory();
			} catch (Exception e) {
				e.printStackTrace();
			}

		return single_instance;
	}

	public Logger getLogger() {

		// Get a reference for logger

		return logger;

	}

}
