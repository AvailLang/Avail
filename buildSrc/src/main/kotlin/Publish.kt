/*
 * Publish.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

import avail.build.Utility.formattedNow

/**
 * Contains state and functionality relative to publish Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Publish
{
	val githubUsername: String get() =
		System.getenv("GITHUB_USER") ?: ""
	val githubPassword: String get() =
		System.getenv("GITHUB_TOKEN") ?: ""

	private const val credentialsWarning =
		"Missing credentials.  To publish, you'll need to create a GitHub " +
			"token:\n" +
		(
			"https://help.github.com/en/actions/" +
				"configuring-and-managing-workflows/" +
				"authenticating-with-the-github_token") +
		"\n" +
		"Then set GITHUB_USER and GITHUB_TOKEN variables to hold your github " +
		"username and the (generated hexadecimal) token, respectively, in " +
		"~/.bash_profile or other appropriate login script." +
		"\n" +
		"Remember to restart IntelliJ after this change."

	/**
	 * Check that the publish task has access to the necessary credentials.
	 */
	fun checkCredentials ()
	{
		if (githubUsername.isEmpty() || githubPassword.isEmpty())
		{
			System.err.println(credentialsWarning)
		}
	}
}
