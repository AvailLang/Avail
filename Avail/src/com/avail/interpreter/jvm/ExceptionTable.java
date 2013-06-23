/**
 * ExceptionTable.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.io.DataOutput;
import java.io.IOException;
import java.util.Deque;
import java.util.Formatter;
import java.util.LinkedList;
import com.avail.annotations.InnerAccess;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;

/**
 * Each {@linkplain GuardedZone entry} in the {@code ExceptionTable} describes
 * one exception handler in the embedding {@link CodeAttribute}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *     href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.3">
 *     The <code>Code</code> Attribute</a>
 */
final class ExceptionTable
{
	/** The header format for textual printing. */
	private static final String headerFormat = "%n\t%-8s %-8s %-8s %s";

	/** The table format for textual printing. */
	private static final String tableFormat = "%n\t%-8d %-8d %-8d %s";

	/**
	 * A {@code GuardedZone} represents a region of a {@linkplain Method
	 * method}'s {@linkplain InstructionWriter body} that is protected against
	 * {@linkplain Throwable throwables}.
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	private final class GuardedZone
	{
		/** The inclusive start {@linkplain Label label}. */
		@InnerAccess final Label startLabel;

		/** The exclusive end {@linkplain Label label}. */
		@InnerAccess final Label endLabel;

		/** The {@linkplain Label label} of the handler subroutine. */
		@InnerAccess final Label handlerLabel;

		/**
		 * The {@linkplain ClassEntry class entry} for the intercepted
		 * {@linkplain Throwable throwable} type.
		 */
		@InnerAccess final ClassEntry catchEntry;

		/**
		 * Construct a new {@link GuardedZone}.
		 *
		 * @param startLabel
		 *        The inclusive start {@linkplain Label label}.
		 * @param endLabel
		 *        The exclusive end label.
		 * @param handlerLabel
		 *        The label of the handler subroutine.
		 * @param catchEntry
		 *        The {@linkplain ClassEntry class entry} for the intercepted
		 *        {@linkplain Throwable throwable} type.
		 */
		public GuardedZone (
			final Label startLabel,
			final Label endLabel,
			final Label handlerLabel,
			final ClassEntry catchEntry)
		{
			this.startLabel = startLabel;
			this.endLabel = endLabel;
			this.handlerLabel = handlerLabel;
			this.catchEntry = catchEntry;
		}

		/**
		 * Write the {@linkplain GuardedZone guarded zone} to the specified
		 * {@linkplain DataOutput output stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		void writeTo (final DataOutput out) throws IOException
		{
			out.writeShort((int) startLabel.address());
			out.writeShort((int) endLabel.address());
			out.writeShort((int) handlerLabel.address());
			if (catchEntry.descriptor().equals("Ljava/lang/Throwable;"))
			{
				out.writeShort(0);
			}
			else
			{
				catchEntry.writeIndexTo(out);
			}
		}
	}

	/** The {@linkplain GuardedZone guarded zones}. */
	private final Deque<GuardedZone> zones = new LinkedList<>();

	/**
	 * Answer the number of {@linkplain GuardedZone guarded zones}.
	 *
	 * @return The number of guarded zones.
	 */
	int size ()
	{
		return zones.size();
	}

	/**
	 * Add a {@linkplain GuardedZone guarded zone} to the {@linkplain
	 * ExceptionTable exception table}.
	 *
	 * @param startLabel
	 *        The inclusive start {@linkplain Label label}.
	 * @param endLabel
	 *        The exclusive end label.
	 * @param handlerLabel
	 *        The label of the handler subroutine.
	 * @param catchEntry
	 *        The {@linkplain ClassEntry class entry} for the intercepted
	 *        {@linkplain Throwable throwable} type.
	 */
	void addGuardedZone (
		final Label startLabel,
		final Label endLabel,
		final Label handlerLabel,
		final ClassEntry catchEntry)
	{
		zones.add(new GuardedZone(
			startLabel, endLabel, handlerLabel, catchEntry));
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		formatter.format("Exception table:");
		formatter.format(headerFormat, "start", "end", "catch", "type");
		for (final GuardedZone zone : zones)
		{
			formatter.format(
				tableFormat,
				zone.startLabel.address(),
				zone.endLabel.address(),
				zone.handlerLabel.address(),
				zone.catchEntry.descriptor());
		}
		return formatter.toString();
	}

	/**
	 * Write the {@linkplain ExceptionTable exception table} to the specified
	 * {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeTo (final DataOutput out) throws IOException
	{
		out.writeShort(zones.size());
		for (final GuardedZone zone : zones)
		{
			zone.writeTo(out);
		}
	}
}
