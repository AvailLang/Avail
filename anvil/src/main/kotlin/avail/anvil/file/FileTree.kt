package avail.anvil.file

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import avail.anvil.components.AsyncImageBitmap
import avail.anvil.themes.AlternatingRowColor
import avail.anvil.themes.AvailColors
import avail.anvil.themes.ImageResources
import avail.anvil.themes.LoadedStyle

@Composable
fun FileExplorerView ()
{
	Surface(
		modifier =
			Modifier.background(AvailColors.BG).fillMaxSize().padding(10.dp),
		color = AvailColors.BG)
	{
		Column(Modifier.fillMaxSize())
		{
			DirectoryStructureView(
				"Avail",
				listOf("Atoms", "Maps", "Numbers", "Tuples", "Sets"))
		}
	}
}
/**
 * A `FileTree` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Composable
fun DirectoryStructureView (name: String, files: List<String>)
{
	var isExpanded by remember { mutableStateOf(false) }
	var rowColor = AlternatingRowColor.ROW2
	val arrow = if (isExpanded) { "▽" } else { "▷" }
	Row (
		Modifier.clickable { isExpanded = !isExpanded }
			.background(rowColor.color)
			.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically)
	{
		DirectoryView(name, arrow)
	}
	if (isExpanded)
	{
		rowColor = rowColor.next
		val listState = rememberLazyListState()
		LazyColumn (state = listState)
		{
			items(files) {
				Row (
					modifier = Modifier.fillMaxWidth()
						.padding(vertical = 3.dp, horizontal = 35.dp)
						.background(rowColor.color),
					verticalAlignment = Alignment.CenterVertically)
				{
					AsyncImageBitmap(
						file = ImageResources.moduleFileImage,
						modifier = Modifier.widthIn(max = 20.dp).heightIn(max = 20.dp))
					Text(
						text = name,
						color = LoadedStyle.color,
						fontSize = LoadedStyle.size,
						modifier =
						Modifier.align(Alignment.CenterVertically).padding(horizontal = 5.dp))
					rowColor = rowColor.next
				}
			}
		}
	}
}

/**
 * A `FileTree` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@Composable
fun ModuleRootView (node: RootNode)
{
	var rowColor = AlternatingRowColor.ROW2
	var isExpanded by remember { mutableStateOf(false) }
	val arrow = if (isExpanded) { "▽" } else { "▷" }
	Row (
		Modifier.clickable { isExpanded = !isExpanded }
			.background(rowColor.color)
			.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically)
	{
		Text(
			text = arrow,
			fontSize = 8.sp,
			color = AvailColors.GREY)

		Spacer(Modifier.width(5.dp))
		AsyncImageBitmap(
			file = ImageResources.packageFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
		Spacer(Modifier.width(4.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size)
	}
	if (isExpanded)
	{
		rowColor = rowColor.next
		val listState = rememberLazyListState()
		LazyColumn (state = listState)
		{
			items(node.sortedChildren) {
				it.draw(rowColor)
			}
		}
	}
}

@Composable
fun ModuleView (
	node: ModuleNode,
	alternatingColor: AlternatingRowColor)
{
	val rowColor = alternatingColor.next
	Row (
		modifier = Modifier.fillMaxWidth()
			.padding(vertical = 3.dp, horizontal = 35.dp)
			.background(rowColor.color),
		verticalAlignment = Alignment.CenterVertically)
	{
		AsyncImageBitmap(
			file = ImageResources.moduleFileImage,
			modifier = Modifier.widthIn(max = 20.dp).heightIn(max = 20.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size,
			modifier =
			Modifier.align(Alignment.CenterVertically).padding(horizontal = 5.dp))
	}
}

@Composable
fun ModuleWithEntriesView (
	node: ModuleNode,
	alternatingColor: AlternatingRowColor)
{
	val rowColor = alternatingColor.next
	Row (
		modifier = Modifier.fillMaxWidth()
			.padding(vertical = 3.dp, horizontal = 35.dp)
			.background(rowColor.color),
		verticalAlignment = Alignment.CenterVertically)
	{
		AsyncImageBitmap(
			file = ImageResources.moduleFileImage,
			modifier = Modifier.widthIn(max = 20.dp).heightIn(max = 20.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size,
			modifier =
			Modifier.align(Alignment.CenterVertically).padding(horizontal = 5.dp))
	}
}

@Composable
fun ResourceView (
	node: ResourceNode,
	alternatingColor: AlternatingRowColor)
{
	val rowColor = alternatingColor.next
	Row (
		modifier = Modifier.fillMaxWidth()
			.padding(vertical = 3.dp, horizontal = 35.dp)
			.background(rowColor.color),
		verticalAlignment = Alignment.CenterVertically)
	{
		AsyncImageBitmap(
			file = ImageResources.moduleFileImage,
			modifier = Modifier.widthIn(max = 20.dp).heightIn(max = 20.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size,
			modifier =
			Modifier.align(Alignment.CenterVertically).padding(horizontal = 5.dp))
	}
}


@Composable
fun ModulePackageView (
	node: ModulePackageNode,
	alternatingColor: AlternatingRowColor)
{
	var isExpanded by remember { mutableStateOf(false) }
	var rowColor = alternatingColor.next
	val arrow = if (isExpanded) { "▽" } else { "▷" }
	Row (
		Modifier.clickable { isExpanded = !isExpanded }
			.background(rowColor.color)
			.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically)
	{
		Text(
			text = arrow,
			fontSize = 8.sp,
			color = AvailColors.GREY)

		Spacer(Modifier.width(5.dp))
		AsyncImageBitmap(
			file = ImageResources.packageFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
		Spacer(Modifier.width(4.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size)
	}
	if (isExpanded)
	{
		rowColor = rowColor.next
		val listState = rememberLazyListState()
		LazyColumn (state = listState, modifier = Modifier.padding(start = 15.dp))
		{

			items(node.sortedChildren) {
				it.draw(alternatingColor)
			}
		}
	}
}

@Composable
fun DirectoryView (
	node: DirectoryNode,
	alternatingColor: AlternatingRowColor)
{
	var isExpanded by remember { mutableStateOf(false) }
	var rowColor = alternatingColor.next
	val arrow = if (isExpanded) { "▽" } else { "▷" }
	Row (
		Modifier.clickable { isExpanded = !isExpanded }
			.background(rowColor.color)
			.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically)
	{
		Text(
			text = arrow,
			fontSize = 8.sp,
			color = AvailColors.GREY)

		Spacer(Modifier.width(5.dp))
		AsyncImageBitmap(
			file = ImageResources.packageFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
		Spacer(Modifier.width(4.dp))
		Text(
			text = node.reference.localName,
			color = LoadedStyle.color,
			fontSize = LoadedStyle.size)
	}
	if (isExpanded)
	{
		rowColor = rowColor.next
		val listState = rememberLazyListState()
		LazyColumn (state = listState, modifier = Modifier.padding(start = 15.dp))
		{
			items(node.sortedChildren) {
				it.draw(alternatingColor)
			}
		}
	}
}

@Composable
fun DirectoryView (name: String, arrow: String)
{
	Text(
		text = arrow,
		fontSize = 8.sp,
		color = AvailColors.GREY)

	Spacer(Modifier.width(5.dp))
	AsyncImageBitmap(
		file = ImageResources.packageFileImage,
		modifier = Modifier.widthIn(max = 20.dp))
	Spacer(Modifier.width(4.dp))
	Text(
		text = name,
		color = LoadedStyle.color,
		fontSize = LoadedStyle.size)
}
