package avail.plugin

/**
 * An [AvailLibraryDependency] for obtaining Avail Standard Library:
 * `dependency("org.availlang:avail-stdlib:+")`.
 *
 * **NOTE** By default [AvailLibraryDependency.version] is set to `+` which
 * indicates the latest version in the repository should be used. To lock in a
 * target version, this should be explicitly set to a different value.
 *
 * The version of the Avail standard library to use
 * (org.availlang:avail-stdlib). By default, it is set to `+` which
 * indicates the latest version in the repository should be used.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailStandardLibrary].
 *
 * @param version
 *   The version of the Avail standard library to use.
 */
class AvailStandardLibrary constructor(version: String = "+"):
	AvailLibraryDependency(
		AvailPlugin.AVAIL,
		AvailPlugin.AVAIL_DEP_GRP,
		AvailPlugin.AVAIL_STDLIB_DEP_ARTIFACT_NAME,
		version)
