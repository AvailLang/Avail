/*
 * CreateProjectPanel.kt
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

package org.availlang.intellij.plugin.module

import avail.anvil.components.ComboBoxWithLabel
import avail.anvil.components.TextFieldWithLabel
import avail.anvil.environment.availStandardLibraries
import com.intellij.util.ui.JBUI
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * A [JPanel] used to create a new [AvailProject].
 *
 * Adapted to IDEA from [avail.anvil.manager.CreateProjectPanel]
 *
 * @author Raúl Raja
 */
class CreateAvailProjectPanel : JPanel(GridBagLayout())
{
	/**
	 * The Avail standard libraries available on this machine.
	 */
	private val standardLibraries =
		availStandardLibraries.associateBy { it.name }

	/**
	 * The Array of standard library names.
	 */
	private val standardLibraryNames: Array<String>
		get() =
			arrayOf("None") + standardLibraries.keys.toTypedArray()

	/**
	 * The selected Avail standard library to use or `null` if none chosen.
	 */
	internal var selectedLibrary: File? = null

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	internal val rootNameField = TextFieldWithLabel("Create Root Name: ")

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	internal val rootsDirField =
		TextFieldWithLabel("Roots Directory Name (optional): ").apply {
			toolTipText =
				"Leaving blank will create roots at top level of project"
		}

	/**
	 * A checkbox to whether or not [StylingGroup] should be imported from the
	 * Avail Standard library.
	 */
	internal val importStyles =
		JCheckBox("Import Styles", false).apply {
			isEnabled = false
			toolTipText = "Imports styles packaged with standard library"
		}

	/**
	 * A checkbox to whether or not [TemplateGroup] should be imported from the
	 * Avail Standard library.
	 */
	internal val importTemplates =
		JCheckBox("Import Templates", false).apply {
			isEnabled = false
			toolTipText = "Imports templates packaged with standard library"
		}

	/**
	 * The [TextFieldWithLabel] used to set the standard library root name.
	 */
	internal val libraryNameField =
		TextFieldWithLabel("Standard Library Root Name: ").apply {
			textField.text = "avail"
		}

	/**
	 * Updated the [JComboBox] used to pick a standard library to add as a root.
	 */
	private val libraryPicker = ComboBoxWithLabel(
		label = "Standard Library: ",
		items = standardLibraryNames,
		additionalComponents = arrayOf(importStyles, importTemplates)
	).apply {
		comboBox.addActionListener {
			comboBox.selectedItem?.toString()?.let { key ->
				standardLibraries[key]?.let { lib ->
					selectedLibrary = lib
					importTemplates.isEnabled = true
					importStyles.isEnabled = true
				} ?: run {
					selectedLibrary = null
					importTemplates.isEnabled = false
					importTemplates.isSelected = false
					importStyles.isEnabled = false
					importStyles.isSelected = false
				}
			}
		}
	}

	init
	{
		// Create some insets for padding around components
		val padding = JBUI.insets(10) // 10-pixel margins on all sides

		val c = GridBagConstraints()
		c.insets = padding // apply the insets to the constraints
		c.fill = GridBagConstraints.HORIZONTAL
		c.weightx = 1.0
		c.weighty = 0.0
		c.gridx = 0
		c.gridwidth = 2 // Span across two columns for better spacing
		c.anchor = GridBagConstraints.CENTER // center components

		// The root name field
		c.gridy = 0
		add(rootNameField, c)

		// The roots directory field
		c.gridy = 1
		add(rootsDirField, c)

		// The library name field
		c.gridy = 2
		add(libraryNameField, c)

		// The library picker
		c.gridy = 3
		add(libraryPicker, c)

		// Empty panel to push everything else to the top
		val emptyPanel = JPanel()
		c.gridy = 4
		c.weighty = 1.0
		c.fill = GridBagConstraints.BOTH
		add(emptyPanel, c)
	}

}
