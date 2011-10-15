/**
 * interpreter/levelOne/L1OperationDispatcher.java
 * Copyright (c) 2010-2011, Mark van Gulik.
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

package com.avail.interpreter.levelOne;

import com.avail.descriptor.NullDescriptor;


/**
 * Provide a generic mechanism for visiting instructions.  In particular, each
 * {@link L1Operation} knows how to {@link
 * L1Operation#dispatch(L1OperationDispatcher) dispatch} to a suitable one of
 * my methods.
 *
 * @author Mark van Gulik&lt;ghoul137@gmail.com&gt;
 */
public interface L1OperationDispatcher
{
	/**
	 * [n,m] - Send the message at index n in the compiledCode's literals. Pop
	 * the arguments for this message off the stack (the message itself
	 * knows how many to expect). The first argument was pushed first, and
	 * is the deepest on the stack. Use these arguments to look up the
	 * method dynamically. Before invoking the method, push the {@linkplain
	 * NullDescriptor#nullObject() null object} onto the stack. Its presence
	 * will help distinguish continuations produced by the pushLabel
	 * instruction from their senders. When the call completes (if ever) by
	 * using an implicit return instruction, it will replace this null object
	 * with the result of the call.
	 */
	void L1_doCall();

	/**
	 * [n] - Push the literal indexed by n in the current compiledCode.
	 */
	void L1_doPushLiteral();

	/**
	 * [n] - Push the argument (actual value) or local variable (the
	 * variable itself) indexed by n. Since this is known to be the last use
	 * (nondebugger) of the argument or local, void that slot of the current
	 * continuation.
	 */
	void L1_doPushLastLocal();

	/**
	 * [n] - Push the argument (actual value) or local variable (the
	 * variable itself) indexed by n.
	 */
	void L1_doPushLocal();

	/**
	 * [n] - Push the outer variable indexed by n in the current function. If
	 * the variable is mutable, clear it (no one will know). If the variable
	 * and function are both mutable, remove the variable from the function by
	 * voiding it.
	 */
	void L1_doPushLastOuter();

	/**
	 * [n,m] - Pop the top n items off the stack, and use them as outer
	 * variables in the construction of a function based on the compiledCode
	 * that's the literal at index m of the current compiledCode.
	 */
	void L1_doClose();

	/**
	 * [n] - Pop the stack and assign this value to the local variable (not
	 * an argument) indexed by n (index 1 is first argument).
	 */
	void L1_doSetLocal();

	/**
	 * [n] - Push the value of the local variable (not an argument) indexed
	 * by n (index 1 is first argument). If the variable itself is mutable,
	 * clear it now - nobody will know.
	 */
	void L1_doGetLocalClearing();

	/**
	 * [n] - Push the outer variable indexed by n in the current function.
	 */
	void L1_doPushOuter();

	/**
	 * [] - Remove the top item from the stack.
	 */
	void L1_doPop();

	/**
	 * [n] - Push the value of the outer variable indexed by n in the
	 * current function. If the variable itself is mutable, clear it at this
	 * time - nobody will know.
	 */
	void L1_doGetOuterClearing();

	/**
	 * [n] - Pop the stack and assign this value to the outer variable
	 * indexed by n in the current function.
	 */
	void L1_doSetOuter();

	/**
	 * [n] - Push the value of the local variable (not an argument) indexed
	 * by n (index 1 is first argument).
	 */
	void L1_doGetLocal();

	/**
	 * [n] - Make a tuple from n values popped from the stack.  Push the tuple.
	 */
	void L1_doMakeTuple();

	/**
	 * [n] - Push the value of the outer variable indexed by n in the
	 * current function.
	 */
	void L1_doGetOuter();

	/**
	 * The extension nybblecode was encountered. Read another nybble and
	 * dispatch it as an extended instruction.
	 */
	void L1_doExtension();

	/**
	 * Build a continuation which, when restarted, will be just like
	 * restarting the current continuation.
	 */
	void L1Ext_doPushLabel();

	/**
	 * [n] - Push the value of the variable that's literal number n in the
	 * current compiledCode.
	 */
	void L1Ext_doGetLiteral();

	/**
	 * [n] - Pop the stack and assign this value to the variable that's the
	 * literal indexed by n in the current compiledCode.
	 */
	void L1Ext_doSetLiteral();

	/**
	 * [n,m] - Send the message at index n in the compiledCode's literals.
	 * Like the call instruction, the arguments will have been pushed on the
	 * stack in order, but unlike call, each argument's type will also have
	 * been pushed (all arguments are pushed, then all argument types).
	 * These are either the arguments' exact types, or constant types (that
	 * must be supertypes of the arguments' types), or any mixture of the
	 * two. These types will be used for method lookup, rather than the
	 * argument types. This supports a 'super'-like mechanism in the
	 * presence of multimethods. Like the call instruction, all arguments
	 * (and types) are popped, then a sentinel null object is pushed, and
	 * the looked up method is started. When the invoked method returns (via
	 * an implicit return instruction), this sentinel will be replaced by
	 * the result of the call.
	 */
	void L1Ext_doSuperCall();

	/**
	 * [n] - Push the nth stack element's type. This is only used by the
	 * supercast mechanism to produce types for arguments not being cast.
	 * See #doSuperCall. This implies the type will be used for a lookup and
	 * then discarded. We therefore don't treat the type as acquiring a new
	 * reference from the stack, so it doesn't have to become immutable.
	 * This could be a sticky point with the garbage collector if it finds
	 * only one reference to the type, but I think it will still work.
	 *
	 * <p>
	 * Strike that.  The level one state has to have a consistent reference
	 * count, so we have to make the object immutable in case the type has
	 * to refer to it.
	 * </p>
	 */
	void L1Ext_doGetType();

	/**
	 * Duplicate the element at the top of the stack. Make the element
	 * immutable since there are now at least two references.
	 */
	void L1Ext_doDuplicate ();

	/**
	 * This shouldn't happen unless the compiler is out of sync with the
	 * interpreter.
	 */
	void L1Ext_doReserved();

	/**
	 * Return to the calling continuation with top of stack.  This isn't an
	 * actual instruction (any more), but it's implied after every block.
	 * Note that the calling continuation has automatically pushed the
	 * expected return type, which after being used to check the return
	 * value should simply be replaced by this value.  This avoids
	 * manipulating the stack depth.
	 */
	void L1Implied_doReturn();
}
