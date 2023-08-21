package org.availlang.artifact.environment.location

/**
 * The supported URI schemes.
 *
 * @author Richard Arriaga
 */
enum class Scheme constructor(val prefix: String)
{
	/**
	 * The canonical representation of an invalid scheme.
	 */
	INVALID(""),

	/**
	 * A file location.
	 */
	FILE("file:///"),
//	{
//		override val optionalPrefix: String = ""
//	},

	/**
	 * A JAR file.
	 */
	JAR("jar://");

	open val optionalPrefix: String get() = prefix
 }
