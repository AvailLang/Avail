/*
 * NewModuleDialog.kt
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

package avail.anvil.dialogs

import avail.anvil.AvailEditor
import avail.anvil.AvailWorkbench
import avail.anvil.actions.RefreshAction
import avail.anvil.components.ComboWithLabel
import avail.anvil.streams.StreamStyle
import avail.builder.ModuleName
import avail.builder.ModuleRoot
import avail.resolver.ModuleRootResolver
import avail.utility.notNullAnd
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.ModuleHeaderFileMetadata
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.io.File
import java.io.FileFilter
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Opens the dialog for creating a new Avail module.
 *
 * @author Richard Arriaga
 *
 * @property targetDir
 *   The absolute path to the directory new module will be created.
 * @property parentQualifiedName
 *   The fully qualified name of the parent location the module will be created
 *   in.
 * @property targetProjectRoot
 *   The [AvailProjectRoot] where the module is being added.
 * @property moduleRoot
 *   The [ModuleRoot] where the module is to be created in.
 * @property workbench
 *   The running [AvailWorkbench].
 */
class NewModuleDialog constructor(
	private val targetDir: String,
	private val parentQualifiedName: String,
	private val targetProjectRoot: AvailProjectRoot,
	private val moduleRoot: ModuleRoot,
	private val workbench: AvailWorkbench
): JFrame("Create New Module")
{
	/**
	 * The possible file extensions for the new Avail module per the project
	 * configuration.
	 */
	private val extensionOptions: Array<String> get() =
		(listOf(targetProjectRoot.resourceTypeManager.moduleFileExtension) +
			targetProjectRoot.resourceTypeManager.headerlessExtensions
				.flatMap { setOf(it.headerExtension, it.headerlessExtension) })
		.toTypedArray()

	/**
	 * The [JTextField] field to use wto set the module name.
	 */
	private val moduleNameTextField: JTextField = JTextField()

	/**
	 * The variable that holds onto the proposed module name.
	 */
	private val baseModuleName get() = moduleNameTextField.text

	/**
	 * Available [ModuleHeaderFileMetadata] that represent templates.
	 */
	private val headers: Array<ModuleHeaderFileMetadata> =
		targetProjectRoot.moduleHeaders.toMutableSet().apply {
			addAll(workbench.availProject.moduleHeaders)
		}.toMutableList().apply {
			sortBy { it.name }
		}.toTypedArray()

	/**
	 * The [JCheckBox] that when check indicates a Module Package is being
	 * created which requires creating both a new package and a package
	 * representative inside of it. If unchecked, a normal package is being
	 * created.
	 */
	private val modulePackageCB =
		JCheckBox("Create as Module Package").apply {
			font = Font(font.name, font.style, 10)
		}

	/**
	 * The [ComboWithLabel] used to pick the appropriate module file header to
	 * prefix the file with.
	 */
	private val modulePicker = ComboWithLabel(
		"Module Header: ", headers
	).apply {
		toolTipText = "Adds project module header template to created file"
	}

	/**
	 * The selected module file extension.
	 */
	private val selectedHeader: ModuleHeaderFileMetadata? get() =
	modulePicker.combo.selectedItem as? ModuleHeaderFileMetadata?

	/**
	 * `true` indicates a Module Package is being created which requires
	 * creating both a new package and a package representative inside of it;
	 * `false` indicates a normal package is being created.
	 */
	private val isModulePackage get() = modulePackageCB.isSelected

	/**
	 * A validation error message or an empty string if none.
	 */
	private var errorMessage = ""

	/**
	 * The [ComboWithLabel] used to pick the appropriate module file extension
	 * if the selected [AvailProjectRoot.availModuleExtensions] has more than
	 * one option.
	 */
	private val fileExtensionPicker = ComboWithLabel(
		"Extension: ",
		extensionOptions
	).apply {
			toolTipText = "Pick file extension for new module"
	}

	/**
	 * The selected module file extension.
	 */
	private val selectedFileExtension get() =
		fileExtensionPicker.combo.selectedItem as? String

	/**
	 * The variable that holds onto the proposed module name.
	 */
	private val moduleName get() = "$baseModuleName.$selectedFileExtension"

	/**
	 * The absolute path to the location where the module will be created.
	 */
	private val targetLocationAbsolutePath get() = "$targetDir$moduleName"

	/**
	 * The module name to create.
	 */
	private val createdModuleName get() =
		ModuleName("$parentQualifiedName${File.separator}$baseModuleName")

	/**
	 * The error label to be displayed if there is something wrong with the
	 * validation of the chosen Module name.
	 */
	private val errorMessageLabel = JLabel().apply {
		font = Font(font.name, Font.ITALIC, 10)
		foreground = Color.RED
		text = errorMessage
		isVisible = false
	}

	/**
	 * The center panel for where both the
	 */
	private val centerPanel = JPanel(BorderLayout())

	val optionsPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		add(modulePicker)
		add(modulePackageCB)
	}

	/**
	 * Conditionally populate the center panel
	 */
	private fun populateCenterPanel ()
	{
		centerPanel.removeAll()
		errorMessageLabel.apply {
			errorMessageLabel.text = errorMessage
			isVisible = errorMessage.isNotEmpty()
		}
		fileExtensionPicker.isVisible =
			extensionOptions.size > 1
		centerPanel.apply {
			add(errorMessageLabel, BorderLayout.PAGE_START)
			add(fileExtensionPicker, BorderLayout.CENTER)
			add(optionsPanel, BorderLayout.PAGE_END)
		}
	}

	/**
	 * Attempt to create the new module.
	 */
	private fun attemptCreate ()
	{
		if ('/' in moduleName || '\\' in moduleName)
		{
			errorMessage = "$moduleName must not contain '/' or '\\'!"
			SwingUtilities.invokeLater {
				populateCenterPanel()
				displayWindow()
				centerPanel.validate()
				repaint()
			}
			return
		}
		val dir = File(targetDir)
		val duplicateFiles = dir.listFiles(FileFilter { it.name == moduleName })
		if (duplicateFiles.notNullAnd { isNotEmpty() })
		{
			errorMessage = "$moduleName already exists!"
			SwingUtilities.invokeLater {
				populateCenterPanel()
				displayWindow()
				centerPanel.validate()
				repaint()
			}
			return
		}
		workbench.writeText(
			"Created module: $targetLocationAbsolutePath\n",
			StreamStyle.INFO)
		val toOpen = when (isModulePackage)
		{
			true -> ModuleName("$createdModuleName/$baseModuleName")
			else -> createdModuleName
		}
		lateinit var id: UUID
		id = moduleRoot.resolver.subscribeRootWatcher { type, ref ->
			if (type == ModuleRootResolver.WatchEventType.CREATE &&
				ref.qualifiedName == toOpen.qualifiedName)
			{
				SwingUtilities.invokeLater {
					val editor =
						workbench.openEditors.computeIfAbsent(toOpen) {
							AvailEditor(workbench, toOpen)
						}
					RefreshAction(workbench).runAction()
					editor.toFront()
					moduleRoot.resolver.unsubscribeRootWatcher(id)
				}
			}
		}
		val file = when
		{
			isModulePackage ->
			{
				File(targetLocationAbsolutePath).mkdirs()
				File("$targetLocationAbsolutePath${File.separator}$moduleName")
			}
			else -> File(targetLocationAbsolutePath)
		}
		file.createNewFile()
		val sh = selectedHeader
		if (sh != null)
		{
			var t = sh.fileContents
			t = t.replace("{{MOD}}", baseModuleName)
			val date = Date.from(Instant.now())
			val formatter = SimpleDateFormat("yyyy", Locale.getDefault())
			val year = formatter.format(date)
			t = t.replace("{{YEAR}}", year)
			t = "$t\n"
			file.writeText(t)
		}
		dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
	}

	init
	{
		val panel = JPanel(BorderLayout()).apply {
			border = BorderFactory.createEmptyBorder(15, 10, 15, 10)
		}
		val moduleNamePanel = JPanel(GridBagLayout()).apply {
			panel.add(this, BorderLayout.PAGE_START)
		}
		// The label for the text field to enter the module name
		JLabel("Module Name: ").apply {
			moduleNamePanel.add(
				this,
				GridBagConstraints().apply {
					gridx = 0
					gridy = 0
					gridwidth = 1
				})
		}

		moduleNameTextField.apply {
			moduleNamePanel.add(
				this,
				GridBagConstraints().apply {
					weightx = 1.0
					weighty = 1.0
					fill = GridBagConstraints.HORIZONTAL
					gridx = 1
					gridy = 0
					gridwidth = 1
				})
		}
		populateCenterPanel()
		panel.add(centerPanel, BorderLayout.CENTER)

		// The create/cancel button panel
		val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
			panel.add(this, BorderLayout.PAGE_END)
		}

		// The button to cancel the action and close the screen.
		JButton("Cancel").apply {
			buttonPanel.add(this, BorderLayout.PAGE_END)
			addActionListener {
				this@NewModuleDialog.dispatchEvent(
					WindowEvent(this@NewModuleDialog, WindowEvent.WINDOW_CLOSING))
			}
		}
		val createButton: JButton = JButton("Create").apply {
			buttonPanel.add(this, BorderLayout.PAGE_END)
			isEnabled = false

			addActionListener {
				attemptCreate()
			}
		}
		createButton.addKeyListener(object: KeyAdapter() {
			override fun keyPressed(e: KeyEvent)
			{
				when
				{
					e.keyCode == KeyEvent.VK_ENTER && createButton.isEnabled ->
						attemptCreate()
				}
			}
		})
		moduleNameTextField.addKeyListener(object: KeyAdapter() {
			override fun keyReleased(e: KeyEvent)
			{
				super.keyReleased(e)
				createButton.isEnabled =
					moduleNameTextField.text.isNotEmpty()
			}

			override fun keyPressed(e: KeyEvent)
			{
				when
				{
					e.keyCode == KeyEvent.VK_ENTER && createButton.isEnabled ->
						attemptCreate()
				}
			}
		})
		modulePackageCB.addKeyListener(object: KeyAdapter() {
			override fun keyPressed(e: KeyEvent)
			{
				when (e.keyCode)
				{
					KeyEvent.VK_ENTER ->
					{
						modulePackageCB.isSelected = !modulePackageCB.isSelected
					}
				}
			}
		})
		rootPane.registerKeyboardAction(
			{
				this@NewModuleDialog.dispose()
			},
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW)

		add(panel)
		displayWindow()
	}

	override fun getPreferredSize(): Dimension
	{
		val currentWidth = size.width
		return super.getPreferredSize().apply {
			width = currentWidth
		}
	}

	/**
	 * Perform the necessary final operations to display this view.
	 */
	private fun displayWindow ()
	{
		setLocationRelativeTo(workbench)
		pack()
		maximumSize = Dimension(700, size.height)
		minimumSize = Dimension(300, size.height)
		isVisible = true
		toFront()
	}
}
