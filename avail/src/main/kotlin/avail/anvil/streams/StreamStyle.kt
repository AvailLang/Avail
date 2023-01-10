/*
 * StreamStyle.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.anvil.streams

import avail.anvil.StyleRule
import avail.anvil.Stylesheet
import avail.anvil.SystemColors
import avail.anvil.SystemStyleClassifier
import java.awt.Color

/**
 * An abstraction of the styles of streams used by the workbench.
 *
 * @property classifier
 *   The governing style classifier.
 * @property selector
 *   The method selector for the [default&#32;color][defaultColor].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum class StreamStyle constructor(
	val systemClassifier: SystemStyleClassifier,
	private val selector: SystemColors.()->Color
)
{
	/** The stream style used to echo user input. */
	IN_ECHO(SystemStyleClassifier.STREAM_INPUT, SystemColors::streamInput),

	/** The stream style used to display normal output. */
	OUT(SystemStyleClassifier.STREAM_OUTPUT, SystemColors::streamOutput),

	/** The stream style used to display error output. */
	ERR(SystemStyleClassifier.STREAM_ERROR, SystemColors::streamError),

	/** The stream style used to display informational text. */
	INFO(SystemStyleClassifier.STREAM_INFO, SystemColors::streamInfo),

	/** The stream style used to echo commands. */
	COMMAND(SystemStyleClassifier.STREAM_COMMAND, SystemColors::streamCommand),

	/** The stream style used to provide build progress updates. */
	BUILD_PROGRESS(
		SystemStyleClassifier.STREAM_BUILD_PROGRESS,
		SystemColors::streamBuildProgress
	);

	/** The style classifier. */
	val classifier get() = systemClassifier.classifier

	/**
	 * The [default][SystemColors] [color][Color] for this [style][StreamStyle].
	 * Only used when the active [stylesheet][Stylesheet] does not include any
	 * [rules][StyleRule] for the [classifiers][classifier].
	 */
	val defaultColor get() = SystemColors.active.selector()
}
