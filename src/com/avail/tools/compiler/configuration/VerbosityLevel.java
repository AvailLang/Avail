/**
 * VerbosityLevel.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.tools.compiler.configuration;

/**
 * The level of verbosity requested of the compiler.
 *   0 ~ error message only (default)
 *   1 ~ global build progress + error messages
 *   2 ~ global build progress + local build progress + error messages
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public enum VerbosityLevel
{
	/**
	 * The default level when verbosity is not requested. Only error messages
	 * are emitted at this level.
	 */
	ERROR_ONLY,

	/**
	 * The default level when verbosity is requested but a level is not
	 * specified. At this level the compiler emits the global build progress and
	 * any error messages.
	 */
	GLOBAL_PROGRESS
	{
		@Override
		public boolean displayGlobalProgress ()
		{
			return true;
		}
	},

	/**
	 * At this level, both the global build progress and the local build
	 * progress are output, plus any error messages.
	 */
	GLOBAL_LOCAL_PROGRESS
	{
		@Override
		public boolean displayGlobalProgress ()
		{
			return true;
		}
		@Override
		public boolean displayLocalProgress ()
		{
			return true;
		}
	};

	/**
	 * Supplies the VerbosityLevel corresponding to the supplied integer.
	 *
	 * @param i The supplied integer.
	 * @return The VerbosityLevel.
	 */
	static VerbosityLevel atLevel (final int i)
	{
		return values()[i];
	}

	/**
	 * @return True only when verbosity level 1 or 2 is selected. This is
	 *         accomplished through method overrides.
	 */
	public boolean displayGlobalProgress()
	{
		return false;
	}

	/**
	 * @return True only when verbosity level 2 is selected. This is
	 *         accomplished through method overrides.
	 */
	public boolean displayLocalProgress()
	{
		return false;
	}
}
