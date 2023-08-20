/*
 * ProjectProblems.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.availlang.artifact.environment.project

import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.location.InvalidLocation
import java.io.CharArrayWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * A distinct identifiable problem with an [AvailProject].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
sealed class ProjectProblem
{
	/**
	 * The name of this type of [ProjectProblem].
	 */
	abstract val type: String

	/**
	 * A description of the problem.
	 */
	abstract val description: String

	/**
	 * An optional [Throwable] that describes the problem. If one does not exist
	 * then `null`.
	 */
	open var exception: Throwable? = null
		protected set

	/**
	 * The time in milliseconds since the unix epoch when this [ProjectProblem]
	 * was created. This approximates when the problem was recognized.
	 */
	val created = System.currentTimeMillis()

	/**
	 * Write this problem to the provided [Writer].
	 *
	 * @param writer
	 *   The [Writer] to write to.
	 */
	open fun writeTo (writer: Writer)
	{
		val border = "-----------------"
		writer.write("$border $type (${localTimestamp(created)}) $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
		exception?.let {
			writer.write("\n------- Stack Trace -------\n")
			writer.write(exceptionStack)
			writer.write("\n")
		}
	}

	override fun toString(): String =
		StringWriter().apply {
			writeTo(this)
		}.toString()

	/**
	 * The String [Throwable.printStackTrace] if [exception] is not `null`.
	 * Empty String otherwise.
	 */
	val exceptionStack get() =
		exception?.let { e ->
			CharArrayWriter().let {
				e.printStackTrace(PrintWriter(it))
				it.toString()
			}
		} ?: ""
}

/**
 * A [ProjectProblem] that is the result of an exception that cannot be
 * explained. This is most likely the result of a problem with the plugin and
 * thus should be reported as a bug.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [UnexplainedProblem].
 *
 * @param exception
 *   The [Throwable] that occurred creating this problem.
 * @param description
 *   The description of the problem. Defaults to the `exception`'s message if
 *   present.
 */
@Suppress("unused")
class UnexplainedProblem constructor (
	exception: Throwable,
	override val description: String = exception.message ?: "No error message."
): ProjectProblem()
{
	override val type: String = "Unexplained Problem"

	init
	{
		this.exception = exception
	}

	override fun writeTo(writer: Writer)
	{
		val border = "----------------"
		writer.write("$border $type (${localTimestamp(created)}) $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n------- Stack Trace -------\n")
		writer.write(exceptionStack)
		writer.write("\n")
	}
}

/**
 * A [ProjectProblem] with a [AvailLocation].
 *
 * @author Richard Arriaga
 *
 * @property invalidLocation
 *   The [InvalidLocation] that is the cause of this [ProjectProblem].
 */
@Suppress("unused")
class LocationProblem constructor(
	val invalidLocation: InvalidLocation
): ProjectProblem()
{
	override val type: String = "Location Problem"

	override val description: String
		get() = invalidLocation.problem

	override fun writeTo(writer: Writer)
	{
		val border = "------------------"
		writer.write("$border $type (${localTimestamp(created)}) $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
	}
}

/**
 * A [ProjectProblem] with adding a Module Root.
 *
 * @author Richard Arriaga
 */
@Suppress("unused")
class ModuleRootScanProblem constructor(
	override val description: String
): ProjectProblem()
{
	override val type: String = "Module Root Scan Problem"
	override fun writeTo(writer: Writer)
	{
		val border = "----------------"
		writer.write("$border $type (${localTimestamp(created)}) $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
	}
}

/**
 * A [ProjectProblem] with the configuration file.
 *
 * @author Richard Arriaga
 */
class ConfigFileProblem constructor(
	override val description: String,
	exception: Throwable? = null
): ProjectProblem()
{
	override val type: String = "Config File Problem"

	init
	{
		this.exception = exception
	}

	override fun writeTo(writer: Writer)
	{
		val border = "-----------------"
		writer.write("$border $type (${localTimestamp(created)}) $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
	}
}

////////////////////////////////////////////////////////////////////////////////
//                             Date-Time Utilities                            //
////////////////////////////////////////////////////////////////////////////////

/**
 * Answer a [ZonedDateTime] from the provided time in milliseconds since the
 * unix epoch UTC.
 *
 * @param millisUnixEpoch
 *   The time in milliseconds since the unix epoch UTC.
 * @return
 *   The created [ZonedDateTime].
 */
fun zonedDateTime (millisUnixEpoch: Long): ZonedDateTime
{
	val zoneId = ZonedDateTime.now().zone
	val instant = Instant.ofEpochMilli(millisUnixEpoch)
	return ZonedDateTime.of(
		instant.atZone(zoneId).toLocalDate(),
		instant.atZone(zoneId).toLocalTime(),
		zoneId)
}

/**
 * Answer a formatted local timestamp for the provided format and the time in
 * milliseconds since the unix epoch UTC.
 *
 * @param millisUnixEpoch
 *   The time in milliseconds since the unix epoch UTC to format.
 * @param formatPattern
 *   The string format patter for the [DateTimeFormatter.ofPattern].
 * @return
 *   The formatted date-time.
 */
fun formattedLocalTimestamp (
	millisUnixEpoch: Long,
	formatPattern: String
): String =
	DateTimeFormatter.ofPattern(formatPattern)
		.format(zonedDateTime(millisUnixEpoch))

/**
 * Answer a formatted local timestamp for the time in milliseconds since the
 * unix epoch UTC using the format, `yyyy/MM/dd HH:mm:ss'.'SSS z`, according to
 * the rules of the [DateTimeFormatter].
 *
 * @param millisUnixEpoch
 *   The time in milliseconds since the unix epoch UTC to format.
 * @return
 *   The formatted date-time.
 */
fun localTimestamp (millisUnixEpoch: Long): String =
	formattedLocalTimestamp(millisUnixEpoch, "yyyy/MM/dd HH:mm:ss'.'SSS z")

/**
 * Answer a formatted local timestamp for the time in milliseconds since the
 * unix epoch UTC using the format, `yyyyMMddHHmmss`, according to the rules of
 * the [DateTimeFormatter].
 *
 * @param millisUnixEpoch
 *   The time in milliseconds since the unix epoch UTC to format.
 * @return
 *   The formatted date-time.
 */
@Suppress("unused")
fun compactLocalTimestamp (millisUnixEpoch: Long): String =
	formattedLocalTimestamp(millisUnixEpoch, "yyyyMMddHHmmss")
