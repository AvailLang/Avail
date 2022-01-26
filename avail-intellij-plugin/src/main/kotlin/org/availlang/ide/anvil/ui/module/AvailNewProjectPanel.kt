/*
 * AvailNewProjectPanel.kt
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

package org.availlang.ide.anvil.ui.module

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
//import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

/**
 * A `AvailNewProjectPanel` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailNewProjectPanel: Disposable
{
	override fun dispose()
	{
		TODO("Not yet implemented")
	}
}
/**
 * Annotation is used for single UI DSL demo
 *
 * @param title tab name in the demo
 * @param description description that is shown above the demo
 * @param scrollbar true if the demo should be wrapped into scrollbar pane
 */
@ApiStatus.Internal
@Target(AnnotationTarget.FUNCTION)
internal annotation class Demo(val title: String, val description: String, val scrollbar: Boolean = false)

@Demo(title = "Basics",
	description = "UI DSL builder builds content row by row. Every row consist of cells, last cell in row occupies all remaining width. " +
		"Rows have layout property (see RowLayout) which specify policy of cells layout in row.<br>" +
		"Result of builder is a grid like structure (see GridLayout), where near cells can be merged into one cell. " +
		"Every cell can contain some component or a sub-grid.")
fun demoBasics(): DialogPanel
{
	return panel {
		row("Row1 label:") {
			textField()
			label("Some text")
		}

		row("Row2:") {
			label("This text is aligned with previous row")
		}

		row("Row3:") {
			label("Rows 3 and 4 are in common parent grid")
			textField()
		}.layout(RowLayout.PARENT_GRID)

		row("Row4:") {
			textField()
			label("Rows 3 and 4 are in common parent grid")
		}.layout(RowLayout.PARENT_GRID)
	}
}

@Suppress("DialogTitleCapitalization")
@Demo(title = "Components",
	description = "There are many different components supported by UI DSL. Here are some of them.",
	scrollbar = true)
fun demoComponents(parentDisposable: Disposable? = null): DialogPanel {
	val panel = panel {
		row {
			checkBox("checkBox")
		}

		var radioButtonValue = 2
		this.buttonGroup({ radioButtonValue }, { radioButtonValue = it })
		{
			row("radioButton") {
				radioButton("Value 1", 1)
				radioButton("Value 2", 2)
			}
		}

		row {
			button("button") {}
		}

		row("actionButton:") {
			val action = object : DumbAwareAction("Action text", "Action description", AllIcons.Actions.QuickfixOffBulb) {
				override fun actionPerformed(e: AnActionEvent) {
				}
			}
			actionButton(action)
		}

		row("actionsButton:") {
			actionsButton(object : DumbAwareAction("Action one") {
				override fun actionPerformed(e: AnActionEvent) {
				}
			},
				object : DumbAwareAction("Action two") {
					override fun actionPerformed(e: AnActionEvent) {
					}
				})
		}

		row("segmentedButton:") {
			val property = PropertyGraph().graphProperty { "" }
			segmentedButton(listOf("Button 1", "Button 2", "Button Last"), property) { it }
		}

		row("label:") {
			label("Some label")
		}

//		row("text:") {
//			val c: Row = this
//			this.text("text supports max line width and can contain links, try <a href='https://www.jetbrains.com'>jetbrains.com</a>")
//		}

		row("link:") {
			link("Focusable link") {}
		}

		row("browserLink:") {
			browserLink("jetbrains.com", "https://www.jetbrains.com")
		}

		row("dropDownLink:") {
			dropDownLink("Item 1", listOf("Item 1", "Item 2", "Item 3"))
		}

		row("icon:") {
			icon(AllIcons.Actions.QuickfixOffBulb)
		}

		row("contextHelp:") {
			contextHelp("contextHelp description", "contextHelp title")
		}

		row("textField:") {
			textField()
		}

		row("textFieldWithBrowseButton:") {
			textFieldWithBrowseButton()
		}

//		row("expandableTextField:") {
//			expandableTextField()
//		}

		row("intTextField(0..100):") {
			intTextField(0..100)
		}

		row("spinner(0..100):") {
			spinner(0..100)
		}

		row("spinner(0.0..100.0, 0.01):") {
			spinner(0.0..100.0, 0.01)
		}

		row {
			label("textArea:")
				.verticalAlign(VerticalAlign.TOP)
				.gap(RightGap.SMALL)
			textArea()
				.rows(5)
				.horizontalAlign(HorizontalAlign.FILL)
		}.layout(RowLayout.PARENT_GRID)

		row("comboBox:") {
			comboBox(arrayOf("Item 1", "Item 2"))
		}

		row("PARENT_GRID is set, cell[0,0]:") {
			label("Label 1 in parent grid, cell[1,0]")
			label("Label 2 in parent grid, cell[2,0]")
		}.layout(RowLayout.PARENT_GRID)

//		row("PARENT_GRID is set:") {
//			textField("textField1")
//			textField("textField2")
//		}.layout(RowLayout.PARENT_GRID)

//		row("Row label provided, LABEL_ALIGNED is used:") {
//			textField("textField1")
//			textField("textField2")
//		}
//
//		row {
//			label("Row label is not provided, INDEPENDENT is used:")
//			textField("textField1")
//			textField("textField2")
//		}
	}

//	val disposable = Disposer.newDisposable()
//	panel.registerValidators(disposable)
//	Disposer.register(parentDisposable, disposable)

	return panel
}
