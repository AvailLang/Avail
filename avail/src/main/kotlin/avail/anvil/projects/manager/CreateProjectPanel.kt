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
import avail.anvil.components.TextFieldTextFieldButton
import avail.anvil.components.TextFieldWithLabel
import avail.anvil.components.TextFieldWithLabelAndButton
import avail.anvil.createScrollPane
import avail.anvil.icons.ProjectManagerIcons
import avail.anvil.projects.GlobalAvailConfiguration
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.project.AvailProject
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

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
	private val onCreate: (AvailProject) -> Unit,
	private val onCancel: () -> Unit
): JPanel(GridBagLayout())
{
	// TODO MUST Build
	//  - capture project name
	//  - capture project location
	//  - capture repo location (populate default)
	//  - add new roots to create
	//  - add existing roots
	//  - dark mode flag
	//  - populate templates
	//  - project copyright
	//  - add clone existing project?

	fun create (): AvailProject
	{
		TODO()
	}
// 6e6f71
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
		DirectoryChooser("Project Directory: ","Select Avail Repository Directory" )

	private val repoLocation =
		DirectoryChooser(
			"Repository: ",
			"Select Avail Repository Directory"
		).apply {
			textField.text = AvailEnvironment.availHomeRepos
		}

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
		addActionListener {
			val project = create()
//			config.add(project, )
			onCreate(project)
			// TODO
			println("Create Button Doesn't have a target screen yet!")
		}
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
	 * The list of added [TextFieldWithLabelAndButton]s that represent the
	 * added roots.
	 */
	private val addedRoots = mutableMapOf<Int, TextFieldTextFieldButton>()

	/**
	 * The [JPanel] used to display all of the project rows.
	 */
	private val rootRowsInnerPanel: JPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS).apply {
			alignmentY = Component.TOP_ALIGNMENT
		}
	}

	/**
	 * The [JScrollPane] that contains the [rootRowsInnerPanel].
	 */
	private val scrollPane: JScrollPane = createScrollPane(rootRowsInnerPanel).apply {
		verticalScrollBarPolicy =
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
		minimumSize = Dimension(width, 300)
		preferredSize = Dimension(width, 300)
		alignmentY = Component.TOP_ALIGNMENT
	}

	private var addedRootsId = 0

	fun newRootRow (id: Int): TextFieldTextFieldButton =
		TextFieldTextFieldButton().apply {
			val remove = JButton(ProjectManagerIcons.cancelFilled(19))
			remove.addActionListener {
				val removed =
					addedRoots.remove(id) ?: return@addActionListener
				SwingUtilities.invokeLater {
					rootRowsInnerPanel.remove(removed)
					rootRowsInnerPanel.revalidate()
					rootRowsInnerPanel.repaint()
				}
			}
			add(remove,
				GridBagConstraints().apply {
					gridx = 3
					gridy = 0
					gridwidth = 1
				})
			addedRoots[id] = this
		}

	fun addRow ()
	{
		rootRowsInnerPanel.add(newRootRow(addedRootsId++))
		rootRowsInnerPanel.revalidate()
		rootRowsInnerPanel.repaint()
	}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val bottomPanel = JPanel().apply {
		layout = (FlowLayout(FlowLayout.RIGHT))
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(750, 50)
		maximumSize = Dimension(750, 50)
		add(cancel)
		add(createButton)
	}

	init
	{
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
		add(repoLocation,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 3
				gridwidth = 1
			})
		add(rootNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 4
				gridwidth = 1
			})
		add(
			JPanel(FlowLayout(FlowLayout.LEFT)).apply {
				add(JLabel("Add Existing Roots "))
				add(JButton("+").apply {
					addActionListener {
						SwingUtilities.invokeLater {
							addRow()
						}
					}
				})
			},
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 5
				gridwidth = 1
			})
		add(scrollPane,
			GridBagConstraints().apply {
				weightx = 1.0
				weighty = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 6
				gridwidth = 1
				gridheight = 5
			})
		add(
			bottomPanel,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 11
				gridwidth = 1
			})
	}



}


