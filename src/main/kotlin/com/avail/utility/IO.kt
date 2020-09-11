/*
 * IO.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.utility

/**
 * `IO` is a utility class for I/O operations.
 *
 * Prominent among the operations provided are [close][.close] operations that
 * suppress the annoying checked [Exception]s that Java's [AutoCloseable.close]
 * operation throws *for no earthly reason*. The `#close(AutoCloseable) close`
 * methods defined here silence exceptions, the way God intended. If you should
 * encounter a bizarre case where a `close` operation should really throw an
 * exception, then  *do not use the `close` methods defined herein*.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object IO
{
	/**
	 * Attempt to [close][AutoCloseable.close] the specified
	 * [resource][AutoCloseable]. Suppress any [exception][Exception] thrown by
	 * the underlying `close` operation.
	 *
	 * @param closeable
	 *   A closeable resource. **Must not be `null`**; see
	 *   [closeIfNotNull][.closeIfNotNull] to handle that case.
	 */
	fun close(closeable: AutoCloseable)
	{
		try
		{
			closeable.close()
		}
		catch (e: Exception)
		{
			// Do nothing; suppress this exception.
		}
	}

	/**
	 * Attempt to [close][AutoCloseable.close] the specified
	 * [resource][AutoCloseable], which is generously permitted to be `null`.
	 * Suppress any [exception][Exception] thrown by the underlying `close`
	 * operation.
	 *
	 * @param closeable
	 *   A closeable resource, or `null`.
	 */
	@JvmStatic
	fun closeIfNotNull(closeable: AutoCloseable?)
	{
		if (closeable !== null)
		{
			try
			{
				closeable.close()
			}
			catch (e: Exception)
			{
				// Do nothing; suppress this exception.
			}
		}
	}
}
