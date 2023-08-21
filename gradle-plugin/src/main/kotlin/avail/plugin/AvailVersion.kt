package avail.plugin

/**
 * A version for published Avail (org.availlang:avail:VERSION).
 *
 * @author Richard Arriaga
 */
class AvailVersion constructor (
	versionString: String
): Comparable<AvailVersion>
{
	/**
	 * The major version component.
	 */
	val major: Int

	/**
	 * The minor version component.
	 */
	val minor: Int

	/**
	 * The revision version component.
	 */
	val revision: Int

	init
	{
		val parts = versionString
			.removeSuffix("-SNAPSHOT")
			.split(".")
		major = parts[0].toInt()
		minor = parts[1].toInt()
		revision = parts[2].toInt()
	}

	private fun verAsString (ver: Int): String =
		if (ver < 10) { "0$ver"} else ver.toString()

	val version: String =
		"$major.${verAsString(minor)}.${verAsString(revision)}"

	override fun toString (): String = version
	override fun compareTo(other: AvailVersion): Int =
		when
		{
			major > other.major -> 1
			major < other.major -> -1
			else -> // major versions are equal
				when
				{

					minor > other.minor -> 1
					minor < other.minor -> -1
					else -> // minor versions are equal
						when
						{
							revision > other.revision -> 1
							revision < other.revision -> -1
							else ->  0 // revisions are equal
						}
				}
		}
}
