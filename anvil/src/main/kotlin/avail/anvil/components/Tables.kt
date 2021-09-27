/*
 * Tables.kt
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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusOrder
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

////////////////////////////////////////////////////////////////////////////////
//                                Cell views.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * Simple cell view, with support for both immutable and mutable content.
 *
 * @param modifier
 *   The modifier for the entire outer cell.
 * @param data
 *   The source of data that is used to populate the cell.
 * @param weight
 *   The [RowScope] [Modifier] weight that determines the size of this cell
 *   relative to all other cells in the [Row].
 * @param isEditable
 *   Whether the cell permits editing.
 * @param content
 *   [Composable] that draws the `data` content in the cell. Accepts both the
 *   `data`, row number, and a boolean, `true` if the cell is being edited;
 *   `false` otherwise.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun <T> RowScope.CellView (
	modifier: Modifier = Modifier,
	data: T,
	rowNumber: Int,
	weight: Float = 0.5f,
	isEditable: Boolean = false,
	content: @Composable BoxScope.(T, Int, MutableState<Boolean>) -> Unit)
{
	var pointerInside by remember { mutableStateOf(false) }
	val isEditing = remember { mutableStateOf(false) }
	Box(
		modifier = modifier
			.padding(3.dp)
			.fillMaxSize()
			.height(IntrinsicSize.Min)
			.align(Alignment.CenterVertically)
			.weight(weight)
			.clickable(
				enabled = isEditable && !isEditing.value,
				onClickLabel = "Edit cell")
			{
				isEditing.value = true
			}
			.onFocusChanged {
				isEditing.value = it.hasFocus
			}
			.pointerMoveFilter(
				onEnter = {
					pointerInside = true
					false
				},
				onExit = {
					pointerInside = false
					false
				}))
	{
		content(data, rowNumber, isEditing)
		if (isEditable)
		{
			val tint =
				if (pointerInside)
				{
					LocalContentColor.current.copy(
						alpha = LocalContentAlpha.current)
				}
				else
				{
					Color.Transparent
				}
			Icon(
				Icons.Outlined.Edit,
				contentDescription = "Edit cell",
				modifier = Modifier
					.padding(5.dp)
					.align(Alignment.CenterEnd),
				tint = tint)
		}
	}
}

/**
 * Simple cell view, with support for both immutable and mutable content.
 *
 * @param modifier
 *   The modifier for the entire outer cell.
 * @param header
 *   The text used to populate the column header cell.
 * @param weight
 *   The [RowScope] [Modifier] weight that determines the size of this cell
 *   relative to all other cells in the [Row].
 * @param columnAction
 *   The optional [ColumnAction] to be performed on clicking this cell; `null`
 *   indicates no action is to be taken.
 * @param content
 *   [Composable] that draws the `data` content in the cell. Accepts both the
 *   `data`, row number, and a boolean, `true` if the cell is being edited;
 *   `false` otherwise.
 */
@Composable
fun RowScope.HeaderCellView (
	modifier: Modifier = Modifier,
	header: String,
	weight: Float = 0.5f,
	columnAction: ColumnAction? = null,
	content: @Composable BoxScope.(String) -> Unit)
{
	columnAction?.let { modifier.columnAction(it) }
	Box(modifier = modifier
		.fillMaxSize()
		.weight(weight)
		.align(Alignment.CenterVertically))
	{
		content(header)
	}
}

////////////////////////////////////////////////////////////////////////////////
//                               Rows & Columns.                              //
////////////////////////////////////////////////////////////////////////////////

/**
 * Provide a clickable action when the [DataColumn.headerCellView] is clicked.
 *
 * @author Richard Arriaga
 *
 * @property onClickLabel
 *   The label to apply to the [Modifier.clickable] in the [HeaderCellView].
 * @property onClickColumnAction
 *   The action to perform when the [HeaderCellView] is clicked.
 */
data class ColumnAction constructor(
	val onClickLabel: String,
	val onClickColumnAction: () -> Unit)

/**
 * Add the [ColumnAction] to this [Modifier].
 *
 * @param columnAction
 *   The `ColumnAction` to add to the clickable.
 */
private fun Modifier.columnAction (columnAction: ColumnAction): Modifier
{
	clickable(
		onClickLabel = columnAction.onClickLabel,
		onClick = columnAction.onClickColumnAction)
	return this
}

