package org.availlang.ide.anvil.language

import com.intellij.lang.Language
import org.availlang.ide.anvil.language.file.AvailFileType

/**
 * The Avail [Language].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailLanguage: Language(
	"Avail",
	"text/avail",
	"text/x-avail",
	"application/x-avail")
{
	override fun getAssociatedFileType () = AvailFileType

	override fun isCaseSensitive () = true

	override fun getDisplayName () = "Avail"
}
