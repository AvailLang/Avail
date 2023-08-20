package org.availlang.artifact

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * The current time as a String in the format `yyyy-MM-ddTHH:mm:ss.SSSZ`.
 */
val formattedNow: String get()
{
	val formatter = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ")
		.withLocale(Locale.getDefault())
		.withZone(ZoneId.of("UTC"))
	return formatter.format(Instant.now())
}
