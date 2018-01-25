/**
 * L2ToJavaGenerator.java
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

package com.avail.optimizer;

import com.avail.descriptor.AvailObject;
import com.sun.org.apache.bcel.internal.generic.ArrayType;
import com.sun.org.apache.bcel.internal.generic.ClassGen;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.bcel.internal.generic.MethodGen;
import com.sun.org.apache.bcel.internal.generic.Type;

import java.util.concurrent.atomic.AtomicLong;

import static com.sun.org.apache.bcel.internal.Constants.ACC_FINAL;
import static com.sun.org.apache.bcel.internal.Constants.ACC_PUBLIC;

/**
 * The {@code L2ToJavaGenerator} generates a corresponding subclass of {@link
 * L2JavaTranslation}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2ToJavaGenerator
{
	static AtomicLong counter = new AtomicLong(0);

//	ClassGen  cg = new ClassGen("HelloWorld", "java.lang.Object",
//		"<generated>", ACC_PUBLIC | ACC_SUPER, null);
//	MethodGen mg = new MethodGen(ACC_STATIC | ACC_PUBLIC,
//		Type.VOID, new Type[] { new ArrayType(Type.STRING, 1) },
//		new String[] { "argv" }, "main", "HelloWorld", il, cp);
//...
//	cg.addMethod(mg.getMethod());
//il.dispose(); // Reuse instruction handles of list

	final ClassGen classGenerator = new ClassGen(
		"com.avail.dynamic.$" + counter.incrementAndGet(),
		L2JavaTranslation.class.getCanonicalName(),
		"<generated>",
		ACC_PUBLIC | ACC_FINAL,
		null);

	final MethodGen startMethodGenerator = new MethodGen(
		ACC_PUBLIC,
		Type.getType(AvailObject.class),
		new Type[] { new ArrayType(Type.getType(AvailObject.class), 1)},
		new String [] { "args" },
		"start",
		classGenerator.getClassName(),
		new InstructionList(),
		new ConstantPoolGen());

	final MethodGen resumeMethodGenerator = new MethodGen(
		ACC_PUBLIC,
		Type.getType(AvailObject.class),
		new Type[] { new ArrayType(Type.getType(AvailObject.class), 1)},
		new String [] { "thisContinuation" },
		"resume",
		classGenerator.getClassName(),
		new InstructionList(),
		new ConstantPoolGen());

	L2ToJavaGenerator ()
	{
		// nothing
	}
}
