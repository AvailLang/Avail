/*
 * Problems.kt
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

package org.availlang.ide.anvil.models

import java.io.BufferedWriter
import java.io.PrintWriter

/**
 * A distinct identifiable problem with an [AvailProject].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
sealed class ProjectProblem
{
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
	 * Write this problem to the provdied [BufferedWriter].
	 *
	 * @param writer
	 *   The [BufferedWriter] to write to.
	 */
	abstract fun writeTo (writer: BufferedWriter)
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
class UnexplainedProblem constructor (
	exception: Throwable,
	override val description: String = exception.message ?: "No error message."
): ProjectProblem()
{
	init
	{
		this.exception = exception
	}

	override fun writeTo(writer: BufferedWriter)
	{
		val border = "-----------------------------"
		writer.write("$border Unexplained Problem $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n------- Stack Trace -------\n")
		exception!!.printStackTrace(PrintWriter(writer))
		writer.write("\n")
	}
}

/**
 * A [ProjectProblem] with a [ProjectLocation].
 *
 * @author Richard Arriaga
 *
 * @property invalidLocation
 *   The [InvalidLocation] that is the cause of this [ProjectProblem].
 */
class LocationProblem constructor(
	val invalidLocation: InvalidLocation
): ProjectProblem()
{
	override val description: String
		get() = invalidLocation.problem

	override fun writeTo(writer: BufferedWriter)
	{
		val border = "-------------------------------"
		writer.write("$border LocationProblem $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
	}
}

/**
 * A [ProjectProblem] with adding a Module Root.
 *
 * @author Richard Arriaga
 */
class ModuleRootScanProblem constructor(
	override val description: String
): ProjectProblem()
{
	override fun writeTo(writer: BufferedWriter)
	{
		val border = "----------------------------"
		writer.write("$border ModuleRootScanProblem $border\n\n")
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
	override val description: String
): ProjectProblem()
{
	override fun writeTo(writer: BufferedWriter)
	{
		val border = "------------------------------"
		writer.write("$border ConfigFileProblem $border\n\n")
		writer.write("Message: $description\n")
		writer.write("\n")
	}
}

