/**
 * Configurator.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.utility.configuration;


/**
 * A {@code Configurator} produces a {@linkplain Configuration configuration}
 * from information provided during construction of a concrete implementation.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <ConfigurationType>
 *        The type of resulting {@linkplain Configuration configurations}.
 */
public interface Configurator<ConfigurationType extends Configuration>
{
	/**
	 * Using information provided during construction of the {@linkplain
	 * Configurator configurator}, produce the entailed {@linkplain
	 * Configuration configuration}.
	 *
	 * <p>Implementations should be idempotent, i.e., multiple invocations
	 * should produce the same observable effect as a single invocation.</p>
	 *
	 * @throws ConfigurationException
	 *         If the configuration could not be created for any reason.
	 */
	public void updateConfiguration () throws ConfigurationException;

	/**
	 * Answer the {@linkplain Configuration configuration} produced by the
	 * {@linkplain Configurator configurator}.
	 *
	 * @return A configuration.
	 */
	public ConfigurationType configuration ();
}