/**
 * `DataColumn` contains information needed to display a column in a table.
 *
 * @author Richard A Arriaga
 *
 * @param T
 *   The type of object that provides data to populate cells in this column.
 * @property header
 *   The column title header. No header displayed if empty.
 * @property columnWeight
 *   The [Modifier]'s [RowScope] `weight` that determines the width of the
 *   column relative to other [DataColumn]s in the [TableView].
 * @property isEditable
 *   `true` indicates cells in this column are editable; `false` otherwise.
 * @property onClickColumnAction
 *   The [ColumnAction] associated with this [DataColumn] or `null` if there is
 *   no action.
 * @property dataCellView
 *   The [Composable] that accepts the data column source type, [T], row number,
 *   boolean indicating if in edit mode, then draws the [CellView] contents.
 * @property headerCellView
 *   The [Composable] that accepts the column header string, and draws the
 *   [CellView] contents.
 */
data class DataColumn<T> constructor(
	val header: String = "",
	val columnWeight: Float,
	val isEditable: Boolean = false,
	val onClickColumnAction: ColumnAction? = null,
	val headerCellModifier: Modifier = Modifier,
	val dataCellModifier: Modifier = Modifier,
	val headerCellView: @Composable BoxScope.(String) -> Unit,
	val dataCellView: @Composable
		BoxScope.(T, Int, MutableState<Boolean>) -> Unit)

/**
 * Draw a header row in a [TableView] for the provided [DataColumn]s.
 *
 * @param dataColumns
 *   The `DataColumn` list that provide each header for header row to be drawn.
 */
@Composable
fun RowScope.DrawHeaders (dataColumns: List<DataColumn<*>>)
{
	dataColumns.forEach {
		HeaderCellView(
			it.headerCellModifier,
			it.header,
			it.columnWeight,
			it.onClickColumnAction,
			it.headerCellView)
	}
}

/**
 * Draw a row in a [TableView] for the given `data` used to populate the row and
 * the [DataColumn] that provides the capability to draw each cell in the row.
 *
 * @param T
 *   The data type of the data used to populate the row.
 * @param data
 *   The data, `T`, that is used to populate the row.
 * @param dataColumns
 *   The `DataColumn`s list that provides the ability to draw each cell in the
 *   row.
 */
@Composable
fun <T> RowScope.DrawRow (
	data: T,
	rowNumber: Int,
	dataColumns: List<DataColumn<T>>)
{
	val focusRequesters = List(dataColumns.size) { FocusRequester() }
	dataColumns.forEachIndexed { idx, column ->
		val currentFocusRequester = focusRequesters[idx]
		val nextFocusRequester =
			if (idx < dataColumns.size - 1)
			{
				focusRequesters[idx + 1]
			}
			else
			{
				focusRequesters[0]
			}
		CellView(
			column.dataCellModifier.focusOrder(currentFocusRequester)
			{
				nextFocusRequester.requestFocus()
			}.focusRequester(currentFocusRequester),
			data,
			rowNumber,
			column.columnWeight,
			column.isEditable,
			column.dataCellView)
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                Table views.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * `TableView` represents a generic table of [DataColumn]s and [rows][DrawRow]
 * that are populated from a mutable list of data.
 *
 * The table is represented by a single [Column] that contains an optional
 * header [Row] drawn using [HeaderCellView]s followed by a [LazyColumn] of
 * [Row]s of [CellView]s. Each column in the table is described by a
 * [DataColumn].
 *
 * @author Richard Arriaga
 *
 * @param T
 *   The data type that provides data to populate each row.
 * @param modifier
 *   The modifier to apply to the outer table. The table is constructed using
 *   a column that contains
 * @param hasHeaders
 *   `true` indicates a header row should be [drawn][DrawHeaders]; `false`
 *   indicates no header row should be drawn.
 * @param data
 *   The list of data of type [T] used to populate the table.
 * @param columns
 *   The ordered list of [DataColumn]s that represents each column in the table.
 */
@Composable
internal fun <T> TableView(
	modifier: Modifier,
	hasHeaders: Boolean = false,
	data: MutableList<T>,
	columns: List<DataColumn<T>>)
{
	Column(modifier.fillMaxWidth())
	{
		if(hasHeaders)
		{
			Row(
				Modifier.height(IntrinsicSize.Min).fillMaxWidth(),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically)
			{
				DrawHeaders(columns)
			}
		}
		val listState = rememberLazyListState()
		// The LazyColumn represents the table data. Each row is broken up into
		// CellViews, with each cell assigned the same weight for column it is
		// in. This ensures that each column cell is uniform in size for each
		// row.
		LazyColumn (
			Modifier.fillMaxWidth(),
			state = listState)
		{
			// Draw the rows in the table.
			itemsIndexed(data) { idx, row ->
				Row (
					Modifier.height(IntrinsicSize.Min).fillMaxSize(),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically)
				{
					DrawRow(row, idx, columns)
				}
			}
		}
	}
}
