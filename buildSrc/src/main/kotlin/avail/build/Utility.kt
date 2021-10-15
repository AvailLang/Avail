package avail.build

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Contains utility functions and state for use gradle-related building.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Utility
{
	/**
	 * The current time as a String in the format `yyyyMMdd.HHmmss`.
	 */
	val formattedNow: String get()
	{
		val formatter = DateTimeFormatter
			.ofPattern("yyyyMMdd.HHmmss")
			.withLocale(Locale.getDefault())
			.withZone(ZoneId.of("UTC"))
		return formatter.format(Instant.now())
	}
}
