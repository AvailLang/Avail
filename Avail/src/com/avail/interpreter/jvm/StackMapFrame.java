/**
 * StackMapFrame.java
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

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.Tag;

/**
 * A {@link StackMapFrame} is a value, represented by a discriminated union,
 * stored in the {@link StackMapTableAttribute}. It is an explicit indication
 * of the type of a value that may vary as a result of a jump.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 * @see <a
 * 	href="http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.4">
 * 	StackMapTable</a>
 *
 */
public class StackMapFrame
{

	/**
	 * Per the JVM Specification:</br></br>
	 * <i>A verification type specifies the type of either one or two locations,
	 * where a location is either a single local variable or a single operand
	 * stack entry.</i>
	 *
	 * The {@link VerificationTypeInfo} consists of a one-byte value that is an
	 * indication of which type of the discriminated union is being used.
	 *
	 * @see <a
	 * 	href="http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.4">
	 * 	StackMapTable</a>
	 */
	static enum VerificationTypeInfo
	{
		/**
		 * The {@link VerificationTypeInfo <code>Top_variable_info</code>} item
		 * indicates that the local variable has the verification type
		 * <code>top</code>.
		 */
		Top_Variable (0),

		/**
		 * The {@link VerificationTypeInfo <code>Integer_variable_info</code>}
		 * item indicates that the location has the verification type
		 * <code>int</code>.
		 */
		Integer_Variable (1),

		/**
		 * The {@link VerificationTypeInfo <code>Float_variable_info</code>}
		 * item indicates that the location has the verification type
		 * <code>float</code>.
		 */
		Float_Variable (2),

		/**
		 * The {@link VerificationTypeInfo <code>Long_variable_info</code>}
		 * item indicates that the location has the verification type
		 * <code>long</code>.  The <code>Long_variable_info</code> specifies
		 * two locations in the local variable array or in the operand stack.
		 */
		Long_Variable (4),

		/**
		 * The {@link VerificationTypeInfo <code>Double_variable_info</code>}
		 * item indicates that the location has the verification type
		 * <code>double</code>. The <code>Double_variable_info</code> specifies
		 * two locations in the local variable array or in the operand stack.
		 */
		Double_Variable (3),

		/**
		 * The {@link VerificationTypeInfo <code>Null_variable_info</code>}
		 * item indicates that the location has the verification type
		 * <code>null</code>.
		 */
		Null_Variable (5),

		/**
		 * The {@link VerificationTypeInfo
		 * <code>UninitializedThis_variable_info</code>} item indicates that
		 * the location has the verification type
		 * <code>uninitializedThis</code>.
		 */
		UninitializedThis_Variable (6),

		/**
		 * The {@link VerificationTypeInfo <code>Object_variable_info</code>}
		 * item indicates that the location has the verification type
		 * which is the class represented by the {@link ConstantValueAttribute
		 * <code>CONSTANT_Class_info</code>} structure found in the
		 * {@link ConstantPool <code>constant_poo table</code>} at the index
		 * given by <code>cpool_index</code>.
		 */
		Object_Variable (7),

		/**
		 * The {@link VerificationTypeInfo
		 * <code>Uninitialized_variable_info</code>} item indicates that
		 * the location has the verification type
		 * <code>uninitialized(Offset)</code>. The <code>Offset</code> item
		 * indicates the offset, in the <code>code</code> array of the
		 * <code>Code</code> attribute that contains this {@link
		 * StackMapTableAttribute}, of the <code>new</code> instruction that
		 * created the object being stored in the location.
		 */
		Uninitialized_Variable (8);

		/** The value of the verification type. */
		final byte value;

		/**
		 * Construct a new {@link VerificationTypeInfo}.
		 *
		 * @param value
		 *        The value of the verification type.
		 */
		private VerificationTypeInfo (final int value)
		{
			assert (value & 255) == value;
			this.value = (byte) value;
		}

