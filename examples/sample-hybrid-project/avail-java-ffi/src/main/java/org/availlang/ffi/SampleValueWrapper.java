/*
 * SampleFFIUtility.java
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package org.availlang.ffi;
import java.util.Objects;
import java.util.UUID;

/**
 * A {@code SampleValueWrapper} is a sample class that wraps a value.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
@SuppressWarnings("unused")
public class SampleValueWrapper
{
	/**
	 * The wrapped value.
	 */
	public final int value;

	/**
	 * The unique id of this {@link SampleValueWrapper}.
	 */
	public final String id = UUID.randomUUID().toString();

	/**
	 * The time in milliseconds since the Unix Epoch when this created.
	 */
	public final Long created = System.currentTimeMillis();

	/**
	 * @return
	 *   Answer the wrapped value.
	 */
	public int getValue ()
	{
		return value;
	}

	/**
	 * Answer a new {@link SampleValueWrapper} that wraps a new value that is
	 * the sum of the {@link SampleValueWrapper#value} and the provided
	 * {@code addend}.
	 *
	 * @param addend
	 *   The value to add to the wrapped value.
	 * @return
	 *   A new {@code SampleValueWrapper} that wraps the sum.
	 */
	public SampleValueWrapper add (final int addend)
	{
		return new SampleValueWrapper(value + addend);
	}

	@Override
	public String toString ()
	{
		return "SampleValueWrapper{" + value +
			", " + id + "}";
	}

	/**
	 * Construct a new {@link SampleValueWrapper}.
	 *
	 * @param value
	 *   The value to wrap.
	 */
	public SampleValueWrapper (final int value)
	{
		this.value = value;
	}

	@Override
	public boolean equals (final Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof SampleValueWrapper))
		{
			return false;
		}
		final SampleValueWrapper that = (SampleValueWrapper) o;
		return value == that.value;
	}

	@Override
	public int hashCode ()
	{
		return Objects.hash(value);
	}
}
