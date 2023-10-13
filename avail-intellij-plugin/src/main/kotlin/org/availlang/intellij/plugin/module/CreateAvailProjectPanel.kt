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

import avail.anvil.components.TextFieldWithLabel
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.availStandardLibraries
import org.availlang.artifact.environment.project.*
import java.awt.Dimension
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
 * @author Raul Raja
 *
 * @property config
 *   The [GlobalEnvironmentSettings] for this machine.
 * @property onCreate
 *   The function that accepts the newly created [AvailProject].
 * @property onCancel
 *   The function to call if creating a new project is canceled.
 */
class CreateAvailProjectPanel: JPanel(GridBagLayout())
{
	/**
	 * The Avail standard libraries available on this machine.
	 */
	private val standardLibraries =
		availStandardLibraries.associateBy { it.name }

	/**
	 * The Array of standard library names.
	 */
	private val standardLibraryNames: Array<String> get() =
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
	internal val importStyles = JCheckBox("Import Styles", false).apply {
		isEnabled = false
		toolTipText = "Imports styles packaged with standard library"
	}

	/**
	 * A checkbox to whether or not [TemplateGroup] should be imported from the
	 * Avail Standard library.
	 */
	internal val importTemplates = JCheckBox("Import Templates", false).apply {
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
	private val libraryPicker = JComboBox(standardLibraryNames).apply {
		addActionListener {
			selectedItem?.toString()?.let { key ->
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
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(600, 50)
		maximumSize = Dimension(600, 50)
		add(rootNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 3
				gridwidth = 2
			})
		add(rootsDirField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 4
				gridwidth = 2
			})
		add(libraryNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 5
				gridwidth = 1
			})
		add(libraryPicker,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 5
				gridwidth = 1
			})
		add(importStyles,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 6
				gridwidth = 1
			})
		add(importTemplates,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 7
				gridwidth = 1
			})
	}
}
