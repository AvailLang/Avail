/*
 * CreateProjectPanel.kt
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

package avail.anvil.projects.manager

import avail.anvil.components.DirectoryChooser
import avail.anvil.components.TextFieldWithLabel
import avail.anvil.components.TextFieldWithLabelAndButton
import avail.anvil.icons.ProjectManagerIcons
import avail.anvil.projects.GlobalAvailConfiguration
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.AvailProjectV1
import org.availlang.json.JSONWriter
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

/**
 * A [JPanel] used to create a new [AvailProject].
 *
 * @author Richard Arriaga
 *
 * @property config
 *   The [GlobalAvailConfiguration] for this machine.
 * @property onCreate
 *   The function that accepts the newly created [AvailProject].
 * @property onCancel
 *   The function to call if creating a new project is canceled.
 *
 */
class CreateProjectPanel constructor(
	internal val config: GlobalAvailConfiguration,
	private val onCreate: (AvailProject, String) -> Unit,
	private val onCancel: () -> Unit
): JPanel(GridBagLayout())
{
	fun create ()
	{
		val fileName = projectFileName.textField.text
		val projLocation = projectLocation.textField.text
		val projectFilePath = "$projLocation/$fileName.json"
		AvailProjectV1(
			fileName,
			true,
			AvailRepositories(rootNameInJar = null)
		).apply {
			File(projLocation).mkdirs()
			val rootsLocation = "${projLocation}/roots"
			File(rootsLocation).mkdirs()
			val root = AvailProjectRoot(
				projLocation,
				rootNameField.textField.text,
				ProjectHome(
					"roots",
					Scheme.FILE,
					projLocation,
					null))
			roots[root.name] = root

			if (projectFilePath.isNotEmpty())
			{
				// Update the backing project file.
				val writer = JSONWriter.newPrettyPrinterWriter()
				writeTo(writer)
				File(projectFilePath).writeText(writer.contents())
				config.add(this, projectFilePath)
				onCreate(this, projectFilePath)
			}
		}
	}

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	private val projectNameField = TextFieldWithLabel("Project Name: ")
	private val projectFileName =
		TextFieldWithLabelAndButton("Project Config File Name: ")
			.apply panel@{
				textField.text = "avail-config"
				button.apply {
					isContentAreaFilled = false
					isBorderPainted = false
					text = ""
					icon = ProjectManagerIcons.refresh(19)
					addActionListener {
						this@panel.textField.text = "avail-config"
					}
				}
			}

	private val projectLocation =
		DirectoryChooser("Project Directory: ","Select Project Directory" )

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	private val rootNameField = TextFieldWithLabel("Create Root Name: ")

	/**
	 * The button to use to show the create screen.
	 */
	val createButton = JButton("Create").apply {
		isOpaque = true
		border = BorderFactory.createLineBorder(
			Color(0xBB, 0xBB, 0xBB), 1, true)
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
		preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
		maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
		addActionListener { create() }
	}

	/**
	 * The button to use to show the create screen.
	 */
	val cancel = JButton("Cancel").apply {
		isOpaque = true
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
		preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
		maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
		addActionListener {
			onCancel()
		}
	}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val bottomPanel = JPanel().apply {
		layout = (FlowLayout(FlowLayout.RIGHT))
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(600, 50)
		maximumSize = Dimension(600, 50)
		add(cancel)
		add(createButton)
	}

	init
	{
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(600, 50)
		maximumSize = Dimension(600, 50)
		add(
			projectNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 0
				gridwidth = 1
			})
		add(projectFileName,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 1
				gridwidth = 1
			})
		add(projectLocation,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 2
				gridwidth = 1
			})
		add(rootNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 3
				gridwidth = 1
			})
		add(
			bottomPanel,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 5
				gridwidth = 1
			})
	}



}