		/**
		 * Write the {@linkplain Tag tag}'s {@linkplain #value} to the specified
		 * {@linkplain DataOutput binary stream}.
		 *
		 * @param out
		 *        A binary output stream.
		 * @throws IOException
		 *         If the operation fails.
		 */
		void writeTo (final DataOutput out) throws IOException
		{
			out.writeByte(value);
		}
	}
	/**
	 * Construct a new {@link StackMapFrame}.
	 *
	 */
	public StackMapFrame ()
	{
		// TODO Auto-generated constructor stub
	}

	/**
	 * The specified range of acceptable values of {@link FrameType}.
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	static enum FrameTypeRange
	{
		@SuppressWarnings("javadoc")
		Same_Frame((byte)0,(byte)63),

		@SuppressWarnings("javadoc")
		Same_Locals_1_Stack_Item_Frame((byte)64,(byte)127),

		@SuppressWarnings("javadoc")
		Same_Locals_1_Stack_Item_Frame_Extended((byte)247,(byte)247),

		@SuppressWarnings("javadoc")
		Chop_Frame((byte)248,(byte)250),

		@SuppressWarnings("javadoc")
		Same_Frame_Extended((byte)251,(byte)251),

		@SuppressWarnings("javadoc")
		Append_Frame((byte)252,(byte)254),

		@SuppressWarnings("javadoc")
		Full_Frame((byte)255,(byte)255);

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
		private FrameTypeRange(final byte bottomRange, final byte topRange)
		{
			this.bottomRange = bottomRange;
			this.topRange = topRange;
		}

		/**
		 * Check to see if a value is in the specified {@link FrameTypeRange}
		 * @param valueToCheck
		 * 		The value to check
		 * @return
		 */
		boolean inRange(final byte valueToCheck)
		{
			return bottomRange <= valueToCheck && valueToCheck <= topRange;
		}
	}

	/**
	 * The {@link FrameType} indicates the structural makeup of the
	 * {@link StackMapFrame}.
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	abstract class FrameType
	{
		/**
		 * The frame value indicates the frame type of the {@link StackMapFrame}
		 */
		final byte frameValue;

		/**
		 * Provide the {@link JavaBytecode bytecode} offset at which the stack
		 * map frame applies.
		 * @return
		 */
		abstract short deltaOffset();

	    /**
	     * Construct a new {@link FrameType}.
	     *
	     * @param frameValue
	     * 		The value for this frame.
	     */
	    FrameType(final byte frameValue)
	    {
	        this.frameValue = frameValue;
	    }
	}

	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame {@link SameFrame type same_frame} is represented by tags in
	 * the range [0-63].  This frame type indicates that the frame has exactly
	 * the same local variables as the previous frame and that the operand
	 * stack is empty.  The offset_delta value for the frame is the value of
	 * the tag item, frame_type.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class SameFrame extends FrameType
	{
		/**
		 * Construct a new {@link SameFrame}.
		 *
		 * @param frameValue
		 */
		SameFrame(final byte frameValue)
		{
			super(frameValue);
		}

		@Override
		short deltaOffset ()
		{
			return frameValue;
		}
	}

	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type
	 * {@link SameLocals1StackItemFrame same_locals_1_stack_item_frame} is
	 * represented by tags in the range [64, 127]. This frame type indicates
	 * that the frame has exactly the same local variables as the previous
	 * frame and that the operand stack has one entry. The offset_delta value
	 * for the frame is given by the formula frame_type - 64. The verification
	 * type of the one stack entry appears after the frame type.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class SameLocals1StackItemFrame extends FrameType
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
		SameLocals1StackItemFrame (final byte frameValue,
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
		 * @return
		 */
		public List<VerificationTypeInfo> stack()
		{
			return stack;
		}

	}
	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type {@link SameLocals1StackItemFrameExtended
	 * same_locals_1_stack_item_frame_extended} is represented by
	 * the tag 247. This frame type indicates that the frame has exactly the
	 * same local variables as the previous frame and that the operand stack
	 * has one entry. The offset_delta value for the frame is given explicitly,
	 * unlike in the frame type same_locals_1_stack_item_frame. The
	 * verification type of the one stack entry appears after offset_delta.
	 * </i>
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class SameLocals1StackItemFrameExtended extends FrameType
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
		 * Get the {@link List} of {@link VerificationTypeInfo stack items}
		 * @return
		 */
		public List<VerificationTypeInfo> stack()
		{
			return stack;
		}

	}
	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type {@link ChopFrame chop_frame} is represented by tags in
	 * the range [248-250].  This frame type indicates that the frame has the
	 * same local variables as the previous frame except that the last k local
	 * variables are absent, and that the operand stack is empty. The value
	 * of k is given by the formula 251 - frame_type. The offset_delta value
	 * for the frame is given explicitly.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class ChopFrame extends FrameType
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

	}

	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type {@link SameFrameExtended same_frame_extended} is
	 * represented by the tag 251. This frame type indicates that the frame has
	 * exactly the same local variables as the previous frame and that the
	 * operand stack is empty. The offset_delta value for the frame is given
	 * explicitly, unlike in the frame type same_frame.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class SameFrameExtended extends FrameType
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
		SameFrameExtended (
			final short explicitDeltaOffset)
		{
			super((byte)251);
			this.explicitDeltaOffset = explicitDeltaOffset;
		}

		@Override
		short deltaOffset ()
		{
			return explicitDeltaOffset;
		}

	}

	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type {@link AppendFrame append_frame} is represented by tags
	 * in the range [252-254]. This frame type indicates that the frame has the
	 * same locals as the previous frame except that k additional locals are
	 * defined, and that the operand stack is empty. The value of k is given
	 * by the formula frame_type - 251. The offset_delta value for the frame
	 * is given explicitly.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class AppendFrame extends FrameType
	{
		/**
		 * The {@link List} of local {@link VerificationTypeInfo verification
		 * types} of the local variables of this {@link StackMapFrame}.
		 * Should only have size frameValue - 251.</br></br>
		 * <b>Per the JVM Specification:</b></br>
		 *
		 * The 0th entry in <code>locals</code> represents the verification type
		 * of the first additional local variable. If <code>locals[M]</code>
		 * represents local variable <code>N</code>, then: </br></br>
		 *
		 * <ul><li><code>locals[M+1]</code> represents local variable N+1 if
		 * <code>locals[M]</code> is one of
		 * {@link VerificationTypeInfo#Top_Variable <code>Top_variable_info},
		 * {@link VerificationTypeInfo#Integer_Variable Integer_variable_info},
		 * {@link VerificationTypeInfo#Float_Variable Float_variable_info},
		 * {@link VerificationTypeInfo#Null_Variable Null_variable_info},
		 * {@link VerificationTypeInfo#UninitializedThis_Variable
		 * UninitializedThis_variable_info}, {@link
		 * VerificationTypeInfo#Object_Variable Object_variable_info},</code> or
		 * {@link VerificationTypeInfo#Uninitialized_Variable
		 * <code>Uninitialized_variable_info</code>}</li></br>
		 * <li><code>locals[M+1]</code> represents local variable
		 * <code>N+2</code> if <code>locals[M]</code> is either
		 * {@link VerificationTypeInfo#Long_Variable
		 * <code>Long_variable_info</code>} or {@link
		 * VerificationTypeInfo#Double_Variable
		 * <code>Double_variable_info</code>}.</li></ul>
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
		AppendFrame (final byte frameValue,
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
		 * Get the {@link List} of {@link VerificationTypeInfo local variables}
		 * @return
		 */
		public List<VerificationTypeInfo> locals()
		{
			return locals;
		}

	}

	/**
	 * Per the JVM specification:</br>
	 * <i>
	 * The frame type {@link FullFrame full_frame} is represented by the tag
	 * 255. The offset_delta value for the frame is given explicitly.
	 * </i>
	 *
	 * @author Rich Arriaga &lt;rich@availlang.org&gt;
	 */
	class FullFrame extends FrameType
	{
		/**
		 * The {@link List} of local {@link VerificationTypeInfo verification
		 * types} of the local variables of this {@link StackMapFrame}.</br></br>
		 * <b>Per the JVM Specification:</b></br>
		 *
		 * The 0th entry in <code>locals</code> represents the verification type
		 * of the first additional local variable. If <code>locals[M]</code>
		 * represents local variable <code>N</code>, then: </br></br>
		 *
		 * <ul><li><code>locals[M+1]</code> represents local variable N+1 if
		 * <code>locals[M]</code> is one of
		 * {@link VerificationTypeInfo#Top_Variable <code>Top_variable_info},
		 * {@link VerificationTypeInfo#Integer_Variable Integer_variable_info},
		 * {@link VerificationTypeInfo#Float_Variable Float_variable_info},
		 * {@link VerificationTypeInfo#Null_Variable Null_variable_info},
		 * {@link VerificationTypeInfo#UninitializedThis_Variable
		 * UninitializedThis_variable_info}, {@link
		 * VerificationTypeInfo#Object_Variable Object_variable_info},</code> or
		 * {@link VerificationTypeInfo#Uninitialized_Variable
		 * <code>Uninitialized_variable_info</code>}</li></br>
		 * <li><code>locals[M+1]</code> represents local variable
		 * <code>N+2</code> if <code>locals[M]</code> is either
		 * {@link VerificationTypeInfo#Long_Variable
		 * <code>Long_variable_info</code>} or {@link
		 * VerificationTypeInfo#Double_Variable
		 * <code>Double_variable_info</code>}.</li></ul>
		 */
		private final List<VerificationTypeInfo> locals;

		/**
		 * The {@link List} of {@link VerificationTypeInfo stack operands} of
		 * this {@link StackMapFrame}.</br></br>
		 * <b>Per the JVM Specification:</b></br>
		 *
		 * The 0th entry in <code>stack</code> represents the verification type
		 * of the bottom of the operand stack, and subsequent entries in
		 * <code>stack</code> represent the verification types of stack entries
		 * closer to the top of the operand stack. We refer to the bottom of
		 * the operand stack as stack entry 0, and to subsequent entries of the
		 * operand stack as stack entry 1, 2, etc. If <code>stack[M]</code>
		 * represents stack entry <code>N</code>, then: </br></br>
		 *
		 * <ul><li><code>stack[M+1]</code> represents local variable N+1 if
		 * <code>stack[M]</code> is one of
		 * {@link VerificationTypeInfo#Top_Variable <code>Top_variable_info},
		 * {@link VerificationTypeInfo#Integer_Variable Integer_variable_info},
		 * {@link VerificationTypeInfo#Float_Variable Float_variable_info},
		 * {@link VerificationTypeInfo#Null_Variable Null_variable_info},
		 * {@link VerificationTypeInfo#UninitializedThis_Variable
		 * UninitializedThis_variable_info}, {@link
		 * VerificationTypeInfo#Object_Variable Object_variable_info},</code> or
		 * {@link VerificationTypeInfo#Uninitialized_Variable
		 * <code>Uninitialized_variable_info</code>}</li></br>
		 * <li><code>stack[M+1]</code> represents local variable
		 * <code>N+2</code> if <code>stack[M]</code> is either
		 * {@link VerificationTypeInfo#Long_Variable
		 * <code>Long_variable_info</code>} or {@link
		 * VerificationTypeInfo#Double_Variable
		 * <code>Double_variable_info</code>}.</li></ul>
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
		 * @param stack
		 * @param locals
		 * @param explicitDeltaOffset
		 *
		 */
		FullFrame (final List<VerificationTypeInfo> stack,
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
		 * Get the {@link List} of {@link VerificationTypeInfo stack items}
		 * @return
		 */
		public List<VerificationTypeInfo> stack()
		{
			return stack;
		}

		/**
		 * Get the {@link List} of {@link VerificationTypeInfo local variables}
		 * @return
		 */
		public List<VerificationTypeInfo> locals()
		{
			return locals;
		}

		/**
		 * @return the numberOfLocals
		 */
		public short numberOfLocals ()
		{
			return numberOfLocals;
		}

		/**
		 * @return the numberOfStackItems
		 */
		public short numberOfStackItems ()
		{
			return numberOfStackItems;
		}

	}
}
