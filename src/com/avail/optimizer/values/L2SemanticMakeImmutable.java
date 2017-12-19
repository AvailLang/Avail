/**
 * L2SemanticMakeImmutable.java
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
package com.avail.optimizer.values;
/**
 * A semantic value which ensures the inner semantic value being wrapped has
 * been made immutable.
 */
final class L2SemanticMakeImmutable extends L2SemanticValue
{
	/** The semantic value that is wrapped with the immutability assurance. */
	public final L2SemanticValue innerSemanticValue;

	/**
	 * Create a new {@code L2SemanticMakeImmutable} semantic value.
	 *
	 * @param innerSemanticValue
	 *        The semantic value being wrapped.
	 */
	L2SemanticMakeImmutable (
		final L2SemanticValue innerSemanticValue)
	{
		assert !(innerSemanticValue instanceof  L2SemanticMakeImmutable);
		this.innerSemanticValue = innerSemanticValue;
	}

	@Override
	public L2SemanticValue immutable ()
	{
		// It's already immutable.  Immutability wrapping is idempotent.
		return this;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticMakeImmutable))
		{
			return false;
		}
		final L2SemanticMakeImmutable inner = (L2SemanticMakeImmutable) obj;
		return innerSemanticValue.equals(inner.innerSemanticValue);
	}

	@Override
	public int hashCode ()
	{
		return innerSemanticValue.hashCode() ^ 0xB9E019AE;
	}

	@Override
	public String toString ()
	{
		return "Immutable(" + innerSemanticValue + ")";
	}
}
