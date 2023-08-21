package avail.plugin

/**
 * A version for published Avail (org.availlang:avail-stdlib:VERSION).
 *
 * @author Richard Arriaga
 */
class AvailStdLibVersion constructor (
	versionString: String
): Comparable<AvailStdLibVersion>
{
	/**
	 * The major version component of the artifact build version.
	 */
	val artifactMajor: Int

	/**
	 * The minor version component of the artifact build version.
	 */
	val artifactMinor: Int

	/**
	 * The revision version component of the artifact build version.
	 */
	val artifactRevision: Int

	/**
	 * The major version component of the actual Avail Standard Library.
	 */
	val libraryMajor: Int

	/**
	 * The minor version component of the actual Avail Standard Library.
	 */
	val libraryMinor: Int

	/**
	 * The revision version component of the actual Avail Standard Library.
	 */
	val libraryRevision: Int

	init
	{
		val versionParts = versionString
			.removeSuffix("-SNAPSHOT")
			.split("-")
		if (versionParts.size == 1)
		{
			artifactMajor = 0
			artifactMinor = 0
			artifactRevision = 0

			val libraryParts = versionParts[0].split(".")
			libraryMajor = libraryParts[0].toInt()
			libraryMinor = libraryParts[1].toInt()
			libraryRevision = libraryParts[2].toInt()
		}
		else
		{
			val artifactParts = versionParts[0].split(".")
			artifactMajor = artifactParts[0].toInt()
			artifactMinor = artifactParts[1].toInt()
			artifactRevision = artifactParts[2].toInt()

			val libraryParts = versionParts[1].split(".")
			libraryMajor = libraryParts[0].toInt()
			libraryMinor = libraryParts[1].toInt()
			libraryRevision = libraryParts[2].toInt()
		}
	}

	private fun verAsString (ver: Int): String =
		if (ver < 10) { "0$ver"} else ver.toString()

	val version: String =
		"$artifactMajor.${verAsString(artifactMinor)}.${verAsString(artifactRevision)}" +
			"-\"$libraryMajor.${verAsString(libraryMinor)}.${verAsString(libraryRevision)}\""

	override fun toString (): String = version
	private fun compareArtifactVersion (other: AvailStdLibVersion): Int =
		when
		{
			artifactMajor > other.artifactMajor -> 1
			artifactMajor < other.artifactMajor -> -1
			else -> // major versions are equal
				when
				{

					artifactMinor > other.artifactMinor -> 1
					artifactMinor < other.artifactMinor -> -1
					else -> // minor versions are equal
						when
						{
							artifactRevision > other.artifactRevision -> 1
							artifactRevision < other.artifactRevision -> -1
							else ->  0 // revisions are equal
						}
				}
		}
	
	override fun compareTo(other: AvailStdLibVersion): Int =
		when
		{
			libraryMajor > other.libraryMajor -> 1
			libraryMajor < other.libraryMajor -> -1
			else -> // major versions are equal
				when
				{

					libraryMinor > other.libraryMinor -> 1
					libraryMinor < other.libraryMinor -> -1
					else -> // minor versions are equal
						when
						{
							libraryRevision > other.libraryRevision -> 1
							libraryRevision < other.libraryRevision -> -1
							else ->  compareArtifactVersion(other) // revisions are equal
						}
				}
		}
}
