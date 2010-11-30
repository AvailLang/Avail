/**
 * compiler/Mutable.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler;

/**
 * Support explicit mutable wrapping of variables.  This is used specifically
 * for allowing non-final variables to be used by inner classes.  The uses were
 * generated automatically by the Smalltalk -> Java translator after flow
 * analysis.
 * <p>
 * Say there's a variable called <code>outer</code> of type <code>Foo</code>,
 * and you want to read and write it from within an inner class's methods.
 * Simply change its definition from "<code>Foo outer;</code>" to
 * "<code>Mutable&lt;Foo&gt; outer = new Mutable&lt;Foo&gt;();</code>" and change
 * all references to <code>outer</code> to be references to
 * <code>outer.value</code> instead.
 * <p>
 * Primitive Java types are accommodated via their boxed counterparts.  For
 * example, "<code>int x;</code>" becomes
 * "<code>Mutable&lt;Integer&gt = new Mutable&lt;Integer&gt;();</code>", and
 * assignments like "<code>x = 5;</code>" become "<code>x.value = 5;</code>".
 * Java's autoboxing takes care of the rest.
 *
 * @author Mark van Gulik&lt;ghoul137@gmail.com&gt;
 * @param <T> The type of mutable object.
 */
public class Mutable<T>
{
	/**
	 * Expose a public field for readability.  For instance, one could declare
	 * something "final Mutable<Integer> x = new Mutable<Integer>();" and
	 * then have code within inner classes like "x.value = 5" or "x.value++".
	 */
	public T value;

	@Override
	public String toString ()
	{
		return value == null ? "null" : value.toString();
	}
}