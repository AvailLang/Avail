/**
 * FieldModifier.java
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

package com.avail.interpreter.jvm;

import java.util.EnumSet;

/**
 * {@code FieldModifier} provides constants that describe the permissible
 * access and property modifiers for fields.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5-200-A.1">
 *     Field access and property flags</a>
 */
public enum FieldModifier
implements Modifier
{
	/** Declared {@code public}; may be accessed from outside its package. */
	PUBLIC (0x0001),

	/** Declared {@code private}; accessible only within the defining class. */
	PRIVATE (0x0002),

	/** Declared {@code protected}; may be accessed within subclasses. */
	PROTECTED (0x0004),

	/** Declared {@code static}. */
	STATIC (0x0008),

	/** Declared {@code final}; no subclasses allowed. */
	FINAL (0x0010),

	/** Declared {@code volatile}; cannot be cached. */
	VOLATILE (0x0040),

	/**
	 * Declared {@code transient}; not written or read by a persistent object
	 * manager.
	 */
	TRANSIENT (0x0080),

	/** Declared synthetic; not present in the source code. */
	SYNTHETIC (0x1000),

	/** Declared as an element of an {@code enum}. */
	ENUM (0x4000);

	/** The unique modifier bit. */
	public final int bit;

	@Override
	public String toString ()
	{
		return super.toString().toLowerCase();
	}

	/**
	 * Construct a new {@link FieldModifier}.
	 *
	 * @param bit
	 *        The unique modifier bit.
	 */
	private FieldModifier (final int bit)
	{
		this.bit = bit;
	}

	/**
	 * The {@linkplain EnumSet set} of access {@linkplain FieldModifier
	 * modifiers}.
	 */
	private static EnumSet<FieldModifier> accessModifiers = EnumSet.of(
		PUBLIC, PROTECTED, PRIVATE);

	/**
	 * Is the specified {@linkplain EnumSet set} of {@linkplain FieldModifier
	 * modifiers} valid?
	 *
	 * @param modifiers
	 *        The set of modifiers.
	 * @return {@code true} if the field described by the set of modifiers is
	 *         logically coherent, {@code false} otherwise.
	 */
	public static boolean isValid (final EnumSet<FieldModifier> modifiers)
	{
		final EnumSet<FieldModifier> copy = EnumSet.copyOf(modifiers);
		copy.retainAll(accessModifiers);
		if (copy.size() > 1)
		{
			return false;
		}
		if (modifiers.contains(FINAL))
		{
			if (modifiers.contains(VOLATILE))
			{
				return false;
			}
		}
		if (modifiers.contains(ENUM))
		{
			if (copy.size() > 0)
			{
				return false;
			}
			if (modifiers.contains(TRANSIENT))
			{
				return false;
			}
			if (!modifiers.contains(STATIC))
			{
				return false;
			}
			if (!modifiers.contains(FINAL))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer a textual description of the specified {@linkplain EnumSet set} of
	 * {@linkplain FieldModifier modifiers}.
	 *
	 * @param modifiers
	 *        The modifiers.
	 * @return A description of the modifiers.
	 */
	public static String toString (final EnumSet<FieldModifier> modifiers)
	{
		final StringBuilder builder = new StringBuilder(100);
		for (final FieldModifier modifier : modifiers)
		{
			builder.append(modifier);
			builder.append(' ');
		}
		// If any keywords were emitted, then remove the trailing space
		// character.
		if (builder.length() > 0)
		{
			builder.setLength(builder.length() - 1);
		}
		return builder.toString();
	}

	/**
	 * Answer the bitmask that represents the specified {@linkplain EnumSet set}
	 * of {@linkplain FieldModifier modifiers}.
	 *
	 * @param modifiers
	 *        The modifiers.
	 * @return The bitmask.
	 */
	public static int bitmaskFor (final EnumSet<FieldModifier> modifiers)
	{
		int mask = 0;
		for (final FieldModifier modifier : modifiers)
		{
			mask |= modifier.bit;
		}
		return mask;
	}
}
