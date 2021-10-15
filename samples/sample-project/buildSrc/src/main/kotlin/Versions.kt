import org.gradle.api.Project
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/*
 * Versions.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * The central source of all versions. This ranges from dependency versions
 * to language level versions.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Versions
{
	/** The version of Kotlin to be used by Avail. */
	const val kotlin = "1.5.21"

	/** The JVM target version for Kotlin. */
	const val jvmTarget = "16"

	/** The language level version of Kotlin. */
	const val kotlinLanguage = "1.5"

	/**
	 * The stripe release version of avail jars:
	 *  * `avail-core`
	 *  * `avail-workbench`
	 *  * `avail-stdlib`
	 *
	 *  This represents the version of this plugin.
	 */
	const val availStripeVersion = "1.6.0.20211015.161728"

	/**
	 * The location of the properties file that contains the last published
	 * release of the avail libraries.
	 */
	const val releaseVersionFile =
		"src/main/resources/releaseVersion.properties"

	/**
	 * Answer the version id for the `avail-plugin`.
	 *
	 * @param project
	 *   The running Gradle [Project].
	 */
	fun getReleaseVersion (project: Project): String
	{
		val propsFile = FileInputStream(releaseVersionFile)
		val props = Properties()
		props.load(propsFile)
		return props.getProperty("releaseVersion")
	}
}
