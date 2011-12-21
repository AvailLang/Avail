/**
 * AbstractAvailObject.java
 * Copyright (c) 2011, Mark van Gulik.
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

import com.avail.annotations.*;

/**
 * {@code AbstractAvailObject} specifies the essential layout and storage
 * requirements of an Avail object, but does not specify a particular
 * representation. As such, it defines requirements for object and integer
 * storage capability, identity comparison by object address, indirection
 * capability, and descriptor access.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
abstract class AbstractAvailObject
{
	/**
	 * Check if the object's address is valid. Throw an {@link Error} if it
	 * lies outside of all the currently allocated memory regions.
	 *
	 * <p>Not implemented meaningfully in the Java implementation.</p>
	 *
	 * @throws Error
	 *         If the address is invalid.
	 */
	final void checkValidAddress () throws Error
	{
		return;
	}

	/**
	 * Check if the object's address is a valid pointer into to-space. Throw an
	 * {@link Error} if it lies outside of to-space.
	 *
	 * <p>Not implemented meaningfully in the Java implementation.</p>
	 *
	 * @throws Error
	 *         If the address is invalid.
	 */
	final void verifyToSpaceAddress () throws Error
	{
		return;
	}

	/**
	 * Answer whether the {@linkplain AvailObject objects} occupy the same
	 * memory addresses.
	 *
	 * @param anotherObject Another object.
	 * @return Whether the objects occupy the same storage.
	 */
	public final boolean sameAddressAs (
		final @NotNull AbstractAvailObject anotherObject)
	{
		// verifyToSpaceAddress();
		// anotherObject.verifyToSpaceAddress();
		return this == anotherObject;
	}

	/**
	 * The object's {@linkplain AbstractDescriptor descriptor}. Most messages
	 * are redirected through the descriptor to allow the behavior and
	 * representation to change, often without changing the observable
	 * semantics. The descriptor essentially says how this object should behave,
	 * including how its fields are laid out.
	 */
	protected @NotNull AbstractDescriptor descriptor;

	/**
	 * Answer the object's {@linkplain AbstractDescriptor descriptor}.
	 *
	 * @return A descriptor.
	 */
	public final @NotNull AbstractDescriptor descriptor ()
	{
		return descriptor;
	}

	/**
	 * Replace the {@linkplain AbstractDescriptor descriptor} with a {@linkplain
	 * FillerDescriptor filler object}. This blows up for most messages,
	 * catching further uses of this object; note that all further uses are
	 * incorrect by definition.
	 */
	public final void destroy ()
	{
		// verifyToSpaceAddress();
		descriptor = FillerDescriptor.mutable();
	}

	/**
	 * Has this {@linkplain AvailObject object} been {@linkplain #destroy()
	 * destroyed}?
	 *
	 * @return {@code true} if the object has been destroyed, {@code false}
	 *         otherwise.
	 */
	final boolean isDestroyed ()
	{
		checkValidAddress();
		return descriptor == FillerDescriptor.mutable();
	}

	/**
	 * Answer the object's {@linkplain AbstractDescriptor descriptor}'s
	 * {@linkplain AbstractDescriptor#id() id}.
	 *
	 * @return A {@code short} that identifies the descriptor.
	 */
	final short descriptorId ()
	{
		return descriptor.id();
	}

	/**
	 * Set the object's {@linkplain AbstractDescriptor descriptor} by
	 * {@linkplain AbstractDescriptor#id() id}.
	 *
	 * @param anInteger A {@code short} that identifies the new descriptor.
	 */
	final void descriptorId (final short anInteger)
	{
		descriptor = AbstractDescriptor.allDescriptors.get(anInteger);
		checkValidAddress();
	}

	/**
	 * Answer the number of integer slots. All variable integer slots occur
	 * following the last fixed integer slot.
	 *
	 * @return The number of integer slots.
	 */
	public abstract int integerSlotsCount ();

	/**
	 * Answer the number of variable integer slots in this object. This does not
	 * include the fixed integer slots.
	 *
	 * @return The number of variable integer slots.
	 */
	final int variableIntegerSlotsCount ()
	{
		return integerSlotsCount() - descriptor.numberOfFixedIntegerSlots();
	}

	/**
	 * Extract the value of the {@link BitField} of the {@code int} located at
	 * the {@linkplain AbstractSlotsEnum#ordinal() ordinal} of the given field
	 * {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subfield A {@code BitField} that defines the subfield layout.
	 * @return An {@code int} extracted from this object.
	 */
	abstract int bitSlot (
		final @NotNull IntegerSlotsEnum field,
		final @NotNull BitField subfield);

	/**
	 * Replace the value of the {@link BitField} of the {@code int} located at
	 * the {@linkplain AbstractSlotsEnum#ordinal() ordinal} of the given field
	 * {@code enum} value.
	 *
	 * @param field An enumeration value that defines the field ordering.
	 * @param subfield A {@code BitField} that defines the subfield layout.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	abstract void bitSlotPut (
		final @NotNull IntegerSlotsEnum field,
		final @NotNull BitField subfield,
		final int anInteger);

	/**
	 * Extract the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @return The unsigned byte as a short.
	 */
	abstract short byteSlotAt (
		final @NotNull IntegerSlotsEnum e,
		final int byteSubscript);

	/**
	 * Replace the byte at the given one-based byte subscript within the
	 * specified field. Always use little-endian encoding.
	 *
	 * @param e An enumeration value representing an integer field.
	 * @param byteSubscript Which byte to extract.
	 * @param aByte The unsigned byte to write, passed as a short.
	 */
	abstract void byteSlotAtPut (
		final @NotNull IntegerSlotsEnum e,
		final int byteSubscript,
		final short aByte);

	/**
	 * Extract a (16-bit signed) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param e The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @return The {@code short} found at the given short-index.
	 */
	abstract short shortSlotAt (
		final @NotNull IntegerSlotsEnum e,
		final int shortIndex);

	/**
	 * Store the (16-bit signed) {@code short} at the given short-index of the
	 * receiver.
	 *
	 * @param e The enumeration value that identifies the base field.
	 * @param shortIndex The index in bytes (must be even).
	 * @param aShort The {@code short} to store at the given short-index.
	 */
	abstract void shortSlotAtPut (
		final @NotNull IntegerSlotsEnum e,
		final int shortIndex,
		final short aShort);

	/**
	 * Extract the (signed 32-bit) integer for the given field {@code enum}
	 * value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @return An {@code int} extracted from this object.
	 */
	abstract int slot (final @NotNull IntegerSlotsEnum e);

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	abstract void setSlot (
		final @NotNull IntegerSlotsEnum e,
		final int anInteger);

	/**
	 * Extract the (signed 32-bit) integer at the given field enum value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return An {@code int} extracted from this object.
	 */
	abstract int slot (
		final @NotNull IntegerSlotsEnum e,
		final int subscript);

	/**
	 * Store the (signed 32-bit) integer in the four bytes starting at the
	 * given field {@code enum} value.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anInteger An {@code int} to store in the indicated slot.
	 */
	abstract void setSlot (
		final @NotNull IntegerSlotsEnum e,
		final int subscript,
		final int anInteger);

	/**
	 * Answer the number of object slots in this {@link AvailObject}. All
	 * variable object slots occur following the last fixed object slot.
	 *
	 * @return The number of object slots.
	 */
	public abstract int objectSlotsCount ();

	/**
	 * Answer the number of variable object slots in this {@link AvailObject}.
	 * This does not include the fixed object slots.
	 *
	 * @return The number of variable object slots.
	 */
	final int variableObjectSlotsCount ()
	{
		return objectSlotsCount() - descriptor.numberOfFixedObjectSlots();
	}

	/**
	 * Store the {@linkplain AvailObject object} in the receiver at the given
	 * byte-index.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @return The object found at the specified slot in the receiver.
	 */
	abstract AvailObject slot (final @NotNull ObjectSlotsEnum e);

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	abstract void setSlot (
		final @NotNull ObjectSlotsEnum e,
		final @NotNull AvailObject anAvailObject);

	/**
	 * Extract the {@linkplain AvailObject object} at the specified slot of the
	 * receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @return The object found at the specified slot in the receiver.
	 */
	abstract AvailObject slot (
		final @NotNull ObjectSlotsEnum e,
		final int subscript);

	/**
	 * Store the {@linkplain AvailObject object} in the specified slot of the
	 * receiver.
	 *
	 * @param e An enumeration value that defines the field ordering.
	 * @param subscript The positive one-based subscript to apply.
	 * @param anAvailObject The object to store at the specified slot.
	 */
	abstract void setSlot (
		final @NotNull ObjectSlotsEnum e,
		final int subscript,
		final @NotNull AvailObject anAvailObject);

	/**
	 * Sanity check: ensure that the specified field is writable.
	 *
	 * @param e An {@code enum} value whose ordinal is the field position.
	 */
	final void checkWriteForField (final @NotNull AbstractSlotsEnum e)
	{
		descriptor.checkWriteForField(e);
	}

	/**
	 * Slice the current {@linkplain AvailObject object} into two objects, the
	 * left one (at the same starting address as the input), and the right
	 * one (a {@linkplain FillerDescriptor filler object} that nobody should
	 * ever create a pointer to). The new Filler can have zero post-header slots
	 * (i.e., just the header), but the left object must not, since it may turn
	 * into an {@linkplain IndirectionDescriptor indirection} some day and will
	 * require at least one slot for the target pointer.
	 *
	 * @param newIntegerSlotsCount
	 *        The number of integer slots in the left object.
	 */
	abstract void truncateWithFillerForNewIntegerSlotsCount (
		final int newIntegerSlotsCount);

	/**
	 * Slice the current {@linkplain AvailObject object} into two objects, the
	 * left one (at the same starting address as the input), and the right
	 * one (a {@linkplain FillerDescriptor filler object} that nobody should
	 * ever create a pointer to). The new Filler can have zero post-header slots
	 * (i.e., just the header), but the left object must not, since it may turn
	 * into an {@linkplain IndirectionDescriptor indirection} some day and will
	 * require at least one slot for the target pointer.
	 *
	 * @param newObjectSlotsCount The number of object slots in the left object.
	 */
	abstract void truncateWithFillerForNewObjectSlotsCount (
		final int newObjectSlotsCount);

	/**
	 * Construct a new {@link AbstractAvailObject}.
	 *
	 * @param descriptor
	 *        The {@linkplain AbstractDescriptor descriptor} that initially
	 *        describes the format and behavior of this object.
	 */
	protected AbstractAvailObject (
		final @NotNull AbstractDescriptor descriptor)
	{
		this.descriptor = descriptor;
	}
}
