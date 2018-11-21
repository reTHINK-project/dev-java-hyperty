package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateUtilsHelper {

	static String isoFormat = "yyyy-MM-dd'T'HH:mm'Z'";

	public static boolean isToday(Date date) {
		return isSameDay(date, Calendar.getInstance().getTime());
	}

	public static boolean isSameDay(Date date1, Date date2) {
		if (date1 == null || date2 == null) {
			throw new IllegalArgumentException("The dates must not be null");
		}
		Calendar cal1 = Calendar.getInstance();
		cal1.setTime(date1);
		Calendar cal2 = Calendar.getInstance();
		cal2.setTime(date2);
		return isSameDay(cal1, cal2);
	}

	public static boolean isSameDay(Calendar cal1, Calendar cal2) {
		if (cal1 == null || cal2 == null) {
			throw new IllegalArgumentException("The dates must not be null");
		}
		return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
				&& cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
	}

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

	public static Date stringToDate(String dateString) {
		DateFormat df1 = new SimpleDateFormat(isoFormat);
		Date res = null;
		try {
			res = df1.parse(dateString);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	public static boolean isDateInCurrentWeek(Date date) {
		Calendar currentCalendar = Calendar.getInstance();
		int week = currentCalendar.get(Calendar.WEEK_OF_YEAR);
		int year = currentCalendar.get(Calendar.YEAR);
		Calendar targetCalendar = Calendar.getInstance();
		targetCalendar.setTime(date);
		int targetWeek = targetCalendar.get(Calendar.WEEK_OF_YEAR);
		int targetYear = targetCalendar.get(Calendar.YEAR);
		return week == targetWeek && year == targetYear;
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
