/**
 * ClassModifier.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
 * {@code ClassModifier} provides constants that describe the permissible
 * access and property modifiers for classes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E.1">
 *     Class access and property modifiers</a>
 *
 */
public enum ClassModifier
implements Modifier
{
	/** Declared {@code public}; may be accessed from outside its package. */
	PUBLIC (0x0001),

	/** Declared {@code final}; no subclasses allowed. */
	FINAL (0x0010),

	/**
	 * Treat superclass methods specially when invoked by the {@link
	 * JavaBytecode#invokespecial} instruction.
	 *
	 * <p>This really means "treat superclass methods <em>normally</em> for
	 * every JVM post 1.0.2". This should always be set for modern classes, and
	 * its absence is no longer honored by Java 7u13 and beyond.</p>
	 *
	 * @see <a
	 *     href="http://stackoverflow.com/questions/8949933/what-is-the-purpose-of-the-acc-super-access-flag-on-java-class-files">
	 *     What is the purpose of the ACC_SUPER access flag on Java Class files?</a>
	 */
	SUPER (0x0020),

	/** Is an interface, not a class. */
	INTERFACE (0x0200),

	/** Declared {@code abstract}; must not be instantiated. */
	ABSTRACT (0x0400),

	/** Declared synthetic; not present in the source code. */
	SYNTHETIC (0x1000),

	/** Declared as an annotation type. */
	ANNOTATION (0x2000),

	/** Declared as an {@code enum} type. */
	ENUM (0x4000);

	/** The unique modifier bit. */
	public final int bit;

	@Override
	public String toString ()
	{
		return super.toString().toLowerCase();
	}

	/**
	 * Construct a new {@link ClassModifier}.
	 *
	 * @param bit
	 *        The unique modifier bit.
	 */
	private ClassModifier (final int bit)
	{
		this.bit = bit;
	}

	/**
	 * Is the specified {@linkplain EnumSet set} of {@linkplain ClassModifier
	 * modifiers} valid?
	 *
	 * @param modifiers
	 *        The set of modifiers.
	 * @return {@code true} if the class described by the set of modifiers is
	 *         logically coherent, {@code false} otherwise.
	 */
	public static boolean isValid (final EnumSet<ClassModifier> modifiers)
	{
		if (modifiers.contains(ENUM))
		{
			if (modifiers.contains(ABSTRACT))
			{
				return false;
			}
			if (modifiers.contains(ANNOTATION))
			{
				return false;
			}
			if (modifiers.contains(FINAL))
			{
				return false;
			}
			if (modifiers.contains(INTERFACE))
			{
				return false;
			}
		}
		if (modifiers.contains(INTERFACE))
		{
			if (modifiers.contains(FINAL))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer a textual description of the specified {@linkplain EnumSet set} of
	 * {@linkplain ClassModifier modifiers}.
	 *
	 * @param modifiers
	 *        The modifiers.
	 * @return A description of the modifiers.
	 */
	public static String toString (final EnumSet<ClassModifier> modifiers)
	{
		final StringBuilder builder = new StringBuilder(100);
		for (final ClassModifier modifier : modifiers)
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
	 * of {@linkplain ClassModifier modifiers}.
	 *
	 * @param modifiers
	 *        The modifiers.
	 * @return The bitmask.
	 */
	public static int bitmaskFor (final EnumSet<ClassModifier> modifiers)
	{
		int mask = 0;
		for (final ClassModifier modifier : modifiers)
		{
			mask |= modifier.bit;
		}
		return mask;
	}
}
