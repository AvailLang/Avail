/*
 * Configurator.kt
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

package avail.utility.configuration

/**
 * A `Configurator` produces a [configuration][Configuration] tuple information
 * provided during construction of a concrete implementation.
 *
 * @param ConfigurationType
 *   The type of resulting [configurations][Configuration].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface Configurator<ConfigurationType : Configuration>
{
	/**
	 * Using information provided during construction of the
	 * [configurator][Configurator], produce the entailed
	 * [configuration][Configuration].
	 *
	 * Implementations should be idempotent, i.e., multiple invocations should
	 * produce the same observable effect as a single invocation.
	 *
	 * @throws ConfigurationException
	 *   If the configuration could not be created for any reason.
	 */
	@Throws(ConfigurationException::class)
	fun updateConfiguration()

	/**
	 * The [configuration][Configuration] produced by the
	 * [configurator][Configurator].
	 */
	val configuration: ConfigurationType
}
