package org.availlang.intellij.plugin.icons

import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.Icon

/**
 * The object that provides access to all Avail plugin icon assets.
 *
 * @author Richard Arriaga
 */
object AvailIcons
{
	/**
	 * Answer the associated icon.
	 *
	 * @param path
	 *   The resource-folder relative path to the icon to retrieve.
	 * @return
	 *   The retrieved [Icon].
	 */
	private fun icon (path: String): Icon = getIcon(path, javaClass)

	/** The Avail module file icon.  */
	@JvmField
	val AvailModule32x32 = icon("icons/icon_module_file")
}
