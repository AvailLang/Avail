/**
 * A_Variable.java
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

package com.avail.descriptor;

import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.VariableGetException;
import com.avail.exceptions.VariableSetException;

/**
 * {@code A_Variable} is an interface that specifies the behavior specific to
 * Avail {@linkplain VariableDescriptor variables} that an {@link AvailObject}
 * must implement.  It's a sub-interface of {@link A_BasicObject}, the interface
 * that defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Variable
extends A_ChunkDependable
{
	/**
	 * Extract the current value of the {@linkplain VariableDescriptor
	 * variable}.  Answer {@linkplain NilDescriptor#nil() nil} if the variable
	 * has no value.
	 *
	 * @return The variable's value or nil.
	 */
	AvailObject value ();

	/**
	 * Extract the current value of the {@linkplain VariableDescriptor
	 * variable}.  Fail if the variable has no value.
	 *
	 * @return The variable's value.
	 * @throws VariableGetException
	 *         If the current value could not be read, e.g., because the
	 *         variable is unassigned.
	 */
	AvailObject getValue () throws VariableGetException;

	/**
	 * Answer {@code true} if the variable currently has a value, otherwise
	 * answer {@code false}.  No value is typically represented by the
	 * variable's value slot containing {@link NilDescriptor#nil()}.
	 *
	 * @return Whether the variable has a value.
	 */
	boolean hasValue ();

	/**
	 * Assign the given value to the {@linkplain VariableDescriptor variable}.
	 * Fail if the value does not have a type suitable for the variable.
	 *
	 * @param newValue The variable's proposed new value.
	 * @throws VariableSetException
	 *         If the new value is incorrectly typed.
	 */
	void setValue (A_BasicObject newValue) throws VariableSetException;

	/**
	 * Assign the given value to the {@linkplain VariableDescriptor variable}.
	 * The client should ensure that the value is acceptable for the variable.
	 *
	 * @param newValue The variable's new value.
	 */
	void setValueNoCheck (A_BasicObject newValue);

	/**
	 * Read the variable's value and set it to the new value.  Answer the old
	 * value.  Fail if the new value is not suitable for the variable, or if the
	 * variable had no value.  Ensure that the entire operation runs atomically
	 * with respect to other reads and writes of the variable.  Use information
	 * about whether the variable is potentially {@linkplain Mutability#SHARED
	 * shared} between Avail {@linkplain FiberDescriptor fibers} to determine
	 * whether locking operations are needed.
	 *
	 * @param newValue The value to assign.
	 * @return The previous value of the variable.
	 * @throws VariableGetException
	 *         If the current value could not be read, e.g., because the
	 *         variable is unassigned.
	 * @throws VariableSetException
	 *         If the new value is incorrectly typed.
	 */
	AvailObject getAndSetValue (A_BasicObject newValue)
		throws VariableGetException, VariableSetException;

	/**
	 * Read the variable's value, compare it to a reference value via semantic
	 * {@linkplain A_BasicObject#equals(A_BasicObject) equality}, and if they're
	 * equal, store a provided new value into the variable and answer true.
	 * Otherwise answer false.  If the variable is potentially {@linkplain
	 * Mutability#SHARED shared}, then ensure suitable locks bracket this entire
	 * sequence of operations.
	 *
	 * @param reference
	 *        The value to compare against the variable's current value.
	 * @param newValue
	 *        The replacement value to store if the reference value is equal to
	 *        the variable's old value.
	 * @return Whether the replacement took place.
	 * @throws VariableGetException
	 *         If the current value could not be read, e.g., because the
	 *         variable is unassigned.
	 * @throws VariableSetException
	 *         If the new value is incorrectly typed.
	 */
	boolean compareAndSwapValues (
			A_BasicObject reference,
			A_BasicObject newValue)
		throws VariableGetException, VariableSetException;

	/**
	 * Read the variable's value, add the addend to it, and store it back into
	 * the variable.  This sequence of operations is protected by a lock if the
	 * variable is potentially {@linkplain Mutability#SHARED shared} among
	 * multiple Avail {@linkplain FiberDescriptor fibers}.  Fail if the variable
	 * had no value, if the variable's content type is not a subtype of the
	 * {@linkplain IntegerRangeTypeDescriptor#extendedIntegers() extended
	 * integers}, if the addend is not an extended integer, if the sum of the
	 * old value and the addend is undefined (e.g., ∞ plus -∞), or if the sum
	 * does not satisfy the variable's {@linkplain VariableTypeDescriptor
	 * #o_WriteType(AvailObject) write type}.  Return the previous value.
	 *
	 * <p>
	 * It is the client's responsibility to ensure the {@linkplain
	 * VariableTypeDescriptor#o_ReadType(AvailObject) read type} of the variable
	 * is a subtype of extended integer.
	 * </p>
	 *
	 * @param addend The value by which to adjust the variable.
	 * @return The previous value of the variable.
	 * @throws VariableGetException
	 *         If the current value could not be read, e.g., because the
	 *         variable is unassigned.
	 * @throws VariableSetException
	 *         If the new value is incorrectly typed.
	 */
	A_Number fetchAndAddValue (A_Number addend)
		throws VariableGetException, VariableSetException;

	/**
	 * Clear the variable.  This causes the variable to have no value, and
	 * subsequent attempts to {@linkplain A_Variable#getValue() get the value}
	 * of this variable will fail.
	 *
	 * <p>
	 * The variable is not required to have a value prior to this operation.
	 * </p>
	 */
	void clearValue ();

	/**
	 * Add a {@linkplain VariableAccessReactor write reactor} to the {@linkplain
	 * VariableDescriptor variable} and associate it with the specified
	 * key (for subsequent removal).
	 *
	 * @param key
	 *        An {@linkplain AtomDescriptor atom}.
	 * @param reactor
	 *        A write reactor.
	 * @return The target variable (possibly {@linkplain Mutability#SHARED
	 *         shared} now).
	 */
	A_Variable addWriteReactor (
		final A_Atom key,
		final VariableAccessReactor reactor);

	/**
	 * Remove the {@linkplain VariableAccessReactor write reactor} associated
	 * with the specified {@linkplain AtomDescriptor key} from the {@linkplain
	 * VariableDescriptor variable}.
	 *
	 * @param key
	 *        An atom.
	 * @throws AvailException
	 *         If the {@linkplain AvailErrorCode#E_KEY_NOT_FOUND key is not
	 *         found}.
	 */
	void removeWriteReactor (final A_Atom key) throws AvailException;

	/**
	 * Answer the {@linkplain SetDescriptor set} of {@linkplain
	 * VariableAccessReactor write reactor} {@linkplain FunctionDescriptor
	 * functions} that have not previously activated.
	 *
	 * @return The requested functions.
	 */
	A_Set validWriteReactorFunctions ();

	/**
	 * Answer whether this variable is both a write-once variable and
	 * initialized from an expression which is stable – always produces the same
	 * value (modulo loading of modules) and has no side-effects.
	 *
	 * @return Whether the variable was initialized from a stable computation.
	 */
	boolean valueWasStablyComputed ();

	/**
	 * Set whether this write-once variable was initialized from an expression
	 * which is stable – always produces the same value (modulo loading of
	 * modules) and has no side-effects.
	 *
	 * @param wasStablyComputed
	 *        Whether the variable was initialized from a stable computation.
	 */
	void valueWasStablyComputed (final boolean wasStablyComputed);

	/**
	 * Extract the map from this variable, add the key → value binding to it,
	 * and write it back into the variable.
	 *
	 * <p>This is an atomic operation, so the update is serialized with respect
	 * to other operations on this variable.</p>
	 *
	 * @param key The key to add to the map.
	 * @param value The value to add to the map.
	 * @throws VariableGetException If the variable does not contain a map.
	 * @throws VariableSetException If the updated map cannot be written back.
	 */
	void atomicAddToMap (
		final A_BasicObject key,
		final A_BasicObject value)
	throws VariableGetException, VariableSetException;

	/**
	 * Test whether the map in this variable has the specified key.
	 *
	 * <p>This is an atomic operation, so the read is serialized with respect
	 * to other operations on this variable.</p>
	 *
	 * @param key The key to look for in the map.
	 * @throws VariableGetException If the variable is uninitialized.
	 */
	boolean variableMapHasKey (
		final A_BasicObject key)
	throws VariableGetException;

	/**
	 * Answer whether this variable is a module-scoped global.
	 *
	 * @return
	 */
	boolean isGlobal ();

	/**
	 * Only applicable to {@link VariableSharedGlobalDescriptor global
	 * variables}.  Answer the {@link A_Module module} in which it's defined.
	 *
	 * @return The module in which this global variable/constant is defined.
	 */
	A_Module globalModule ();

	/**
	 * Only applicable to {@link VariableSharedGlobalDescriptor global
	 * variables}.  Answer the name of this global variable or constant.
	 *
	 * @return The name of this global variable/constant.
	 */
	A_String globalName ();
}
