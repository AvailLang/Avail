/*
 * CreateRootView.kt
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

package avail.anvil.views

import avail.anvil.components.TextFieldWithLabel
import org.availlang.artifact.environment.project.AvailProjectRoot
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * The [JFrame] used to create a new root.
 *
 * @author Richard Arriaga
 */
class CreateRootView constructor(
	val projectDirectory: String,
	val onCreate: (AvailProjectRoot) -> Unit,
	val onCancel: () -> Unit
): JFrame("Create New Root")
{
	val panel = JPanel(GridBagLayout()).apply {
		border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
	}

	val nameField = TextFieldWithLabel("Root Name:")
	val projectDirField = TextFieldWithLabel("Project Relative Location:")

	init
	{
		minimumSize = Dimension(500, 500)
		preferredSize = Dimension(500, 500)
		maximumSize = Dimension(500, 500)
		val c = GridBagConstraints().apply {
			weightx = 0.5
		}
		add(nameField, c)
		c.gridwidth = GridBagConstraints.REMAINDER
		add(projectDirField, c)
		c.weightx = 0.0
	}


}
