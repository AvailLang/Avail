/*
 * MavenCentralAPI.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.versions

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.json.JSONObject
import org.availlang.json.JSONReader
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels

/**
 * The [REST API](https://central.sonatype.org/search/rest-api-guide/) for
 * interacting with [Sonatype Maven Central](https://central.sonatype.org/).
 *
 * @author Richard Arriaga
 */
object MavenCentralAPI
{
	/**
	 * Answer the [URL] to search for the latest version of an Avail artifact in
	 * Maven Central. This artifact is expected to stored in group,
	 * `org.availlang`.
	 *
	 * @param artifact
	 *   The name of the JAR artifact to look up.
	 * @return
	 *   The constructed search [URL].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun searchUrl (artifact: String): URL =
		URL("https://search.maven.org/solrsearch/select?q=g:org.availlang" +
			"%20AND%20a:${artifact}%20AND%20p:jar&rows=1&wt=json")

	/**
	 * The URL to search for the latest version of `avail-stdlib`
	 */
	private val searchAvailLibUrl = searchUrl("avail-stdlib")

	/**
	 * The URL to search for the latest version of `avail` SDK.
	 */
	private val searchAvailSdkUrl = searchUrl("avail")

	/**
	 * Search for the Avail artifact using the given URL to perform the search.
	 *
	 * @param url
	 *   The API [URL] to search.
	 * @param success
	 *   Accepts the [JSONReader] that contains the response.
	 * @param failure
	 *   Accepts the [HttpURLConnection.getResponseCode] and
	 *   [HttpURLConnection.responseMessage] in the event of a failure.
	 */
	private fun search (
		url: URL,
		success: (JSONReader) -> Unit,
		failure: (Int, String?) -> Unit)
	{
		val con = url.openConnection() as HttpURLConnection
		con.requestMethod = "GET"
		val rspCode = con.responseCode
		if (rspCode != 200)
		{
			failure(rspCode, con.responseMessage)
			return
		}
		val responseReader = BufferedReader(InputStreamReader(con.inputStream))
		success(JSONReader(responseReader))
	}

	/**
	 * Search for the Avail standard library artifact, `avail-stdlib`.
	 *
	 * @param success
	 *   Accepts the [JSONReader] that contains the response.
	 * @param failure
	 *   Accepts the [HttpURLConnection.getResponseCode] and
	 *   [HttpURLConnection.responseMessage] in the event of a failure.
	 */
	fun searchAvailStdLib (
		success: (JSONReader) -> Unit,
		failure: (Int, String?) -> Unit)
	{
		search(searchAvailLibUrl, success, failure)
	}

	/**
	 * Search for the Avail SDK artifact, `avail`.
	 *
	 * @param success
	 *   Accepts the [JSONReader] that contains the response.
	 * @param failure
	 *   Accepts the [HttpURLConnection.getResponseCode] and
	 *   [HttpURLConnection.responseMessage] in the event of a failure.
	 */
	fun searchAvailSdk (
		success: (JSONReader) -> Unit,
		failure: (Int, String?) -> Unit)
	{
		search(searchAvailSdkUrl, success, failure)
	}

	/**
	 * The [URL] used to download the targeted Avail artifact.
	 *
	 * @param artifactId
	 *   The artifact id of the Avail artifact to download.
	 * @param version
	 *   The version of the Avail artifact to download.
	 * @return
	 *   The constructed download [URL].
	 */
	private fun artifactDownloadUrl (
		artifactId: String,
		version: String
	): URL = URL("https://search.maven.org/remotecontent?filepath=" +
		"org/availlang/$artifactId/$version/$artifactId-$version.jar")

	/**
	 * Download the provided file [URL] to the target location.
	 *
	 * @param fileURL
	 *   The [URL] of the file to download.
	 * @param target
	 *   The file path were to write the file.
	 * @return
	 *   The number of bytes written.
	 */
	private fun download (fileURL: URL, target: String): Long =
		FileOutputStream(target).channel.transferFrom(
			Channels.newChannel(fileURL.openStream()), 0, Long.MAX_VALUE)

	/**
	 * Download the Avail Standard Library artifact of the provided version to
	 * the [AvailEnvironment.availHomeLibs] in "org/availlang/".
	 *
	 * @param version
	 *   The version of the artifact to download.
	 * @return
	 *   The number of bytes written to the file.
	 */
	fun downloadAvailStandardLib (version: String): Long =
		download(
			artifactDownloadUrl("avail-stdlib", version),
			"${AvailEnvironment.availHomeLibs}/org/availlang/" +
				"avail-stdlib-$version.jar")
}

/**
 * The interface that declares the state expected to be read from a response
 * to a search for an Avail artifact.
 *
 * @author Richard Arriaga
 */
sealed interface SearchResponse
{
	/**
	 * The version of this Maven Central API search response.
	 */
	val apiVersion: String

	/**
	 * The latest version of the target Avail artifact stored in Maven Central.
	 */
	val latestLibVersion: String

	companion object
	{
		/**
		 * The supported search API versions.
		 */
		val supportedSearchVersions = setOf("2.2")

		/**
		 * Parse the search response into a [SearchResponse].
		 *
		 * @param reader
		 *   The [JSONReader] that contains the response.
		 * @return
		 *   The parsed [SearchResponse] or
		 */
		fun parse (reader: JSONReader): SearchResponse?
		{
			val obj = reader.read() as JSONObject
			val rspHeader =
				obj.getObjectOrNull("responseHeader") ?: return null
			val params = rspHeader.getObjectOrNull("params") ?: return null
			val version = params.getStringOrNull("version") ?: return null
			if (!supportedSearchVersions.contains(version))
			{
				System.err.println(
					"Maven Central API version not supported: $version")
			}
			return when (version)
			{
				"2.2" -> SearchResponseV2_2.parse(obj)
				else -> null
			}
		}
	}
}

/**
 * Version 2.2 of [SearchResponse].
 *
 * @author Richard Arriaga
 */
class SearchResponseV2_2 constructor(
	override val latestLibVersion: String
): SearchResponse
{
	override val apiVersion: String = "2.2"

	companion object
	{
		/**
		 * Parse the provided [JSONObject] into a [SearchResponseV2_2].
		 *
		 * @param obj
		 *   The JSON response [JSONObject] to parse.
		 * @return
		 *   The parsed [SearchResponseV2_2] or `null` if none.
		 */
		fun parse (obj: JSONObject): SearchResponseV2_2?
		{
			val rsp = obj.getObjectOrNull("response") ?: return null
			val docs = rsp.getArrayOrNull("docs") ?: return null
			val first = docs.getObjectOrNull(0) ?: return null
			val latest =
				first.getStringOrNull("latestVersion") ?: return null
			return SearchResponseV2_2(latest)
		}
	}
}
