/*
 * StandardDialogs.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.anvil.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.WindowScope
import avail.anvil.Anvil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Window
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileFilter

/**
 * Open a system file dialog to select a file for [saving][FileDialog.SAVE].
 *
 * @param window
 *   The parent [ComposeWindow].
 * @param title
 *   The title for the dialog. Some native OS dialogs may not display this.
 * @param startDirectory
 *   The directory to open the project in. Defaults to the user's home
 *   directory.
 * @param choiceFilter
 *   Accepts a [File] and answers `true` if it is acceptable as a target;
 *   `false` otherwise. Defaults to always answering true.
 * @param onResult
 *   Accepts the chosen [Path] or `null` if canceled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileSaveDialog(
	window: Dialog,
	title: String,
	startDirectory: String = Anvil.userHome,
	choiceFilter: (File) -> Boolean = { true },
	onResult: (result: Path?) -> Unit)
{
	FileDialog(window, title, false, startDirectory, choiceFilter, onResult)
}

/**
 * Open a system file dialog to select a file for [loading][FileDialog.LOAD].
 *
 * @param window
 *   The parent [ComposeWindow].
 * @param title
 *   The title for the dialog. Some native OS dialogs may not display this.
 * @param startDirectory
 *   The directory to open the project in. Defaults to the user's home
 *   directory.
 * @param choiceFilter
 *   Accepts a [File] and answers `true` if it is acceptable as a target;
 *   `false` otherwise. Defaults to always answering true.
 * @param onResult
 *   Accepts the chosen [Path] or `null` if canceled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileOpenDialog(
	window: Dialog,
	title: String,
	startDirectory: String = Anvil.userHome,
	choiceFilter: (File) -> Boolean = { true },
	onResult: (result: Path?) -> Unit)
{
	FileDialog(window, title, true, startDirectory, choiceFilter, onResult)
}

/**
 * Open a system file dialog.
 *
 * @param window
 *   The parent [ComposeWindow].
 * @param title
 *   The title for the dialog. Some native OS dialogs may not display this.
 * @param isLoad
 *   `true` if the target file is to be opened; `false` if it is to be saved.
 * @param startDirectory
 *   The directory to open the project in. Defaults to the user's home
 *   directory.
 * @param choiceFilter
 *   Accepts a [File] and answers `true` if it is acceptable as a target;
 *   `false` otherwise. Defaults to always answering true.
 * @param onResult
 *   Accepts the chosen [Path] or `null` if canceled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileDialog(
	window: Dialog,
	title: String,
	isLoad: Boolean,
	startDirectory: String,
	choiceFilter: (File) -> Boolean,
	onResult: (result: Path?) -> Unit
) =
	AwtWindow(
		create = {
			object : FileDialog(window, title, if (isLoad) LOAD else SAVE)
			{
				init
				{
					directory = startDirectory
					filenameFilter = object : FilenameFilter
					{
						override fun accept(dir: File, name: String): Boolean =
							choiceFilter(dir.toPath().resolve(name).toFile())
					}
				}

				override fun setVisible(value: Boolean)
				{
					super.setVisible(value)
					if (value)
					{
						if (file != null)
						{
							onResult(File(directory).resolve(file).toPath())
						}
						else
						{
							onResult(null)
						}
					}
				}
			}.apply {
				this.title = title
			}
		},
		dispose = FileDialog::dispose)

/**
 * Open a system file dialog to choose a directory.
 *
 * @param window
 *   The parent [ComposeWindow].
 * @param title
 *   The title for the dialog. Some native OS dialogs may not display this.
 * @param startDirectory
 *   The directory to open the project in. Defaults to the user's home
 *   directory.
 * @param onResult
 *   Accepts the chosen [Path] or `null` if canceled.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SelectDirectoryDialog(
	window: Dialog,
	title: String,
	startDirectory: String,
	current: String = "",
	onResult: (result: Path?) -> Unit
)
{
	val chooser = JFileChooser(startDirectory)
	chooser.dialogTitle = title
	chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
	if (current.isNotEmpty())
	{
		chooser.selectedFile = File(current)
	}
	chooser.addChoosableFileFilter(
		object : FileFilter()
		{
			override fun getDescription(): String
			{
				return "Directories"
			}

			override fun accept(f: File?): Boolean
			{
				assert(f !== null)
				return f!!.isDirectory && f.canWrite()
			}
		})

	AwtWindow(
		create = {
			val result = chooser.showDialog(
				window, "Select")
			if (result == JFileChooser.APPROVE_OPTION)
			{
				onResult(chooser.selectedFile.toPath())
			}
			window
		},
		dispose = Window::dispose)
}

/**
 * Draw a simple Yes - No confirmation dialogue.
 *
 * @param title
 *   The title message of the dialog.
 * @param message
 *   The message to present.
 * @param onSelection
 *   Accepts `true` if user affirms selection, `false` otherwise.
 */
@Composable
fun WindowScope.ConfirmDialog(
	title: String,
	message: String,
	onSelection: (Boolean) -> Unit
) {
	DisposableEffect(Unit)
	{
		val job = Anvil.applicationIoScope.launch(Dispatchers.Swing)
		{
			val selection = JOptionPane.showConfirmDialog(
				window, message, title, JOptionPane.YES_NO_OPTION)
			onSelection(selection == 0)
		}
		onDispose { job.cancel() }
	}
}
