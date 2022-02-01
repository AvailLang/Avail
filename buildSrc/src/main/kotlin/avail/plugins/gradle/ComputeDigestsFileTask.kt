package avail.plugins.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import java.io.File
import java.security.MessageDigest

/**
 * Construct a single file that summarizes message digests of all files of a
 * directory structure.  Each input file is read, digested with the specified
 * [digestAlgorithm] (default is SHA-256), and written to the digests file.
 * The entries are the file name relative to the basePath, a colon, the hex
 * representation of the digest of that file, and a linefeed.
 *
 * This digests file is used within an .avail library, such as avail-stdlib.jar.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class CreateDigestsFileTask : DefaultTask()
{
	@Input
	var basePath: String = ""

	@Input
	var digestAlgorithm: String = "SHA-256"

	@TaskAction
	fun createDigestsFile() {
		val baseFile = File(basePath)
		val digestsString = buildString {
			inputs.files.files
				.filter(File::isFile)
				.forEach { file ->
					val digest = MessageDigest.getInstance(digestAlgorithm)
					digest.update(file.readBytes())
					val hex = digest.digest()
						.joinToString("") { String.format("%02X", it) }
					append("${file.toRelativeString(baseFile)}:$hex\n")
				}
		}
		outputs.files.singleFile.writeText(digestsString)
	}
}
