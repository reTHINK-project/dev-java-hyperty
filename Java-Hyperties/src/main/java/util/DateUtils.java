package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class DateUtils {

	static String isoFormat = "yyyy-MM-dd'T'HH:mm'Z'";

	/**
	 * Get the current time, following the ISO 8601 standard.
	 * 
	 * @return
	 */
	public static String getCurrentDateAsISO8601() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat(isoFormat); // Quoted "Z" to indicate UTC, no timezone offset
		df.setTimeZone(tz);
		return df.format(new Date());
	}

	public static boolean validateDate(String value) {
		Date date = null;
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(isoFormat);
			date = sdf.parse(value);
			if (!value.equals(sdf.format(date))) {
				date = null;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (date == null) {
			// Invalid date format
			return false;
		} else {
			// Valid date format
			return true;
		}
	}

}
