/*
 * OpenModuleAction.kt
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

import avail.environment.AvailEditor
import avail.environment.AvailWorkbench
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.KeyStroke

/**
 * Open an editor on the selected module.
 *
 * @constructor
 * Construct a new [OpenModuleAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class OpenModuleAction
constructor (
	workbench: AvailWorkbench,
) : AbstractWorkbenchAction(
	workbench,
	"Open selected module",
	KeyStroke.getKeyStroke(KeyEvent.VK_O, AvailWorkbench.menuShortcutMask))
{
	override fun actionPerformed(event: ActionEvent)
	{
		var isNew = false
		val moduleName =
			workbench.selectedModule()!!.resolverReference.moduleName
		val editor = workbench.openEditors.computeIfAbsent(moduleName) {
			isNew = true
			AvailEditor(workbench, moduleName)
		}
		if (!isNew)
		{
			editor.openStructureView(true)
			editor.toFront()
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"View/edit the selected module.")
	}
}
