package org.availlang.ide.anvil.langauge.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.availlang.ide.anvil.Anvil
import org.availlang.ide.anvil.langauge.AvailIcons
import org.availlang.ide.anvil.langauge.AvailLanguage
import org.availlang.json.JSONWriter
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

/**
 * `AvailFileType` is the [LanguageFileType] for the [AvailLanguage].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailFileType: LanguageFileType(AvailLanguage)
{
	// Must match <fileType name="..."> in plugin.xml
	override fun getName(): String = "Avail File"
	val b = AtomicBoolean(true)

	override fun getDescription(): String
	{
		if (b.getAndSet(false))
		{
			val project =
				Anvil.projectDescriptor.project { println("Project!!!") }
			val r = project.runtime
//			val writer = JSONWriter()
//			writer.startObject()
//			writer.at("foo") { write(6) }
//			writer.endObject()
		}
		return "Avail language file ${Anvil.CONFIG_FILE}"
	}

	override fun getDefaultExtension(): String = "avail"

	override fun getIcon(): Icon = AvailIcons.moduleFileImage
}
