/*
 * AbstractWorkbenchAction.kt
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

package avail.environment.actions

import avail.environment.AvailWorkbench
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.KeyStroke

/**
 * An abstraction for all the workbench's actions.
 *
 * @property workbench
 *   The owning [AvailWorkbench].
 *
 * @constructor
 * Construct a new [AbstractWorkbenchAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param name
 *   The action's name.
 * @param keyStroke
 *   The [KeyStroke] under which to install an action, with window-level scope.
 */
abstract class AbstractWorkbenchAction constructor(
	val workbench: AvailWorkbench,
	name: String,
	keyStroke: KeyStroke? = null,
	private val rootPane: JRootPane = workbench.rootPane
) : AbstractAction(name)
{
	fun name(): String = getValue(Action.NAME) as String

	init
	{
		keyStroke?.let {
			putValue(Action.ACCELERATOR_KEY, keyStroke)
			rootPane.actionMap.put(this, this)
			rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
				keyStroke, this)
		}
	}
}
