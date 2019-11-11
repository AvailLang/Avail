/*
 * RefreshAction.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.environment.actions

import com.avail.environment.AvailWorkbench
import javax.swing.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent

import javax.swing.SwingUtilities.invokeLater

/**
 * A `RefreshAction` updates the module tree with new information from the
 * filesystem.
 *
 * @constructor
 * Construct a new `RefreshAction`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class RefreshAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Refresh")
{
	override fun actionPerformed(event: ActionEvent?)
	{
		val modulesAndEntryPoints =
			workbench.calculateRefreshedTrees()
		invokeLater {
			workbench.refreshFor(
				modulesAndEntryPoints.first(),
				modulesAndEntryPoints.second())
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Refresh the availability of top-level modules.")
		putValue(
			Action.ACCELERATOR_KEY,
			KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0))
	}
}
