package org.availlang.intellij.plugin.assets

import com.intellij.ui.IconManager
import javax.swing.Icon

/**
 * The object that provides access to all Avail plugin icon assets.
 *
 * @author Richard Arriaga
 */
object AvailIcons
{
	/**
	 * Load the specific Avail icon in the resources folder.
	 *
	 * @param path
	 *   The resources directory relative path to the asset to load.
	 * @param cacheKey
	 *   TODO figure out how this is used and how we should assign these keys.
	 * @param flags
	 *   TODO figure out how this is used and what we should pass in.
	 */
	private fun load(path: String, cacheKey: Int, flags: Int): Icon =
		IconManager.getInstance().loadRasterizedIcon(
			path,
			AvailIcons::class.java.getClassLoader(),
			cacheKey,
			flags)

	/** The Avail module file icon.  */
	val AvailModule =
		load(
			"icons/avail-icon-file-avail.svg",
			189921742,
			2)
}
