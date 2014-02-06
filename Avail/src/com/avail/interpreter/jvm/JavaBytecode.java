/**
 * JavaBytecode.java
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

import static com.avail.interpreter.jvm.JavaOperand.*;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.invoke.WrongMethodTypeException;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;
import com.avail.interpreter.jvm.ConstantPool.ConstantEntry;
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;
import com.avail.interpreter.jvm.ConstantPool.InterfaceMethodrefEntry;
import com.avail.interpreter.jvm.ConstantPool.MethodrefEntry;

/**
 * The members of {@code JavaBytecode} represent the bytecodes of the Java
 * virtual machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see <a
 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html">
 *    The Java Virtual Machine Instruction Set</a>
 */
enum JavaBytecode
{
	/**
	 * Load {@code reference} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aaload">
	 *    aaload</a>
	 */
	aaload (0x32,
		O(ARRAYREF, INDEX),
		O(OBJECTREF),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code reference} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aastore">
	 *    aastore</a>
	 */
	aastore (0x53,
		O(ARRAYREF, INDEX, OBJECTREF),
		O(OBJECTREF),
		X(NullPointerException.class,
			ArrayIndexOutOfBoundsException.class,
			ArrayStoreException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},


	/**
	 * Push {@code null}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aconst_null">
	 *    aconst_null</a>
	 */
	aconst_null (0x1,
		O(),
		O(NULL)),

	/**
	 * Load {@code reference} from local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aload">
	 *    aload</a>
	 */
	aload (0x19, 1,
		O(),
		O(OBJECTREF))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code reference} from local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aload_n">
	 *    aload_0</a>
	 */
	aload_0 (0x2a,
		O(),
		O(OBJECTREF))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code reference} from local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aload_n">
	 *    aload_1</a>
	 */
	aload_1 (0x2b,
		O(),
		O(OBJECTREF))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code reference} from local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aload_n">
	 *    aload_2</a>
	 */
	aload_2 (0x2c,
		O(),
		O(OBJECTREF))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code reference} from local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.aload_n">
	 *    aload_3</a>
	 */
	aload_3 (0x2d,
		O(),
		O(OBJECTREF))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Create new array of {@code reference}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.anewarray">
	 *    anewarray</a>
	 */
	anewarray (0xbd, 2,
		O(COUNT),
		O(ARRAYREF),
		X(NegativeArraySizeException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use NewObjectArrayInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Return {@code reference} from method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.areturn">
	 *    areturn</a>
	 */
	areturn (0xb0,
		O(OBJECTREF),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}
	},

	/**
	 * Get length of array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.arraylength">
	 *    arraylength</a>
	 */
	arraylength (0xbe,
		O(ARRAYREF),
		O(LENGTH),
		X(NullPointerException.class)),

	/**
	 * Get length of array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.astore">
	 *    astore</a>
	 */
	astore (0x3a, 1,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Store {@code reference} into local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.astore_n">
	 *    astore_0</a>
	 */
	astore_0 (0x4b,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code reference} into local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.astore_n">
	 *    astore_1</a>
	 */
	astore_1 (0x4c,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code reference} into local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.astore_n">
	 *    astore_2</a>
	 */
	astore_2 (0x4d,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code reference} into local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.astore_n">
	 *    astore_3</a>
	 */
	astore_3 (0x4e,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Throw exception or error
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.athrow">
	 *    athrow</a>
	 */
	athrow (0xbf,
		O(OBJECTREF),
		O(OBJECTREF),
		X(NullPointerException.class, IllegalMonitorStateException.class)),

	/**
	 * Load {@code byte} or {@code boolean} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.baload">
	 *    baload</a>
	 */
	baload (0x33,
		O(ARRAYREF, INDEX),
		O(INT),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code byte} or {@code boolean} array.
	 */
	bastore (0x54,
		O(ARRAYREF, INDEX, INT),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Push {@code byte}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.bipush">
	 *    bipush</a>
	 */
	bipush (0x10, 1,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new PushByteInstruction((int) operands[0]);
		}
	},

	/**
	 * Load {@code char} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.caload">
	 *    caload</a>
	 */
	caload (0x34,
		O(ARRAYREF, INDEX),
		O(INT),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code char} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.castore">
	 *    castore</a>
	 */
	castore (0x55,
		O(ARRAYREF, INDEX, INT),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Check whether object is of given type.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.checkcast">
	 *    checkcast</a>
	 */
	checkcast (0xc0, 2,
		O(OBJECTREF),
		O(OBJECTREF),
		X(ClassCastException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new CheckCastInstruction((ClassEntry) operands[0]);
		}
	},

	/**
	 * Convert {@code double} to {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.d2f">
	 *    d2f</a>
	 */
	d2f (0x90,
		O(DOUBLE),
		O(FLOAT)),

	/**
	 * Convert {@code double} to {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.d2i">
	 *    d2i</a>
	 */
	d2i (0x8e,
		O(DOUBLE),
		O(INT)),

	/**
	 * Convert {@code double} to {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.d2l">
	 *    d2l</a>
	 */
	d2l (0x8f,
		O(DOUBLE),
		O(LONG)),

	/**
	 * Add {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dadd">
	 *    dadd</a>
	 */
	dadd (0x63,
		O(DOUBLE, DOUBLE),
		O(DOUBLE)),

	/**
	 * Load {@code double} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.daload">
	 *    daload</a>
	 */
	daload (0x31,
		O(ARRAYREF, INDEX),
		O(DOUBLE),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code double} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dastore">
	 *    dastore</a>
	 */
	dastore (0x52,
		O(ARRAYREF, INDEX, DOUBLE),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Compare {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dcmp_op">
	 *    dcmpg</a>
	 */
	dcmpg (0x98,
		O(DOUBLE, DOUBLE),
		O(SIGNUM)),

	/**
	 * Compare {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dcmp_op">
	 *    dcmpl</a>
	 */
	dcmpl (0x97,
		O(DOUBLE, DOUBLE),
		O(SIGNUM)),

	/**
	 * Push {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dconst_d">
	 *    dconst_0</a>
	 */
	dconst_0 (0xe,
		O(),
		O(DOUBLE_0)),

	/**
	 * Push {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dconst_d">
	 *    dconst_1</a>
	 */
	dconst_1 (0xf,
		O(),
		O(DOUBLE_1)),

	/**
	 * Divide {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ddiv">
	 *    ddiv</a>
	 */
	ddiv (0x6f,
		O(DOUBLE, DOUBLE),
		O(DOUBLE)),

	/**
	 * Load {@code double} from local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dload">
	 *    dload</a>
	 */
	dload (0x18, 1,
		O(),
		O(DOUBLE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code double} from local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dload_n">
	 *    dload_0</a>
	 */
	dload_0 (0x26,
		O(),
		O(DOUBLE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code double} from local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dload_n">
	 *    dload_1</a>
	 */
	dload_1 (0x27,
		O(),
		O(DOUBLE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code double} from local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dload_n">
	 *    dload_2</a>
	 */
	dload_2 (0x28,
		O(),
		O(DOUBLE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code double} from local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dload_n">
	 *    dload_3</a>
	 */
	dload_3 (0x29,
		O(),
		O(DOUBLE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Multiply {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dmul">
	 *    dmul</a>
	 */
	dmul (0x6b,
		O(DOUBLE, DOUBLE),
		O(DOUBLE)),

	/**
	 * Negate {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dneg">
	 *    dneg</a>
	 */
	dneg (0x77,
		O(DOUBLE),
		O(DOUBLE)),

	/**
	 * Remainder {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.drem">
	 *    drem</a>
	 */
	drem (0x73,
		O(DOUBLE, DOUBLE),
		O(DOUBLE)),

	/**
	 * Return {@code double} from method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dreturn">
	 *    dreturn</a>
	 */
	dreturn (0xaf,
		O(DOUBLE),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}
	},

	/**
	 * Store {@code double} into local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dstore">
	 *    dstore</a>
	 */
	dstore (0x39, 1,
		O(DOUBLE),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Store {@code double} into local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dstore_n">
	 *    dstore_0</a>
	 */
	dstore_0 (0x47,
		O(DOUBLE),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code double} into local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dstore_n">
	 *    dstore_1</a>
	 */
	dstore_1 (0x48,
		O(DOUBLE),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code double} into local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dstore_n">
	 *    dstore_2</a>
	 */
	dstore_2 (0x49,
		O(DOUBLE),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code double} into local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dstore_n">
	 *    dstore_3</a>
	 */
	dstore_3 (0x4a,
		O(DOUBLE),
		O()),

	/**
	 * Subtract {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dsub">
	 *    dsub</a>
	 */
	dsub (0x67,
		O(DOUBLE, DOUBLE),
		O(DOUBLE)),

	/**
	 * Duplicate the top operand stack value.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup">
	 *    dup</a>
	 */
	dup (0x59,
		O(CATEGORY_1),
		O(CATEGORY_1))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new DupInstruction();
		}
	},

	/**
	 * Duplicate the top operand stack value and insert two values down.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup_x1">
	 *    dup_x1</a>
	 */
	dup_x1 (0x5a,
		O(CATEGORY_1, CATEGORY_1),
		O(CATEGORY_1, CATEGORY_1, CATEGORY_1))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new DupX1Instruction();
		}
	},

	/**
	 * Duplicate the top operand stack value and insert two or three values
	 * down.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup_x2">
	 *    dup_x2</a>
	 */
	dup_x2 (0x5b,
		O(CATEGORY_2, CATEGORY_1),
		O(CATEGORY_1, CATEGORY_2, CATEGORY_1))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new DupX2Instruction();
		}
	},

	/**
	 * Duplicate the top one or two operand stack values.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup2">
	 *    dup2</a>
	 */
	dup2 (0x5c,
		O(CATEGORY_2),
		O(CATEGORY_2, CATEGORY_2))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new Dup2Instruction();
		}
	},

	/**
	 * dup2_x1
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup2_x1">
	 *    dup2_x1</a>
	 */
	dup2_x1 (0x5d,
		O(CATEGORY_2, CATEGORY_1),
		O(CATEGORY_1, CATEGORY_2, CATEGORY_1))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new Dup2X1Instruction();
		}
	},

	/**
	 * dup2_x2
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.dup2_x2">
	 *    dup2_x2</a>
	 */
	dup2_x2 (0x5e,
		O(CATEGORY_2, CATEGORY_2),
		O(CATEGORY_2, CATEGORY_2, CATEGORY_2))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			return new Dup2X2Instruction();
		}
	},

	/**
	 * Convert {@code float} to {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.f2d">
	 *    f2d</a>
	 */
	f2d (0x8d,
		O(FLOAT),
		O(DOUBLE)),

	/**
	 * Convert {@code float} to {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.f2i">
	 *    f2i</a>
	 */
	f2i (0x8b,
		O(FLOAT),
		O(INT)),

	/**
	 * Convert {@code float} to {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.f2l">
	 *    f2l</a>
	 */
	f2l (0x8c,
		O(FLOAT),
		O(LONG)),

	/**
	 * Add {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fadd">
	 *    fadd</a>
	 */
	fadd (0x62,
		O(FLOAT, FLOAT),
		O(FLOAT)),

	/**
	 * Load {@code float} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.faload">
	 *    faload</a>
	 */
	faload (0x30,
		O(ARRAYREF, INDEX),
		O(FLOAT),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code float} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fastore">
	 *    fastore</a>
	 */
	fastore (0x51,
		O(ARRAYREF, INDEX, FLOAT),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Compare {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fcmp_op">
	 *    fcmpg</a>
	 */
	fcmpg (0x96,
		O(FLOAT, FLOAT),
		O(SIGNUM)),

	/**
	 * Compare {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fcmp_op">
	 *    fcmpl</a>
	 */
	fcmpl (0x95,
		O(FLOAT, FLOAT),
		O(SIGNUM)),

	/**
	 * Push {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fconst_f">
	 *    fconst_0</a>
	 */
	fconst_0 (0x0b,
		O(),
		O(FLOAT_0)),

	/**
	 * Push {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fconst_f">
	 *    fconst_1</a>
	 */
	fconst_1 (0x0c,
		O(),
		O(FLOAT_1)),

	/**
	 * Push {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fconst_f">
	 *    fconst_2</a>
	 */
	fconst_2 (0x0d,
		O(),
		O(FLOAT_2)),

	/**
	 * Divide {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fdiv">
	 *    fdiv</a>
	 */
	fdiv (0x6e,
		O(FLOAT, FLOAT),
		O(FLOAT)),

	/**
	 * Load {@code float} from local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fload">
	 *    fload</a>
	 */
	fload (0x17, 1,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code float} from local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fload_n">
	 *    fload_0</a>
	 */
	fload_0 (0x22,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code float} from local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fload_n">
	 *    fload_1</a>
	 */
	fload_1 (0x23,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code float} from local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fload_n">
	 *    fload_2</a>
	 */
	fload_2 (0x24,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code float} from local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fload_n">
	 *    fload_3</a>
	 */
	fload_3 (0x25,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Multiply {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fmul">
	 *    fmul</a>
	 */
	fmul (0x6a,
		O(FLOAT, FLOAT),
		O(FLOAT)),

	/**
	 * Negate {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fneg">
	 *    fneg</a>
	 */
	fneg (0x76,
		O(FLOAT),
		O(FLOAT)),

	/**
	 * Remainder {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.frem">
	 *    frem</a>
	 */
	frem (0x72,
		O(FLOAT, FLOAT),
		O(FLOAT)),

	/**
	 * Return {@code float} from method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.freturn">
	 *    freturn</a>
	 */
	freturn (0xae,
		O(FLOAT),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}
	},

	/**
	 * Store {@code flaot} into local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fstore">
	 *    fstore</a>
	 */
	fstore (0x38, 1,
		O(FLOAT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Store {@code float} into local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fstore_n">
	 *    fstore_0</a>
	 */
	fstore_0 (0x43,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code float} into local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fstore_n">
	 *    fstore_1</a>
	 */
	fstore_1 (0x44,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code float} into local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fstore_n">
	 *    fstore_2</a>
	 */
	fstore_2 (0x45,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code float} into local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fstore_n">
	 *    fstore_3</a>
	 */
	fstore_3 (0x46,
		O(),
		O(FLOAT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Subtract {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.fsub">
	 *    fsub</a>
	 */
	fsub (0x66,
		O(FLOAT, FLOAT),
		O(FLOAT)),

	/**
	 * Fetch field from object.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.getfield">
	 *    getfield</a>
	 */
	getfield (0xb4, 2,
		O(OBJECTREF),
		O(VALUE),
		X(NullPointerException.class,
			NoSuchFieldError.class,
			IllegalAccessError.class,
			IncompatibleClassChangeError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new GetFieldInstruction((FieldrefEntry) operands[0], false);
		}
	},

	/**
	 * Fetch field from object.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.getstatic">
	 *    getstatic</a>
	 */
	getstatic (0xb2, 2,
		O(),
		O(VALUE),
		X(NullPointerException.class,
			NoClassDefFoundError.class,
			ExceptionInInitializerError.class,
			NoSuchFieldError.class,
			IllegalAccessError.class,
			IncompatibleClassChangeError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new GetFieldInstruction((FieldrefEntry) operands[0], true);
		}
	},

	/**
	 * Branch always.
	 *
	 * <p>The usual mnemonic is {@code goto}, but this is an unused reserved
	 * word in Java. The mnemonic {@code goto_s} is chosen to contrast with
	 * {@link #goto_w}.</p>
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.goto">
	 *    goto</a>
	 */
	goto_s (0xa7, 2,
		O(),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use GotoInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public String mnemonic ()
		{
			return "goto";
		}
	},

	/**
	 * Branch always (wide index).
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.goto_w">
	 *    goto_w</a>
	 */
	goto_w (0xc8, 4,
		O(),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use GotoInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Convert {@code int} to {@code byte}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2b">
	 *    i2b</a>
	 */
	i2b (0x91,
		O(INT),
		O(INT)),

	/**
	 * Convert {@code int} to {@code char}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2c">
	 *    i2c</a>
	 */
	i2c (0x92,
		O(INT),
		O(INT)),

	/**
	 * Convert {@code int} to {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2d">
	 *    i2d</a>
	 */
	i2d (0x87,
		O(INT),
		O(DOUBLE)),

	/**
	 * Convert {@code int} to {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2f">
	 *    i2f</a>
	 */
	i2f (0x86,
		O(INT),
		O(FLOAT)),

	/**
	 * Convert {@code int} to {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2l">
	 *    i2l</a>
	 */
	i2l (0x85,
		O(INT),
		O(LONG)),

	/**
	 * Convert {@code int} to {@code short}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.i2s">
	 *    i2s</a>
	 */
	i2s (0x93,
		O(INT),
		O(INT)),

	/**
	 * Add {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iadd">
	 *    iadd</a>
	 */
	iadd (0x60,
		O(INT, INT),
		O(INT)),

	/**
	 * Load {@code int} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iaload">
	 *    iaload</a>
	 */
	iaload (0x2e,
		O(ARRAYREF, INDEX),
		O(INT),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Boolean AND {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iand">
	 *    iand</a>
	 */
	iand (0x7e,
		O(INT, INT),
		O(INT)),

	/**
	 * Store into {@code int} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iastore">
	 *    iastore</a>
	 */
	iastore (0x4f,
		O(ARRAYREF, INDEX, INT),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Push {@code int} constant -1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_m1</a>
	 */
	iconst_m1 (0x2,
		O(),
		O(INT_M1)),

	/**
	 * Push {@code int} constant 0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_0</a>
	 */
	iconst_0 (0x3,
		O(),
		O(INT_0)),

	/**
	 * Push {@code int} constant 1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_1</a>
	 */
	iconst_1 (0x4,
		O(),
		O(INT_1)),

	/**
	 * Push {@code int} constant 2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_2</a>
	 */
	iconst_2 (0x5,
		O(),
		O(INT_2)),

	/**
	 * Push {@code int} constant 3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_3</a>
	 */
	iconst_3 (0x6,
		O(),
		O(INT_3)),

	/**
	 * Push {@code int} constant 4.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_4</a>
	 */
	iconst_4 (0x7,
		O(),
		O(INT_4)),

	/**
	 * Push {@code int} constant 5.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iconst_i">
	 *    iconst_5</a>
	 */
	iconst_5 (0x8,
		O(),
		O(INT_5)),

	/**
	 * Divide {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.idiv">
	 *    idiv</a>
	 */
	idiv (0x6c,
		O(INT, INT),
		O(INT),
		X(ArithmeticException.class)),

	/**
	 * Branch if {@code reference} comparison {@code (==)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_acmp_cond">
	 *    if_acmpeq</a>
	 */
	if_acmpeq (0xa5, 2,
		O(OBJECTREF, OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code reference} comparison {@code (!=)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_acmp_cond">
	 *    if_acmpne</a>
	 */
	if_acmpne (0xa6, 2,
		O(OBJECTREF, OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (==)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmpeq</a>
	 */
	if_icmpeq (0x9f, 2,
		O(INT, INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (!=)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmpne</a>
	 */
	if_icmpne (0xa0, 2,
		O(INT, INT),
	O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (<)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmplt</a>
	 */
	if_icmplt (0xa1, 2,
		O(INT, INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (>=)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmpge</a>
	 */
	if_icmpge (0xa2, 2,
		O(INT, INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (>)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmpgt</a>
	 */
	if_icmpgt (0xa3, 2,
		O(INT, INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (<=)} succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    if_icmple</a>
	 */
	if_icmple (0xa4, 2,
		O(INT, INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (==)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_icmp_cond">
	 *    ifeq</a>
	 */
	ifeq (0x99, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (!=)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_cond">
	 *    ifne</a>
	 */
	ifne (0x9a, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (<)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_cond">
	 *    iflt</a>
	 */
	iflt (0x9b, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (>=)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_cond">
	 *    ifge</a>
	 */
	ifge (0x9c, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (>)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_cond">
	 *    ifgt</a>
	 */
	ifgt (0x9d, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code int} comparison {@code (<=)} with zero succeeds.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.if_cond">
	 *    ifle</a>
	 */
	ifle (0x9e, 2,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code reference} not {@code null}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ifnonnull">
	 *    ifnonnull</a>
	 */
	ifnonnull (0xc7, 2,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Branch if {@code reference} is {@code null}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ifnull">
	 *    ifnull</a>
	 */
	ifnull (0xc6, 2,
		O(OBJECTREF),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ConditionalBranchInstruction(
				this, (Label) operands[0]);
		}
	},

	/**
	 * Increment local variable by constant.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iinc">
	 *    iinc</a>
	 */
	iinc (0x84, 2,
		O(),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 2;
			return new IncrementInstruction(
				(LocalVariable) operands[0], (int) operands[1]);
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code int} from local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iload">
	 *    iload</a>
	 */
	iload (0x15, 1,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code int} from local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iload_n">
	 *    iload_0</a>
	 */
	iload_0 (0x1a,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code int} from local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iload_n">
	 *    iload_1</a>
	 */
	iload_1 (0x1b,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code int} from local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iload_n">
	 *    iload_2</a>
	 */
	iload_2 (0x1c,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code int} from local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iload_n">
	 *    iload_3</a>
	 */
	iload_3 (0x1d,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Multiply {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.imul">
	 *    imul</a>
	 */
	imul (0x68,
		O(INT, INT),
		O(INT)),

	/**
	 * Negate {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ineg">
	 *    ineg</a>
	 */
	ineg (0x74,
		O(INT),
		O(INT)),

	/**
	 * Determine if object is of given type.
	 *
	 * <p>The usual mnemonic is {@code instanceof}, but this conflicts with a
	 * Java reserved word.</p>
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.instanceof">
	 *    instanceof</a>
	 */
	instanceof_ (0xc1, 2,
		O(OBJECTREF),
		O(ZERO_OR_ONE))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new InstanceofInstruction((ClassEntry) operands[0]);
		}

		@Override
		public String mnemonic ()
		{
			return "instanceof";
		}
	},

	/**
	 * Invoke dynamic method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokedynamic">
	 *    invokedynamic</a>
	 */
	invokedynamic (0xba, 4,
		O(PLURAL),
		O(),
		X(BootstrapMethodError.class, WrongMethodTypeException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			// TODO: [TLS] Finish support for invokedynamic.
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Invoke interface method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokeinterface">
	 *    invokeinterface</a>
	 */
	invokeinterface (0xb9, 4,
		O(OBJECTREF, PLURAL),
		O(),
		X(IncompatibleClassChangeError.class,
			NullPointerException.class,
			NoSuchMethodError.class,
			AbstractMethodError.class,
			IllegalAccessError.class,
			UnsatisfiedLinkError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new InvokeInterfaceInstruction(
				(InterfaceMethodrefEntry) operands[0]);
		}
	},

	/**
	 * Invoke instance method; special handling for superclass, private, and
	 * instance initialization method invocations.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokespecial">
	 *    invokespecial</a>
	 */
	invokespecial (0xb7, 2,
		O(OBJECTREF, PLURAL),
		O(),
		X(IncompatibleClassChangeError.class,
			NullPointerException.class,
			NoSuchMethodError.class,
			AbstractMethodError.class,
			IllegalAccessError.class,
			UnsatisfiedLinkError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new InvokeInstruction(this, (MethodrefEntry) operands[0]);
		}
	},

	/**
	 * Invoke a class ({@code static}) method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokestatic">
	 *    invokestatic</a>
	 */
	invokestatic (0xb8, 2,
		O(PLURAL),
		O(),
		X(NoClassDefFoundError.class,
			ExceptionInInitializerError.class,
			IncompatibleClassChangeError.class,
			NoSuchMethodError.class,
			AbstractMethodError.class,
			IllegalAccessError.class,
			UnsatisfiedLinkError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new InvokeInstruction(this, (MethodrefEntry) operands[0]);
		}
	},

	/**
	 * Invoke instance method; dispatch based on class.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokevirtual">
	 *    invokevirtual</a>
	 */
	invokevirtual (0xb6, 2,
		O(OBJECTREF, PLURAL),
		O(),
		X(IncompatibleClassChangeError.class,
			NullPointerException.class,
			NoSuchMethodError.class,
			AbstractMethodError.class,
			IllegalAccessError.class,
			UnsatisfiedLinkError.class,
			WrongMethodTypeException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new InvokeInstruction(this, (MethodrefEntry) operands[0]);
		}
	},

	/**
	 * Boolean OR {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ior">
	 *    ior</a>
	 */
	ior (0x80,
		O(INT, INT),
		O(INT)),

	/**
	 * Remainder {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.irem">
	 *    irem</a>
	 */
	irem (0x70,
		O(INT, INT),
		O(INT),
		X(ArithmeticException.class)),

	/**
	 * Return {@code int} from method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ireturn">
	 *    ireturn</a>
	 */
	ireturn (0xac,
		O(INT),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}
	},

	/**
	 * Shift left {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ishl">
	 *    ishl</a>
	 */
	ishl (0x78,
		O(INT, INT),
		O(INT)),

	/**
	 * Arithmetic shift right {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ishr">
	 *    ishr</a>
	 */
	ishr (0x7a,
		O(INT, INT),
		O(INT)),

	/**
	 * Store {@code int} into local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.istore">
	 *    istore</a>
	 */
	istore (0x36, 1,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Store {@code int} into local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.istore_n">
	 *    istore_0</a>
	 */
	istore_0 (0x3b,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code int} into local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.istore_n">
	 *    istore_1</a>
	 */
	istore_1 (0x3c,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code int} into local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.istore_n">
	 *    istore_2</a>
	 */
	istore_2 (0x3d,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code int} into local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.istore_n">
	 *    istore_3</a>
	 */
	istore_3 (0x3e,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Subtract {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.isub">
	 *    isub</a>
	 */
	isub (0x64,
		O(INT, INT),
		O(INT)),

	/**
	 * Logical shift right {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.iushr">
	 *    iushr</a>
	 */
	iushr (0x7c,
		O(INT, INT),
		O(INT)),

	/**
	 * Boolean XOR {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ixor">
	 *    ixor</a>
	 */
	ixor (0x82,
		O(INT, INT),
		O(INT)),

	/**
	 * Jump subroutine.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.jsr">
	 *    jsr</a>
	 */
	jsr (0xa8, 2,
		O(),
		O(RETURN_ADDRESS))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use JumpSubroutineInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Jump subroutine (wide index).
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.jsr_w">
	 *    jsr_w</a>
	 */
	jsr_w (0xc9, 4,
		O(),
		O(RETURN_ADDRESS))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use JumpSubroutineInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Convert {@code long} to {@code double}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.l2d">
	 *    l2d</a>
	 */
	l2d (0x8a,
		O(LONG),
		O(DOUBLE)),

	/**
	 * Convert {@code long} to {@code float}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.l2f">
	 *    l2f</a>
	 */
	l2f (0x89,
		O(LONG),
		O(FLOAT)),

	/**
	 * Convert {@code long} to {@code int}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.l2i">
	 *    l2i</a>
	 */
	l2i (0x88,
		O(LONG),
		O(INT)),

	/**
	 * Add {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ladd">
	 *    ladd</a>
	 */
	ladd (0x61,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Long {@code long} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.laload">
	 *    laload</a>
	 */
	laload (0x2f,
		O(ARRAYREF, INDEX),
		O(LONG),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Boolean AND {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.land">
	 *    land</a>
	 */
	land (0x7f,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Store into {@code long} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lastore">
	 *    lastore</a>
	 */
	lastore (0x50,
		O(ARRAYREF, INDEX, LONG),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Compare {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lcmp">
	 *    lcmp</a>
	 */
	lcmp (0x94,
		O(LONG, LONG),
		O(SIGNUM)),

	/**
	 * Push {@code long} constant 0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lconst_l">
	 *    lconst_l</a>
	 */
	lconst_0 (0x9,
		O(),
		O(LONG_0)),

	/**
	 * Push {@code long} constant 1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lconst_l">
	 *    lconst_l</a>
	 */
	lconst_1 (0xa,
		O(),
		O(LONG_1)),

	/**
	 * Push item from run-time constant pool.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ldc">
	 *    ldc</a>
	 */
	ldc (0x12, 1,
		O(),
		O(VALUE),
		X(IllegalAccessError.class, IncompatibleClassChangeError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new LoadConstantInstruction((ConstantEntry) operands[0]);
		}
	},

	/**
	 * Push item from run-time constant pool (wide index).
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ldc_w">
	 *    ldc_w</a>
	 */
	ldc_w (0x13, 2,
		O(),
		O(VALUE),
		X(IllegalAccessError.class, IncompatibleClassChangeError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new LoadConstantInstruction((ConstantEntry) operands[0]);
		}
	},

	/**
	 * Push {@code long} or {@code double} from run-time constant pool (wide
	 * index).
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ldc2_w">
	 *    ldc2_w</a>
	 */
	ldc2_w (0x14, 2,
		O(),
		O(VALUE),
		X(IllegalAccessError.class, IncompatibleClassChangeError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new LoadConstantInstruction((ConstantEntry) operands[0]);
		}
	},

	/**
	 * Divide {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ldiv">
	 *    ldiv</a>
	 */
	ldiv (0x6d,
		O(LONG, LONG),
		O(LONG),
		X(ArithmeticException.class)),

	/**
	 * Load {@code long} from local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lload">
	 *    lload</a>
	 */
	lload (0x16, 1,
		O(),
		O(LONG))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Load {@code long} from local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lload_n">
	 *    lload_n</a>
	 */
	lload_0 (0x1e,
		O(),
		O(LONG))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code long} from local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lload_n">
	 *    lload_n</a>
	 */
	lload_1 (0x1f,
		O(),
		O(LONG))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code long} from local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lload_n">
	 *    lload_n</a>
	 */
	lload_2 (0x20,
		O(),
		O(LONG))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Load {@code long} from local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lload_n">
	 *    lload_n</a>
	 */
	lload_3 (0x21,
		O(),
		O(LONG))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use LoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Multiply {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lmul">
	 *    lmul</a>
	 */
	lmul (0x69,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Negate {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lneg">
	 *    lneg</a>
	 */
	lneg (0x75,
		O(LONG),
		O(LONG)),

	/**
	 * Access jump table by key match and jump.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lookupswitch">
	 *    lookupswitch</a>
	 */
	lookupswitch (0xab, 8,
		O(INT),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 3;
			return new LookupSwitchInstruction(
				(int[]) operands[0],
				(Label[]) operands[1],
				(Label) operands[2]);
		}
	},

	/**
	 * Boolean OR {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lor">
	 *    lor</a>
	 */
	lor (0x81,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Remainder {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lrem">
	 *    lrem</a>
	 */
	lrem (0x71,
		O(LONG, LONG),
		O(LONG),
		X(ArithmeticException.class)),

	/**
	 * Return {@code long} from method.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lreturn">
	 *    lreturn</a>
	 */
	lreturn (0xad,
		O(LONG),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}
	},

	/**
	 * Shift left {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lshl">
	 *    lshl</a>
	 */
	lshl (0x79,
		O(LONG, INT),
		O(LONG)),

	/**
	 * Arithmetic shift right {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lshr">
	 *    lshr</a>
	 */
	lshr (0x7b,
		O(LONG, INT),
		O(LONG)),

	/**
	 * Store {@code long} into local variable.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lstore">
	 *    lstore</a>
	 */
	lstore (0x37, 1,
		O(LONG),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean supportsWidePrefix ()
		{
			return true;
		}
	},

	/**
	 * Store {@code long} into local variable #0.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lstore_n">
	 *    lstore_n</a>
	 */
	lstore_0 (0x3f,
		O(LONG),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code long} into local variable #1.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lstore_n">
	 *    lstore_n</a>
	 */
	lstore_1 (0x40,
		O(LONG),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code long} into local variable #2.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lstore_n">
	 *    lstore_n</a>
	 */
	lstore_2 (0x41,
		O(LONG),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store {@code long} into local variable #3.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lstore_n">
	 *    lstore_n</a>
	 */
	lstore_3 (0x42,
		O(LONG),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use StoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Subtract {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lsub">
	 *    lsub</a>
	 */
	lsub (0x65,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Logical shift right {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lushr">
	 *    lushr</a>
	 */
	lushr (0x7d,
		O(LONG, INT),
		O(LONG)),

	/**
	 * Boolean XOR {@code long}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.lxor">
	 *    lxor</a>
	 */
	lxor (0x83,
		O(LONG, LONG),
		O(LONG)),

	/**
	 * Enter monitor for object.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.monitorenter">
	 *    monitorenter</a>
	 */
	monitorenter (0xc2,
		O(OBJECTREF),
		O(),
		X(NullPointerException.class)),

	/**
	 * Exit monitor for object.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.monitorexit">
	 *    monitorexit</a>
	 */
	monitorexit (0xc3,
		O(OBJECTREF),
		O(),
		X(NullPointerException.class, IllegalMonitorStateException.class)),

	/**
	 * Create new multidimensional array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.multianewarray">
	 *    multianewarray</a>
	 */
	multianewarray (0xc5, 3,
		O(PLURAL),
		O(ARRAYREF),
		X(ClassNotFoundException.class,
			NoClassDefFoundError.class,
			IllegalAccessError.class,
			NegativeArraySizeException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 2;
			return new NewObjectArrayInstruction(
				(ClassEntry) operands[0],
				(int) operands[1]);
		}
	},

	/**
	 * Create new object.
	 *
	 * <p>The usual mnemonic is {@code new}, but this is a reserved word in
	 * Java.</p>
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.new">
	 *    new</a>
	 */
	new_ (0xbb, 2,
		O(),
		O(OBJECTREF),
		X(ClassNotFoundException.class,
			NoClassDefFoundError.class,
			IllegalAccessError.class,
			InstantiationError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new NewObjectInstruction((ClassEntry) operands[0]);
		}

		@Override
		public String mnemonic ()
		{
			return "new";
		}
	},

	/**
	 * Create new array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.newarray">
	 *    newarray</a>
	 */
	newarray (0xbc, 1,
		O(COUNT),
		O(ARRAYREF),
		X(NegativeArraySizeException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use NewObjectArrayInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Do nothing.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.nop">
	 *    nop</a>
	 */
	nop (0x0,
		O(),
		O()),

	/**
	 * Pop the top operand stack value.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.pop">
	 *    pop</a>
	 */
	pop (0x57,
		O(CATEGORY_1),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 0;
			return new PopInstruction();
		}
	},

	/**
	 * Pop the top one or two operand stack values.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.pop2">
	 *    pop2</a>
	 */
	pop2 (0x58,
		O(CATEGORY_2),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 0;
			return new Pop2Instruction();
		}
	},

	/**
	 * Set field in object.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.putfield">
	 *    putfield</a>
	 */
	putfield (0xb5, 2,
		O(OBJECTREF, VALUE),
		O(),
		X(NoSuchFieldError.class,
			IncompatibleClassChangeError.class,
			IllegalAccessError.class,
			NullPointerException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new SetFieldInstruction((FieldrefEntry) operands[0], false);
		}
	},

	/**
	 * Set static field in class.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.putstatic">
	 *    putstatic</a>
	 */
	putstatic (0xb3, 2,
		O(VALUE),
		O(),
		X(NoSuchFieldError.class,
			IncompatibleClassChangeError.class,
			IllegalAccessError.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new SetFieldInstruction((FieldrefEntry) operands[0], true);
		}
	},

	/**
	 * Return from subroutine.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.ret">
	 *    ret</a>
	 */
	ret (0xa9, 1,
		O(),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new ReturnSubroutineInstruction((LocalVariable) operands[0]);
		}
	},

	/**
	 * Return {@code void} from method.
	 *
	 * <p>The usual mnemonic is {@code return}, but this is a reserved word in
	 * Java.</p>
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.return">
	 *    return</a>
	 */
	return_ (0xb1,
		O(),
		O(),
		X(IllegalMonitorStateException.class))
	{
		@Override
		public boolean isReturn ()
		{
			return true;
		}

		@Override
		public String mnemonic ()
		{
			return "return";
		}
	},

	/**
	 * Load {@code short} from array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.saload">
	 *    saload</a>
	 */
	saload (0x35,
		O(ARRAYREF, INDEX),
		O(INT),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayLoadInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Store into {@code short} array.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.sastore">
	 *    sastore</a>
	 */
	sastore (0x56,
		O(ARRAYREF, INDEX, INT),
		O(),
		X(NullPointerException.class, ArrayIndexOutOfBoundsException.class))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert false : "Use ArrayStoreInstruction instead";
			throw new UnsupportedOperationException();
		}
	},

	/**
	 * Push {@code short}.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.sipush">
	 *    sipush</a>
	 */
	sipush (0x11, 2,
		O(),
		O(INT))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new PushShortInstruction((int) operands[0]);
		}
	},

	/**
	 * Swap the top two operand stack values.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.swap">
	 *    swap</a>
	 */
	swap (0x5f,
		O(CATEGORY_1, CATEGORY_1),
		O(CATEGORY_1, CATEGORY_1))
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new SwapInstruction();
		}
	},

	/**
	 * Access jump table by index and jump.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.tableswitch">
	 *    tableswitch</a>
	 */
	tableswitch (0xaa, 12,
		O(INDEX),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			assert operands.length == 1;
			return new TableSwitchInstruction(
				(int) operands[0],
				(int) operands[1],
				(Label[]) operands[2],
				(Label) operands[3]);
		}
	},

	/**
	 * Extend local variable index by additional bytes.
	 *
	 * @see <a
	 *    href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.wide">
	 *    wide</a>
	 */
	wide (0xc4, 3,
		O(),
		O())
	{
		@Override
		public JavaInstruction create (final Object... operands)
		{
			final JavaBytecode bytecode = (JavaBytecode) operands[0];
			assert bytecode.supportsWidePrefix();
			switch (bytecode)
			{
				case aload:
				case dload:
				case fload:
				case iload:
				case lload:
					return new LoadInstruction((LocalVariable) operands[1]);
				case astore:
				case dstore:
				case fstore:
				case istore:
				case lstore:
					return new StoreInstruction((LocalVariable) operands[1]);
				case iinc:
					return new IncrementInstruction(
						(LocalVariable) operands[1], (int) operands[2]);
				default:
					assert false : "This never happens";
					throw new IllegalArgumentException();
			}
		}
	},

	/**
	 * Invalid instruction.
	 */
	invalid (0xfd,
		O(),
		O())
	{
		@Override
		public String mnemonic ()
		{
			return "«invalid»";
		}
	};

	/**
	 * Construct an array from lexical elements.
	 *
	 * @param elements
	 *        The array elements.
	 * @return The argument.
	 */
	private static JavaOperand[] O (final JavaOperand... elements)
	{
		return elements;
	}

	/**
	 * Construct an array from lexical elements.
	 *
	 * @param elements
	 *        The array elements.
	 * @return The argument.
	 */
	@SafeVarargs
	private static Class<? extends Throwable>[] X (
		final Class<? extends Throwable>... elements)
	{
		return elements;
	}

	/** The opcode. */
	private final int opcode;

	/**
	 * Answer the opcode.
	 *
	 * @return The opcode.
	 */
	public int opcode ()
	{
		return opcode;
	}

	/** A table of the {@link JavaBytecode}s by opcode. */
	private static JavaBytecode[] byOpcode = new JavaBytecode[256];

	/**
	 * Answer the {@link JavaBytecode} that corresponds to the specified opcode.
	 *
	 * @param opcode
	 *        An opcode.
	 * @return A {@code JavaBytecode}.
	 */
	public static JavaBytecode byOpcode (final int opcode)
	{
		return byOpcode[opcode];
	}

	static
	{
		for (final JavaBytecode bytecode : JavaBytecode.values())
		{
			assert byOpcode[bytecode.opcode] == null : String.format(
				"%s and %s have the same opcode",
				byOpcode[bytecode.opcode],
				bytecode);
			byOpcode[bytecode.opcode] = bytecode;
		}
	}

	/** The minimum format size, measured in bytes. */
	private final int minimumFormatSize;

	/**
	 * Answer the minimum format size, measured in bytes.
	 *
	 * @return The minimum format size.
	 */
	public int minimumFormatSize ()
	{
		return minimumFormatSize;
	}

	/**
	 * Does the {@linkplain JavaBytecode bytecode} affect a {@code return}?
	 *
	 * @return {@code true} if the bytecode affects a return, {@code false}
	 *         otherwise.
	 */
	public boolean isReturn ()
	{
		return false;
	}

	/** The input {@linkplain JavaOperand stack operands}. */
	private final JavaOperand[] inputOperands;

	/**
	 * Answer the input {@linkplain JavaOperand stack operands}.
	 *
	 * @return The input stack operands.
	 */
	public JavaOperand[] inputOperands ()
	{
		return inputOperands;
	}

	/** The output {@linkplain JavaOperand stack operands}. */
	private final JavaOperand[] outputOperands;

	/**
	 * Answer the output {@linkplain JavaOperand stack operands}.
	 *
	 * @return The output stack operands.
	 */
	public JavaOperand[] outputOperands ()
	{
		return outputOperands;
	}

	/** The {@link Throwable} classes that can be raised by this opcode. */
	private final Class<? extends Throwable>[] exceptions;

	/**
	 * Answer the {@link Throwable} classes that can be raised by this opcode.
	 *
	 * @return The possible {@code Throwable} classes.
	 */
	public Class<? extends Throwable>[] exceptions ()
	{
		return exceptions;
	}

	/**
	 * Answer the mnemonic for this {@linkplain JavaBytecode bytecode}.
	 *
	 * @return The mnemonic for this bytecode.
	 */
	public String mnemonic ()
	{
		return toString();
	}

	/**
	 * Does the {@linkplain JavaBytecode bytecode} support the {@link #wide}
	 * prefix?
	 *
	 * @return {@code true} if the bytecode supports the wide prefix, {@code
	 *         false} otherwise.
	 */
	public boolean supportsWidePrefix ()
	{
		return false;
	}

	/**
	 * Write the {@linkplain JavaBytecode bytecode} to the specified {@linkplain
	 * DataOutput binary stream}.
	 *
	 * @param out
	 *        A binary output stream.
	 * @throws IOException
	 *         If the operation fails.
	 */
	public final void writeTo (final DataOutput out) throws IOException
	{
		out.writeByte(opcode);
	}

	/**
	 * Create an {@linkplain JavaInstruction instruction} that represents the
	 * receiving {@linkplain JavaBytecode bytecode}.
	 *
	 * @param operands
	 *        The operands.
	 * @return An instruction.
	 */
	public JavaInstruction create (final Object... operands)
	{
		assert operands.length == 0;
		return new SimpleInstruction(this);
	}

	/**
	 * Construct a new {@link JavaBytecode}.
	 *
	 * @param opcode
	 *        The opcode.
	 * @param extraBytes
	 *        The minimum extra bytes.
	 * @param inputOperands
	 *        The input {@linkplain JavaOperand stack operands}.
	 * @param outputOperands
	 *        The output stack operands.
	 * @param exceptions
	 *        The {@link Throwable} classes that can be raised by this opcode.
	 */
	private JavaBytecode (
		final int opcode,
		final int extraBytes,
		final JavaOperand[] inputOperands,
		final JavaOperand[] outputOperands,
		final Class<? extends Throwable>[] exceptions)
	{
		assert (opcode & 255) == opcode;
		this.opcode = opcode;
		this.minimumFormatSize = 1 + extraBytes;
		this.inputOperands = inputOperands;
		this.outputOperands = outputOperands;
		this.exceptions = exceptions;
	}

	/**
	 * Construct a new {@link JavaBytecode}.
	 *
	 * @param opcode
	 *        The opcode.
	 * @param minimumFormatSize
	 *        The minimum format size, measured in bytes.
	 * @param inputOperands
	 *        The input {@linkplain JavaOperand stack operands}.
	 * @param outputOperands
	 *        The output stack operands.
	 */
	private JavaBytecode (
		final int opcode,
		final int minimumFormatSize,
		final JavaOperand[] inputOperands,
		final JavaOperand[] outputOperands)
	{
		this(opcode, minimumFormatSize, inputOperands, outputOperands, X());
	}

	/**
	 * Construct a new {@link JavaBytecode}.
	 *
	 * @param opcode
	 *        The opcode.
	 * @param inputOperands
	 *        The input {@linkplain JavaOperand stack operands}.
	 * @param outputOperands
	 *        The output stack operands.
	 * @param exceptions
	 *        The {@link Throwable} classes that can be raised by this opcode.
	 */
	private JavaBytecode (
		final int opcode,
		final JavaOperand[] inputOperands,
		final JavaOperand[] outputOperands,
		final Class<? extends Throwable>[] exceptions)
	{
		this(opcode, 0, inputOperands, outputOperands, exceptions);
	}

	/**
	 * Construct a new {@link JavaBytecode}.
	 *
	 * @param opcode
	 *        The opcode.
	 * @param inputOperands
	 *        The input {@linkplain JavaOperand stack operands}.
	 * @param outputOperands
	 *        The output stack operands.
	 */
	private JavaBytecode (
		final int opcode,
		final JavaOperand[] inputOperands,
		final JavaOperand[] outputOperands)
	{
		this(opcode, 0, inputOperands, outputOperands, X());
	}
}
