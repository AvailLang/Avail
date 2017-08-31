/**
 * IO.java
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

package com.avail.utility;

import javax.annotation.Nullable;

/**
 * {@code IO} is a utility class for I/O operations.
 *
 * Prominent among the operations provided are {@link #close(AutoCloseable)
 * close} operations that suppress the annoying checked {@link Exception}s that
 * Java's {@linkplain AutoCloseable#close()} operation throws <em>for no earthly
 * reason</em>. The {@code #close(AutoCloseable) close} methods defined here
 * silence exceptions, the way God intended. If you should encounter a bizarre
 * case where a {@code close} operation should really throw an exception, then
 * <em>do not use the {@code close} methods defined herein</em>.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class IO
{
	/**
	 * Attempt to {@linkplain AutoCloseable#close() close} the specified {@link
	 * AutoCloseable resource}. Suppress any {@linkplain Exception exception}
	 * thrown by the underlying {@code close} operation.
	 *
	 * @param closeable
	 *        A closeable resource. <strong>Must not be {@code null}</strong>;
	 *        see {@link #closeIfNotNull(AutoCloseable) closeIfNotNull} to
	 *        handle that case.
	 */
	public static void close (final AutoCloseable closeable)
	{
		try
		{
			closeable.close();
		}
		catch (final Exception e)
		{
			// Do nothing; suppress this exception.
		}
	}

	/**
	 * Attempt to {@linkplain AutoCloseable#close() close} the specified {@link
	 * AutoCloseable resource}, which is generously permitted to be {@code
	 * null}. Suppress any {@linkplain Exception exception} thrown by the
	 * underlying {@code close} operation.
	 *
	 * @param closeable
	 *        A closeable resource, or {@code null}.
	 */
	public static void closeIfNotNull (final @Nullable AutoCloseable closeable)
	{
		if (closeable != null)
		{
			try
			{
				closeable.close();
			}
			catch (final Exception e)
			{
				// Do nothing; suppress this exception.
			}
		}
	}
}
