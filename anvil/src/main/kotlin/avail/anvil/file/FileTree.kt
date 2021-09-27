package avail.anvil.file

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import avail.anvil.components.AsyncImageBitmap
import avail.anvil.components.AsyncSvg
import avail.anvil.themes.AlternatingRowColor
import avail.anvil.themes.AvailColors
import avail.anvil.themes.ImageResources
import avail.anvil.themes.LoadedStyle

@Composable
fun ItemView (node: AvailNode, rowColor: AlternatingRowColor)
{
	if (node.children.isEmpty())
	{

		return
	}
	var isExpanded by remember { mutableStateOf(false) }
	AsyncSvg(
		file = if (isExpanded) ImageResources.rootFileImage else ImageResources.rootFileImage,
		modifier = Modifier.widthIn(max = 20.dp))

	Spacer(Modifier.width(5.dp))
	AsyncSvg(
		file = ImageResources.rootFileImage,
		modifier = Modifier.widthIn(max = 20.dp))
	Spacer(Modifier.width(4.dp))
	Text(
		text = node.reference.localName,
		color = LoadedStyle.color,
		fontSize = LoadedStyle.size)
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
		AsyncSvg(
			file = ImageResources.rootFileImage,
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
		LazyColumn(state = listState, modifier = Modifier.padding(start = 15.dp))
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
			.padding(vertical = 3.dp, horizontal = 12.dp)
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
			.padding(vertical = 3.dp, horizontal = 12.dp)
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
			.padding(vertical = 3.dp, horizontal = 12.dp)
			.background(rowColor.color),
		verticalAlignment = Alignment.CenterVertically)
	{
		AsyncSvg(
			file = ImageResources.resourceFileImage,
			modifier = Modifier.widthIn(max = 20.dp))
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
	val arrow = if (isExpanded) { "▽" } else { "˃˃˃˃˃˃˲͕>˅⌄⬎" }
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
		Column(modifier = Modifier.padding(start = 15.dp))
		{
			node.sortedChildren.forEach {
				it.draw(rowColor)
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
		AsyncSvg(
			file = ImageResources.resourceDirectoryImage,
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
		Column(modifier = Modifier.padding(start = 15.dp))
		{
			node.sortedChildren.forEach {
				it.draw(rowColor)
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
//
//@Composable
//private fun FileItemIcon(modifier: Modifier, node: AvailNode) =
//	Box(modifier.size(24.dp).padding(4.dp))
//	{
//		when (node)
//		{
//			is DirectoryNode -> TODO()
//			is ModuleNode -> TODO()
//			is ModulePackageNode -> TODO()
//			is RootNode -> TODO()
//			is ResourceNode -> TODO()
//		}
//		when (node) {
//			is FileTree.ItemType.Folder -> when {
//				!type.canExpand -> Unit
//				type.isExpanded -> Icon(
//					Icons.Default.KeyboardArrowDown, contentDescription = null, tint = LocalContentColor.current
//				)
//				else -> Icon(
//					Icons.Default.KeyboardArrowRight, contentDescription = null, tint = LocalContentColor.current
//				)
//			}
//			is FileTree.ItemType.File -> when (type.ext) {
//				"kt" -> Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF3E86A0))
//				"xml" -> Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFFC19C5F))
//				"txt" -> Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF87939A))
//				"md" -> Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF87939A))
//				"gitignore" -> Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color(0xFF87939A))
//				"gradle" -> Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF87939A))
//				"kts" -> Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF3E86A0))
//				"properties" -> Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF62B543))
//				"bat" -> Icon(Icons.Default.Launch, contentDescription = null, tint = Color(0xFF87939A))
//				else -> Icon(Icons.Default.TextSnippet, contentDescription = null, tint = Color(0xFF87939A))
//			}
//		}
//}
