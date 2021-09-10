package com.avail.build

import org.gradle.api.tasks.testing.Test

/**
 * `Configuration` contains static data specific to building.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ConfigurationContext
{
	/**
	 * The relative path to the Kotlin source directory.
	 */
	const val kotlinSourcePath = "src/main/kotlin"

	/**
	 * The group component of the Maven identifier for this project.
	 */
	const val mavenGroup = "com.avail"

	/**
	 * The source path for the documentation source.
	 */
	const val documentationSrcPath = "documentation/docs/src_docs"

	/**
	 * The path for the generated documentation site.
	 */
	const val documentationSitePath = "documentation/site"
}

/**
 * Generally configure the [Test].
 */
fun Test.configureTest()
{
	useJUnitPlatform()
	minHeapSize = "4g"
	maxHeapSize = "6g"
	enableAssertions = true
}
