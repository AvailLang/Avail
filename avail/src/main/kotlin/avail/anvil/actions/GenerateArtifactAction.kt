/*
 * OpenFileAction.kt
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
import avail.anvil.components.ComboWithLabel
import avail.anvil.streams.StreamStyle.ERR
import avail.anvil.streams.StreamStyle.INFO
import avail.utility.stackToString
import org.availlang.artifact.AvailArtifactBuildPlan
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Open an editor on the selected module.
 *
 * @constructor
 * Construct a new [GenerateArtifactAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class GenerateArtifactAction
constructor (
	workbench: AvailWorkbench
) : AbstractWorkbenchAction(
	workbench,
	"Create Artifact")
{
	// Do nothing
	override fun updateIsEnabled(busy: Boolean) {}


	override fun isEnabled(): Boolean =
		workbench.availProject.artifactBuildPlans.isNotEmpty()

	override fun actionPerformed(event: ActionEvent)
	{
		val plans = workbench.availProject.artifactBuildPlans

		val dialog =
			JDialog(
				workbench,
				"Create Artifact",
				Dialog.ModalityType.APPLICATION_MODAL
			).apply {
				minimumSize = Dimension(400, 160)
				preferredSize = Dimension(400, 160)
				maximumSize = Dimension(400, 160)
			}
		var selected: AvailArtifactBuildPlan = plans.first()
		val combo = ComboWithLabel(
			"Plan: ",
			plans.toTypedArray(),
		).apply {
			minimumSize = Dimension(380, 45)
			preferredSize = Dimension(380, 45)
			maximumSize = Dimension(380, 45)
			addComboActionListener {
				selectedItem?.let {
					selected = it as AvailArtifactBuildPlan
				}
			}
		}
		val cancel = JButton("Cancel").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
			addActionListener { dialog.dispose() }
		}

		val buildButton = JButton("Build").apply {
			isOpaque = true
			border = BorderFactory.createLineBorder(
				Color(0xBB, 0xBB, 0xBB), 1, true)
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
			addActionListener {
				selected.buildAvailArtifactJar(
					workbench.availProject,
					{ workbench.writeText("Artifact created:\n\t$it", INFO) }
				) { m, e ->
					val message = "failed to create artifact: $m" +
						(e?.let { "\n${it.stackToString}" } ?: "")
					workbench.writeText(message, ERR)
					e?.printStackTrace()
				}
				dialog.dispose()
			}
		}
		JPanel().apply {
			layout = BoxLayout(this, BoxLayout.Y_AXIS)
			add(JPanel().apply {
				layout = (FlowLayout(FlowLayout.LEFT))
				minimumSize = Dimension(390, 60)
				preferredSize = Dimension(390, 60)
				maximumSize = Dimension(390, 60)
				add(combo)
			})
			add(JPanel().apply {
				layout = (FlowLayout(FlowLayout.RIGHT))
				minimumSize = Dimension(390, 60)
				preferredSize = Dimension(390, 60)
				maximumSize = Dimension(390, 60)
				border = BorderFactory.createEmptyBorder(10,10,10,10)
				add(cancel)
				add(buildButton)
			})
			dialog.add(this)
		}
		SwingUtilities.invokeLater {
			dialog.setLocationRelativeTo(workbench)
			dialog.isVisible = true
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Generate an Avail artifact from the active project.")
	}
}
