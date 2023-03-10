/*
 * ToggleDebugInterpreterL1.kt
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

package avail.anvil.actions

import avail.anvil.AvailWorkbench
import avail.interpreter.execution.Interpreter
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * A `ToggleDebugInterpreterL1` toggles the flag that indicates whether to write
 * debug information about Level One nybblecode execution to the transcript.
 *
 * @constructor
 * Construct a new `ToggleDebugInterpreterL1`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class ToggleDebugInterpreterL1 constructor(
	workbench: AvailWorkbench
) : AbstractWorkbenchAction(workbench, "Debug interpreter L1")
{
	// Do nothing
	override fun updateIsEnabled(busy: Boolean) {}

	override fun actionPerformed(event: ActionEvent)
	{
		Interpreter.debugL1 = Interpreter.debugL1 xor true
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Toggle debug output of Level One nybblecode execution.")
		putValue(
			Action.SELECTED_KEY, Interpreter.debugL1)
	}
}
