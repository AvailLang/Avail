/**
 * StackMapFrame.java
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

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/**
 * A {@link StackMapFrame} is a value, represented by a discriminated union,
 * stored in the {@link StackMapTableAttribute}. It is an explicit indication
 * of the type of a value that may vary as a result of a jump.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 * @see <a
 *       href="http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.4">
 *       StackMapTable</a>
 */
class StackMapFrame
{
	/**
	 * The specified range of acceptable values of {@link FrameType}.
	 */
	static enum FrameTypeRange
	{
		@SuppressWarnings("javadoc")
		Same_Frame(0,63)
		{
			@Override
			SameFrame createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
			{
				return new SameFrame(frameTypeValue);
			}
		},

		@SuppressWarnings("javadoc")
		Same_Locals_1_Stack_Item_Frame(64,127)
		{
			@Override
			SameLocals1StackItemFrame createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new SameLocals1StackItemFrame(frameTypeValue, stack);
				}
		},

		@SuppressWarnings("javadoc")
		Same_Locals_1_Stack_Item_Frame_Extended(247,247)
		{
			@Override
			SameLocals1StackItemFrameExtended createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new SameLocals1StackItemFrameExtended(
						frameTypeValue, stack, explicitDeltaOffset);
				}
		},

		@SuppressWarnings("javadoc")
		Chop_Frame(248,250)
		{
			@Override
			ChopFrame createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new ChopFrame(frameTypeValue,explicitDeltaOffset);
				}
		},

		@SuppressWarnings("javadoc")
		Same_Frame_Extended(251,251)
		{
			@Override
			SameFrameExtended createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new SameFrameExtended(explicitDeltaOffset);
				}
		},

		@SuppressWarnings("javadoc")
		Append_Frame(252,254)
		{
			@Override
			AppendFrame createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new AppendFrame(
						frameTypeValue, locals, explicitDeltaOffset);
				}
		},

		@SuppressWarnings("javadoc")
		Full_Frame(255,255)
		{
			@Override
			FullFrame createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
				{
					return new FullFrame(
						stack, locals, explicitDeltaOffset);
				}
		},

		@SuppressWarnings("javadoc")
		Not_Used(128,246)
		{
			@Override
			FrameType createFrameType (
				final byte frameTypeValue,
				final List<VerificationTypeInfo> stack,
				final List<VerificationTypeInfo> locals,
				final short explicitDeltaOffset)
					throws JVMCodeGenerationException
				{
					throw new JVMCodeGenerationException(String.format(
						"Attempted to create a StackMapFrame with frame type"
						+ " value, %d, however that lies in the reserved"
						+ " range, %d - %d",
						frameTypeValue,
						bottomRange,
						topRange));
				}
		};

		/** The lowest value inclusive **/
		final byte bottomRange;

		/** The greatest value inclusive **/
		final byte topRange;

		/**
		 * Construct a new {@link FrameTypeRange}.
		 *
		 * @param bottomRange
		 *        The lowest value inclusive
		 * @param topRange
		 *        The greatest value inclusive
		 */
		private FrameTypeRange (final int bottomRange, final int topRange)
		{
			final byte tempBottom = (byte)bottomRange;
			assert (tempBottom == (bottomRange & 255));
			this.bottomRange = (byte)bottomRange;

			final byte tempTop = (byte)topRange;
			assert (tempTop == (topRange & 255));
			this.topRange = (byte)topRange;
		}

		/**
		 * Construct a new {@link FrameType}
		 *
		 * @param frameTypeValue
		 *        An unsigned byte value in the range [0,255].  This determines
		 *        the {@link FrameType}.
		 * @param stack
		 *        {@link List} of {@link VerificationTypeInfo stack operand
		 *        types} representing this {@link StackMapFrame}'s operands.
		 * @param locals
		 *        {@link List} of {@link VerificationTypeInfo local variables}
		 *        of this {@link StackMapFrame}'s local variables.
		 * @param explicitDeltaOffset
		 *        The bytecode offset at which the {@link StackMapFrame}
		 *        applies.
		 * @return
		 *        A new {@link FrameType}.
		 * @throws JVMCodeGenerationException
		 */
		abstract FrameType createFrameType (
			byte frameTypeValue,
			final List<VerificationTypeInfo> stack,
			final List<VerificationTypeInfo> locals,
			final short explicitDeltaOffset) throws JVMCodeGenerationException;

		/**
		 * Check to see if a value is in the specified {@link FrameTypeRange}.
		 *
		 * @param valueToCheck
		 *        The value to check.
		 * @return {@code true} if {@code valueToCheck} is in range,
		 *        {@code false} otherwise.
		 */
		boolean inRange (final byte valueToCheck)
		{
			return bottomRange <= valueToCheck && valueToCheck <= topRange;
		}
	}

	/**
	 * A table whose indices are the potential frame type values.
	 */
	static FrameTypeRange[] frameTypeDispatchTable = new FrameTypeRange[256];

	/**
	 * Statically initialize the {@link frameTypeDispatchTable} with suitable
	 * the {@link FrameTypeRange}. Note that this happens as part of class
	 * loading.
	 */
	static
	{
		for (byte i = 0; i < 256; i++)
		{
			if (FrameTypeRange.Same_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Same_Frame;
			}
			else if (FrameTypeRange.Same_Locals_1_Stack_Item_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] =
					FrameTypeRange.Same_Locals_1_Stack_Item_Frame;
			}
			else if (FrameTypeRange
				.Same_Locals_1_Stack_Item_Frame_Extended.inRange(i))
			{
				frameTypeDispatchTable[i] =
					FrameTypeRange.Same_Locals_1_Stack_Item_Frame_Extended;
			}
			else if (FrameTypeRange.Chop_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Chop_Frame;
			}
			else if (FrameTypeRange.Chop_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Chop_Frame;
			}
			else if (FrameTypeRange.Append_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Append_Frame;
			}
			else if (FrameTypeRange.Full_Frame.inRange(i))
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Full_Frame;
			}
			else
			{
				frameTypeDispatchTable[i] = FrameTypeRange.Not_Used;
			}
		}

	}

	/**
	 * The {@link FrameType} indicates the structural makeup of the
	 * {@link StackMapFrame}.
	 */
	abstract static class FrameType
	{
		/**
		 * The frame value indicates the frame type of the {@link StackMapFrame}
		 */
		final byte frameValue;

		/**
		 * Provide the {@link JavaBytecode bytecode} offset at which the stack
		 * map frame applies.
		 *
		 * @return
		 */
		abstract short deltaOffset();

		/**
		 * Answer the size of the {@linkplain FrameType}.
		 *
		 * @return The size of the frame.
		 */
		protected abstract int size ();

		/**
		 * Write the {@linkplain FrameType}'s contents to the specified
		 * {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @param constantPool
		 *        The {@linkplain ConstantPool constant pool}.
		 * @throws IOException
		 *         If the operation fails.
		 */
		abstract void writeTo (
				DataOutput out,
				ConstantPool constantPool)
			throws IOException;

		/**
		 * Construct a new {@link FrameType}.
		 *
		 * @param frameValue
		 *        The value for this frame.
		 */
		FrameType(final byte frameValue)
		{
			this.frameValue = frameValue;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame {@link SameFrame type same_frame} is represented by tags in
	 * the range [0-63].  This frame type indicates that the frame has exactly
	 * the same local variables as the previous frame and that the operand
	 * stack is empty.  The offset_delta value for the frame is the value of
	 * the tag item, frame_type.
	 * </blockquote>
	 */
	static class SameFrame
	extends FrameType
	{
		/**
		 * Construct a new {@link SameFrame}.
		 *
		 * @param frameValue
		 */
		SameFrame (final byte frameValue)
		{
			super(frameValue);
		}

		@Override
		short deltaOffset ()
		{
			return frameValue;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
		}

		@Override
		protected int size ()
		{
			return 1;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type
	 * {@link SameLocals1StackItemFrame same_locals_1_stack_item_frame} is
	 * represented by tags in the range [64, 127]. This frame type indicates
	 * that the frame has exactly the same local variables as the previous
	 * frame and that the operand stack has one entry. The offset_delta value
	 * for the frame is given by the formula frame_type - 64. The verification
	 * type of the one stack entry appears after the frame type.
	 * </blockquote>
	 */
	static class SameLocals1StackItemFrame
	extends FrameType
	{
		/**
		 * The {@link List} of {@link VerificationTypeInfo stack operands} of
		 * this {@link StackMapFrame}.  Should only have one element.
		 */
		private final List<VerificationTypeInfo> stack;

		/**
		 * Construct a new {@link SameLocals1StackItemFrame}.
		 *
		 * @param frameValue
		 * @param stack
		 */
		SameLocals1StackItemFrame (
			final byte frameValue,
			final List<VerificationTypeInfo> stack)
		{
			super(frameValue);
			this.stack = stack;
		}

		@Override
		short deltaOffset ()
		{
			return (short)(frameValue - 64);
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo stack items}
		 *
		 * @return The {@link List} of {@link VerificationTypeInfo stack items}
		 */
		public List<VerificationTypeInfo> stack ()
		{
			return stack;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			for (int i = 0; i < stack.size(); i++)
			{
				stack.get(i).writeTo(out, constantPool);
			}
		}

		@Override
		protected int size ()
		{
			int mySize = 1;
			for (int i = 0; i < stack.size(); i++)
			{
				mySize = mySize + stack.get(i).size();
			}
			return mySize;
		}

	}
	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type {@link SameLocals1StackItemFrameExtended
	 * same_locals_1_stack_item_frame_extended} is represented by
	 * the tag 247. This frame type indicates that the frame has exactly the
	 * same local variables as the previous frame and that the operand stack
	 * has one entry. The offset_delta value for the frame is given explicitly,
	 * unlike in the frame type same_locals_1_stack_item_frame. The
	 * verification type of the one stack entry appears after offset_delta.
	 * </blockquote>
	 */
	static class SameLocals1StackItemFrameExtended
	extends FrameType
	{
		/**
		 * The {@link List} of {@link VerificationTypeInfo stack operands} of
		 * this {@link StackMapFrame}.  Should only have one element.
		 */
		private final List<VerificationTypeInfo> stack;

		/**
		 * The bytecode offset at which the {@link StackMapFrame} applies.
		 */
		private final short explicitDeltaOffset;

		/**
		 * Construct a new {@link SameLocals1StackItemFrameExtended}.
		 *
		 * @param frameValue
		 * @param stack
		 * @param explicitDeltaOffset
		 */
		SameLocals1StackItemFrameExtended (final byte frameValue,
			final List<VerificationTypeInfo> stack,
			final short explicitDeltaOffset)
		{
			super(frameValue);
			this.stack = stack;
			this.explicitDeltaOffset = explicitDeltaOffset;
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo stack items}.
		 *
		 * @return The {@link List} of {@link VerificationTypeInfo stack items}.
		 */
		public List<VerificationTypeInfo> stack()
		{
			return stack;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			out.writeByte(explicitDeltaOffset);
			for (int i = 0; i < stack.size(); i++)
			{
				stack.get(i).writeTo(out, constantPool);
			}
		}

		@Override
		protected int size ()
		{
			int mySize = 3;
			for (int i = 0; i < stack.size(); i++)
			{
				mySize = mySize + stack.get(i).size();
			}
			return mySize;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type {@link ChopFrame chop_frame} is represented by tags in
	 * the range [248-250].  This frame type indicates that the frame has the
	 * same local variables as the previous frame except that the last k local
	 * variables are absent, and that the operand stack is empty. The value
	 * of k is given by the formula 251 - frame_type. The offset_delta value
	 * for the frame is given explicitly.
	 * </blockquote>
	 */
	static class ChopFrame
	extends FrameType
	{
		/**
		 * The bytecode offset at which the {@link StackMapFrame} applies.
		 */
		private final short explicitDeltaOffset;

		/**
		 * Construct a new {@link ChopFrame}.
		 *
		 * @param frameValue
		 * @param explicitDeltaOffset
		 */
		ChopFrame (final byte frameValue, final short explicitDeltaOffset)
		{
			super(frameValue);
			this.explicitDeltaOffset = explicitDeltaOffset;
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			out.writeByte(explicitDeltaOffset);
		}

		@Override
		protected int size ()
		{
			return 3;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type {@link SameFrameExtended same_frame_extended} is
	 * represented by the tag 251. This frame type indicates that the frame has
	 * exactly the same local variables as the previous frame and that the
	 * operand stack is empty. The offset_delta value for the frame is given
	 * explicitly, unlike in the frame type same_frame.
	 * </blockquote>
	 */
	static class SameFrameExtended extends FrameType
	{
		/**
		 * The bytecode offset at which the {@link StackMapFrame} applies.
		 */
		private final short explicitDeltaOffset;

		/**
		 * Construct a new {@link SameFrameExtended}.
		 *
		 * @param explicitDeltaOffset
		 */
		SameFrameExtended (final short explicitDeltaOffset)
		{
			super((byte)251);
			this.explicitDeltaOffset = explicitDeltaOffset;
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			out.writeByte(explicitDeltaOffset);
		}

		@Override
		protected int size ()
		{
			return 3;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type {@link AppendFrame append_frame} is represented by tags
	 * in the range [252-254]. This frame type indicates that the frame has the
	 * same locals as the previous frame except that k additional locals are
	 * defined, and that the operand stack is empty. The value of k is given
	 * by the formula frame_type - 251. The offset_delta value for the frame
	 * is given explicitly.
	 * </blockquote>
	 */
	static class AppendFrame
	extends FrameType
	{
		/**
		 * <p>The {@link List} of local {@link VerificationTypeInfo verification
		 * types} of the local variables of this {@link StackMapFrame}.
		 * Should only have size frameValue - 251.</p>
		 *
		 * <p><b>Per the JVM Specification:</b></p>
		 *
		 * <blockquote><p>The 0th entry in {@code locals} represents the
		 * verification type of the first additional local variable. If
		 * {@code locals[M]} represents local variable {@code N}, then: </p>
		 *
		 * <ul>
		 * <li>{@code locals[M+1]} represents local variable N+1 if {@code
		 * locals[M]} is one of:
		 * <ul>
		 * <li>{@link TopTypeInfo Top_variable_info}</li>
		 * <li>{@link IntegerTypeInfo Integer_variable_info}</li>
		 * <li>{@link FloatTypeInfo Float_variable_info}</li>
		 * <li>{@link NullTypeInfo Null_variable_info}</li>
		 * <li>{@link UninitializedThisTypeInfo
		 * UninitializedThis_variable_info}</li>
		 * <li>{@link ObjectTypeInfo Object_variable_info}</li>
		 * <li>{@link UninitializedTypeInfo Uninitialized_ßvariable_info}</li>
		 * </ul>
		 * <li>{@code locals[M+1]} represents local variable
		 * {@code N+2} if {@code locals[M]} is either
		 * {@link LongTypeInfo Long_variable_info} or
		 * {@link DoubleTypeInfo Double_variable_info}.</li>
		 * </ul></blockquote>
		 */
		private final List<VerificationTypeInfo> locals;

		/**
		 * The bytecode offset at which the {@link StackMapFrame} applies.
		 */
		private final short explicitDeltaOffset;

		/**
		 * Construct a new {@link AppendFrame}.
		 *
		 * @param frameValue
		 * @param locals
		 * @param explicitDeltaOffset
		 */
		AppendFrame (
			final byte frameValue,
			final List<VerificationTypeInfo> locals,
			final short explicitDeltaOffset)
		{
			super(frameValue);
			this.locals = locals;
			this.explicitDeltaOffset = explicitDeltaOffset;
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo local variables}.
		 *
		 * @return The {@link List} of
		 *    {@link VerificationTypeInfo local variables}.
		 */
		public List<VerificationTypeInfo> locals()
		{
			return locals;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			out.writeByte(explicitDeltaOffset);
			for (int i = 0; i < locals.size(); i++)
			{
				locals.get(i).writeTo(out, constantPool);
			}
		}

		@Override
		protected int size ()
		{
			int mySize = 3;
			for (int i = 0; i < locals.size(); i++)
			{
				mySize = mySize + locals.get(i).size();
			}
			return mySize;
		}
	}

	/**
	 * Per the JVM specification:
	 *
	 * <blockquote>
	 * The frame type {@link FullFrame full_frame} is represented by the tag
	 * 255. The offset_delta value for the frame is given explicitly.
	 * </blockquote>
	 */
	static class FullFrame
	extends FrameType
	{
		/**
		 * <p>The {@link List} of local {@link VerificationTypeInfo verification
		 * types} of the local variables of this {@link StackMapFrame}.</p>
		 * <p><b>Per the JVM Specification:</b></p>
		 *
		 * <blockquote><p>The 0th entry in {@code locals} represents the
		 * verification type of the first additional local variable. If
		 * {@code locals[M]} represents local variable {@code N}, then: </p>
		 *
		 * <ul>
		 * <li>{@code locals[M+1]} represents local variable N+1 if {@code
		 * locals[M]} is one of:
		 * <ul>
		 * <li>{@link TopTypeInfo Top_variable_info}</li>
		 * <li>{@link IntegerTypeInfo Integer_variable_info}</li>
		 * <li>{@link FloatTypeInfo Float_variable_info}</li>
		 * <li>{@link NullTypeInfo Null_variable_info}</li>
		 * <li>{@link UninitializedThisTypeInfo
		 * UninitializedThis_variable_info}</li>
		 * <li>{@link ObjectTypeInfo Object_variable_info}</li>
		 * <li>{@link UninitializedTypeInfo Uninitialized_ßvariable_info}</li>
		 * </ul>
		 * <li>{@code locals[M+1]} represents local variable
		 * {@code N+2} if {@code locals[M]} is either
		 * {@link LongTypeInfo Long_variable_info} or
		 * {@link DoubleTypeInfo Double_variable_info}.</li>
		 * </ul></blockquote>
		 */
		private final List<VerificationTypeInfo> locals;

		/**
		 * <p>The {@link List} of {@link VerificationTypeInfo stack operand
		 * types} of this {@link StackMapFrame}.</p>
		 * <p><b>Per the JVM Specification:</b></p>
		 *
		 * <blockquote><p>The 0th entry in {@code stack} represents the verification type
		 * of the bottom of the operand stack, and subsequent entries in
		 * {@code stack} represent the verification types of stack entries
		 * closer to the top of the operand stack. We refer to the bottom of
		 * the operand stack as stack entry 0, and to subsequent entries of the
		 * operand stack as stack entry 1, 2, etc. If {@code stack[M]}
		 * represents stack entry {@code N}, then:</p>
		 *
		 * <ul>
		 * <li>{@code stack[M+1]} represents local variable N+1 if
		 * {@code stack[M]} is one of
		 * <ul>
		 * <li>{@link TopTypeInfo Top_variable_info}</li>
		 * <li>{@link IntegerTypeInfo Integer_variable_info}</li>
		 * <li>{@link FloatTypeInfo Float_variable_info}</li>
		 * <li>{@link NullTypeInfo Null_variable_info}</li>
		 * <li>{@link UninitializedThisTypeInfo UninitializedThis_variable_info}</li>
		 * <li>{@link ObjectTypeInfo Object_variable_info}</li>
		 * <li>{@link UninitializedTypeInfo Uninitialized_variable_info}</li>
		 * </ul>
		 * <li>{@code stack[M+1]} represents local variable
		 * {@code N+2} if {@code stack[M]} is either
		 * {@link LongTypeInfo Long_variable_info} or
		 * {@link DoubleTypeInfo Double_variable_info}.</li>
		 * </ul></blockquote>
		 */
		private final List<VerificationTypeInfo> stack;

		/**
		 * The bytecode offset at which the {@link StackMapFrame} applies.
		 */
		private final short explicitDeltaOffset;

		/**
		 * The number of local variables represented in locals
		 */
		private final short numberOfLocals;

		/**
		 * The number of stack items represented in stack.
		 */
		private final short numberOfStackItems;

		/**
		 * Construct a new {@link FullFrame}.
		 *
		 * @param stack
		 * @param locals
		 * @param explicitDeltaOffset
		 *
		 */
		FullFrame (
			final List<VerificationTypeInfo> stack,
			final List<VerificationTypeInfo> locals,
			final short explicitDeltaOffset)
		{
			super((byte)255);
			this.locals = locals;
			this.explicitDeltaOffset = explicitDeltaOffset;
			this.stack = stack;
			this.numberOfLocals = (short)locals.size();
			this.numberOfStackItems = (short)stack.size();
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo stack items}.
		 *
		 * @return The {@link List} of {@link VerificationTypeInfo stack items}.
		 */
		public List<VerificationTypeInfo> stack()
		{
			return stack;
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo local variables}.
		 *
		 * @return The {@link List} of
		 *       {@link VerificationTypeInfo local variables}.
		 */
		public List<VerificationTypeInfo> locals()
		{
			return locals;
		}

		@Override
		void writeTo (
				final DataOutput out,
				final ConstantPool constantPool)
			throws IOException
		{
			out.writeByte(frameValue);
			out.writeByte(explicitDeltaOffset);
			out.writeByte(numberOfLocals);
			for (int i = 0; i < locals.size(); i++)
			{
				locals.get(i).writeTo(out, constantPool);
			}
			out.writeByte(numberOfStackItems);
			for (int i = 0; i < stack.size(); i++)
			{
				stack.get(i).writeTo(out, constantPool);
			}
		}

		@Override
		protected int size ()
		{
			int mySize = 3;

			for (int i = 0; i < stack.size(); i++)
			{
				mySize = mySize + stack.get(i).size();
			}

			for (int i = 0; i < locals.size(); i++)
			{
				mySize = mySize + locals.get(i).size();
			}
			return mySize;
		}
	}

	/**
	 * The body of the {@link StackMapFrame}.
	 */
	private FrameType frame;

	/**
	 * Answer the size of the {@linkplain StackMapFrame}.
	 *
	 * @return The size of the {@link StackMapFrame}.
	 */
	public int size ()
	{
		return frame.size();
	}

	/**
	 * Write the {@linkplain FrameType}'s contents to the specified
	 * {@linkplain DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @param constantPool
	 *        The {@linkplain ConstantPool constant pool}.
	 * @throws IOException
	 *         If the operation fails.
	 */
	void writeTo (
			final DataOutput out,
			final ConstantPool constantPool)
		throws IOException
	{
		frame.writeTo(out, constantPool);
	}

	/**
	 * Construct a new {@link StackMapFrame}.
	 *
	 * @param frameTypeValue
	 *        The value of the {@link FrameTypeRange}
	 * @param stack
	 *        The {@link List} of {@link VerificationTypeInfo} representing the
	 *        the operand stack.
	 * @param locals
	 *        The {@link List} of {@link VerificationTypeInfo} representing the
	 *        the local variables.
	 * @param explicitDeltaOffset
	 *        The {@link JavaBytecode bytecode} offset at which the stack
	 *        map frame applies.
	 * @throws JVMCodeGenerationException
	 *
	 */
	public StackMapFrame (
			final byte frameTypeValue,
			final List<VerificationTypeInfo> stack,
			final List<VerificationTypeInfo> locals,
			final short explicitDeltaOffset)
		throws JVMCodeGenerationException
	{
		frameTypeDispatchTable[frameTypeValue].createFrameType(
			frameTypeValue, stack, locals, explicitDeltaOffset);
	}
}
