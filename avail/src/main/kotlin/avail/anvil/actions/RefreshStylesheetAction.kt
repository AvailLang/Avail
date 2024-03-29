/*
 * RefreshStylesheetAction.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.actions

import avail.anvil.AvailWorkbench
import avail.anvil.Stylesheet
import avail.anvil.shortcuts.WorkbenchRefreshStylesheetShortcut
import org.availlang.artifact.environment.project.AvailProject
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * A [RefreshStylesheetAction] reloads the [stylesheet][Stylesheet] from the
 * configuration file underlying the [workbench]'s enclosing
 * [project][AvailProject].
 *
 * @param workbench
 *   The owning [workbench][AvailWorkbench].
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
class RefreshStylesheetAction constructor(
	workbench: AvailWorkbench
): AbstractWorkbenchAction(
	workbench,
	"Refresh Stylesheet",
	WorkbenchRefreshStylesheetShortcut)
{
	override fun updateIsEnabled(busy: Boolean)
	{
		isEnabled = !busy
	}

	override fun actionPerformed(e: ActionEvent) = runAction()

	/**
	 * Reloads the [stylesheet][Stylesheet] from the configuration file
	 * underlying the [workbench]'s enclosing [project][AvailProject].
	 */
	fun runAction()
	{
		try
		{
			// TODO RAA revisit
			workbench.stylesheet =
				workbench.buildStylesheet(workbench.projectFileFromDisk)
		}
		catch (e: Exception)
		{
			e.printStackTrace()
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Refresh the stylesheet from the configuration file for "
				+ "this project."
		)
	}
}
