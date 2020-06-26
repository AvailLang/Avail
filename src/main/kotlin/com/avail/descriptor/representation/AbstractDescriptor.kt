/*
 * AbstractDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.representation

import com.avail.annotations.EnumField
import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.HideFieldJustForPrinting
import com.avail.annotations.ThreadSafe
import com.avail.compiler.AvailCodeGenerator
import com.avail.compiler.scanning.LexingState
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import com.avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
import com.avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import com.avail.descriptor.fiber.FiberDescriptor.TraceFlag
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_MapBin
import com.avail.descriptor.maps.MapDescriptor.MapIterable
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.GrammaticalRestrictionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.AbstractNumberDescriptor
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import com.avail.descriptor.numbers.InfinityDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.A_ParsingPlanInProgress
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.representation.AvailObject.Companion.newIndexedDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.sets.SetDescriptor.SetIterator
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ByteStringDescriptor
import com.avail.descriptor.tuples.ByteTupleDescriptor
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor
import com.avail.descriptor.tuples.NybbleTupleDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor
import com.avail.descriptor.tuples.RepeatedElementTupleDescriptor
import com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.TreeTupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TwoByteStringDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FiberTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeTag
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import com.avail.dispatch.LookupTree
import com.avail.exceptions.AvailException
import com.avail.exceptions.AvailUnsupportedOperationException
import com.avail.exceptions.MalformedMessageException
import com.avail.exceptions.MethodDefinitionException
import com.avail.exceptions.SignatureException
import com.avail.exceptions.VariableGetException
import com.avail.exceptions.VariableSetException
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.execution.AvailLoader.LexicalScanner
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.io.TextInterface
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.performance.Statistic
import com.avail.serialization.SerializerOperation
import com.avail.utility.Casts.cast
import com.avail.utility.Casts.nullableCast
import com.avail.utility.Strings.newlineTab
import com.avail.utility.json.JSONWriter
import com.avail.utility.visitor.AvailSubobjectVisitor
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Stream
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min

/**
 * [AbstractDescriptor] is the base descriptor type.  An [AvailObject] contains
 * a descriptor, to which it delegates nearly all of its behavior.  That allows
 * interesting operations like effective type mutation (within a language that
 * does not support it directly, such as Java).  It also allows multiple
 * representations of equivalent objects, such as more than one representation
 * for the tuple `<1,2,3>`.  It can be represented as an AvailObject using
 * either an [ObjectTupleDescriptor], a [ByteTupleDescriptor], a
 * [NybbleTupleDescriptor], or a [TreeTupleDescriptor].  It could even be an
 * [IndirectionDescriptor] if there is another object that already represents
 * this tuple.
 *
 * In particular, [AbstractDescriptor] is abstract and has two children, the
 * class [Descriptor] and the class [IndirectionDescriptor], the latter of which
 * has no classes.  When a new operation is added in an ordinary descriptor
 * class (below `Descriptor`), it should be added with an `@Override`
 * annotation.  A quick fix on that error allows an implementation to be
 * generated in AbstractDescriptor, which should be converted manually to an
 * abstract method.  That will make both `Descriptor` and
 * `IndirectionDescriptor` (and all subclasses of `Descriptor` except the one in
 * which the new method first appeared) to indicate an error, in that they need
 * to implement this method.  A quick fix can add it to `Descriptor`, after
 * which it can be tweaked to indicate a runtime error.  Another quick fix adds
 * it to `IndirectionDescriptor`, and copying nearby implementations leads it to
 * invoke the non "o_" method in [AvailObject].  This will show up as an error,
 * and one more quick fix can generate the corresponding method in `AvailObject`
 * whose implementation, like methods near it, extracts the
 * [AvailObject.descriptor] and invokes upon it the original message (that
 * started with "o_"), passing `this` as the first argument.  Code generation
 * will eventually make this relatively onerous task more tractable and less
 * error prone.
 *
 * @property mutability
 *   The [mutability][Mutability] of my instances.
 * @property typeTag
 *   Every descriptor has this field, and clients can access it directly to
 *   quickly determine the basic type of any value having that descriptor. This
 *   is purely an optimization for fast type checking and dispatching.
 *
 * @constructor
 *
 * Construct a new `AbstractDescriptor`.
 *
 * @param mutability
 *   The [Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines
 *   this object's object slots layout, or null if there are no object
 *   slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines
 *   this object's object slots layout, or null if there are no integer
 *   slots.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class AbstractDescriptor protected constructor (
	val mutability: Mutability,
	val typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?)
{
	/**
	 * Used for quickly checking object fields when
	 * [AvailObjectRepresentation.shouldCheckSlots] is enabled.
	 */
	val debugObjectSlots: Array<Array<ObjectSlotsEnum>?>

	/**
	 * Used for quickly checking integer fields when
	 * [AvailObjectRepresentation.shouldCheckSlots] is enabled.
	 */
	val debugIntegerSlots: Array<Array<IntegerSlotsEnum>?>

	/**
	 * Whether an [object][AvailObject] using this descriptor can have more than
	 * the minimum number of object slots. Populated automatically by the
	 * constructor, based on the presence of an underscore at the end of its
	 * final [ObjectSlotsEnum] name.
	 */
	private val hasVariableObjectSlots: Boolean

	/**
	 * Whether an [object][AvailObject] using this descriptor can have more than
	 * the minimum number of integer slots. Populated automatically by the
	 * constructor, based on the presence of an underscore at the end of its
	 * final [IntegerSlotsEnum] name.
	 */
	private val hasVariableIntegerSlots: Boolean

	/**
	 * The minimum number of object slots an [AvailObject] can have if it uses
	 * this [descriptor][AbstractDescriptor]. Does not include indexed slots
	 * possibly at the end. Populated automatically by the constructor.
	 */
	private val numberOfFixedObjectSlots: Int

	/**
	 * The minimum number of integer slots an [AvailObject] can have if it uses
	 * this descriptor. Does not include indexed slots possibly at the end.
	 * Populated automatically by the constructor.
	 */
	private val numberOfFixedIntegerSlots: Int

	init
	{
		val objectSlots: Array<out ObjectSlotsEnum> =
			if (objectSlotsEnumClass !== null) objectSlotsEnumClass.enumConstants
			else arrayOf()
		@Suppress("ConstantConditionIf")
		debugObjectSlots =
			if (AvailObjectRepresentation.shouldCheckSlots)
				arrayOfNulls(max(objectSlots.size, 1))
			else
				emptyDebugObjectSlots
		hasVariableObjectSlots =
			objectSlots.isNotEmpty()
				&& objectSlots[objectSlots.size - 1].fieldName().endsWith("_")
		numberOfFixedObjectSlots =
			objectSlots.size - if (hasVariableObjectSlots) 1 else 0
		val integerSlots: Array<out IntegerSlotsEnum> =
			if (integerSlotsEnumClass !== null)
				integerSlotsEnumClass.enumConstants
			else
				arrayOf()
		@Suppress("ConstantConditionIf")
		debugIntegerSlots =
			if (AvailObjectRepresentation.shouldCheckSlots)
				arrayOfNulls(max(integerSlots.size, 1))
			else
				emptyDebugIntegerSlots
		hasVariableIntegerSlots =
			integerSlots.isNotEmpty()
				&& integerSlots[integerSlots.size - 1].fieldName().endsWith("_")
		numberOfFixedIntegerSlots =
			integerSlots.size - if (hasVariableIntegerSlots()) 1 else 0
	}

	/**
	 * A non-enum [ObjectSlotsEnum] that can be instantiated at will.
	 * Useful for customizing debugger views of objects.
	 *
	 * @property
	 *   The slot name.
	 *
	 * @constructor
	 *
	 * Create a new artificial slot with the given name.
	 *
	 * @param name
	 *   The name of the slot.
	 */
	class DebuggerObjectSlots (val name: String) : ObjectSlotsEnum

	/**
	 * Are [objects][AvailObject] using this [descriptor][AbstractDescriptor]
	 * [mutable][Mutability.MUTABLE]?
	 *
	 * @return
	 *   `true` if the described object is mutable, `false` otherwise.
	 */
	@get:ReferencedInGeneratedCode
	val isMutable get () = mutability === Mutability.MUTABLE

	/**
	 * Answer the [mutable][Mutability.MUTABLE] version of this descriptor.
	 *
	 * @return
	 *   A mutable descriptor equivalent to the receiver.
	 */
	abstract fun mutable (): AbstractDescriptor

	/**
	 * Answer the [immutable][Mutability.IMMUTABLE] version of this descriptor.
	 *
	 * @return
	 *   An immutable descriptor equivalent to the receiver.
	 */
	abstract fun immutable (): AbstractDescriptor

	/**
	 * Answer the [shared][Mutability.SHARED] version of this descriptor.
	 *
	 * @return
	 *   A shared descriptor equivalent to the receiver.
	 */
	abstract fun shared (): AbstractDescriptor

	/**
	 * Are [objects][AvailObject] using this descriptor
	 * [shared][Mutability.SHARED]?
	 *
	 * @return
	 *   `true` if the described object is shared, `false` otherwise.
	 */
	val isShared get () = mutability === Mutability.SHARED

	/**
	 * Answer the minimum number of object slots an [AvailObject] can have if it
	 * uses this descriptor. Does not include indexed slots possibly at the end.
	 * Populated automatically by the constructor.
	 *
	 * @return
	 *   The minimum number of object slots featured by an object using this
	 *   descriptor.
	 */
	fun numberOfFixedObjectSlots () = numberOfFixedObjectSlots

	/**
	 * Answer the minimum number of integer slots an [AvailObject] can have if
	 * it uses this descriptor. Does not include indexed slots possibly at the
	 * end. Populated automatically by the constructor.
	 *
	 * @return
	 *   The minimum number of integer slots featured by an object using this
	 *   descriptor.
	 */
	fun numberOfFixedIntegerSlots () = numberOfFixedIntegerSlots

	/**
	 * Can an [object][AvailObject] using this descriptor have more than the
	 * [minimum&#32;number&#32;of&#32;object&#32;slots][numberOfFixedObjectSlots]?
	 *
	 * @return
	 *   `true` if it is permissible for an [object][AvailObject] using this
	 *   descriptor to have more than the
	 *   [minimum&#32;number&#32;of&#32;object&#32;slots][numberOfFixedObjectSlots],
	 *   `false` otherwise.
	 */
	fun hasVariableObjectSlots () = hasVariableObjectSlots

	/**
	 * Can an [object][AvailObject] using this descriptor have more than the
	 * [minimum&#32;number&#32;of&#32;integer&#32;slots][numberOfFixedIntegerSlots]?
	 *
	 * @return
	 *   `true` if it is permissible for an [object][AvailObject] using this
	 *   descriptor to have more than the
	 *   [minimum&#32;number&#32;of&#32;integer&#32;slots][numberOfFixedIntegerSlots],
	 *   `false` otherwise.
	 */
	fun hasVariableIntegerSlots () = hasVariableIntegerSlots

	/**
	 * Describe the object for the IntelliJ debugger.
	 *
	 * @param self
	 *   The [AvailObject] to describe.
	 * @return
	 *   An array of [AvailObjectFieldHelper]s that describe the logical parts
	 *   of the given object.
	 */
	open fun o_DescribeForDebugger (
		self: AvailObject): Array<AvailObjectFieldHelper>
	{
		val cls: Class<Descriptor> = cast(this@AbstractDescriptor.javaClass)
		val loader = cls.classLoader
		var enumClass: Class<Enum<*>>? = nullableCast(
			try
			{
				loader.loadClass("${cls.canonicalName}\$IntegerSlots")
			}
			catch (e: ClassNotFoundException)
			{
				null
			})
		val fields = mutableListOf<AvailObjectFieldHelper>()
		if (enumClass !== null)
		{
			val slots = enumClass.enumConstants
			for (i in 0 until numberOfFixedIntegerSlots())
			{
				val slot = slots[i]
				if (getAnnotation(
						slot,
						HideFieldInDebugger::class.java)
					=== null)
				{
					fields.add(
						AvailObjectFieldHelper(
							self,
							slot as IntegerSlotsEnum,
							-1,
							AvailIntegerValueHelper(
								self.slot(slot as IntegerSlotsEnum))))
				}
			}
			val slot = slots[slots.size - 1]
			if (getAnnotation(slot, HideFieldInDebugger::class.java) === null)
			{
				for (i in numberOfFixedIntegerSlots()
					until self.integerSlotsCount())
				{
					val subscript = i - numberOfFixedIntegerSlots() + 1
					fields.add(
						AvailObjectFieldHelper(
							self,
							slot as IntegerSlotsEnum,
							subscript,
							AvailIntegerValueHelper(
								self.slot(
									slot as IntegerSlotsEnum, subscript))))
				}
			}
		}
		enumClass = nullableCast(
			try
			{
				loader.loadClass("${cls.canonicalName}\$ObjectSlots")
			}
			catch (e: ClassNotFoundException)
			{
				null
			})
		if (enumClass !== null)
		{
			val slots: Array<Enum<*>> = enumClass.enumConstants
			for (i in 0 until numberOfFixedObjectSlots())
			{
				val slot = slots[i]
				if (getAnnotation(
						slot,
						HideFieldInDebugger::class.java)
					=== null)
				{
					fields.add(
						AvailObjectFieldHelper(
							self,
							slot as ObjectSlotsEnum,
							-1,
							self.slot(slot as ObjectSlotsEnum)))
				}
			}
			val slot = slots[slots.size - 1]
			if (getAnnotation(slot, HideFieldInDebugger::class.java) === null)
			{
				for (i in numberOfFixedObjectSlots()
					until self.objectSlotsCount())
				{
					val subscript = i - numberOfFixedObjectSlots() + 1
					fields.add(
						AvailObjectFieldHelper(
							self,
							slot as ObjectSlotsEnum,
							subscript,
							self.slot(slot as ObjectSlotsEnum, subscript)))
				}
			}
		}
		return fields.toTypedArray()
	}

	/**
	 * Answer whether the field at the given offset is allowed to be modified
	 * even in an immutable object.
	 *
	 * @param e
	 *   The byte offset of the field to check.
	 * @return
	 *   Whether the specified field can be written even in an immutable object.
	 */
	protected open fun allowsImmutableToMutableReferenceInField (
		e: AbstractSlotsEnum
	) = false

	/**
	 * Answer how many levels of printing to allow before elision.
	 *
	 * @return
	 *   The number of levels.
	 */
	open fun maximumIndent () = 12

	/**
	 * Ensure that the specified field is writable.
	 *
	 * @param e
	 *   An `enum` value whose ordinal is the field position.
	 */
	fun checkWriteForField (e: AbstractSlotsEnum)
	{
		assert(isMutable || allowsImmutableToMutableReferenceInField(e))
	}

	/**
	 * Create a new [object][AvailObject] whose [descriptor][AbstractDescriptor]
	 * is the receiver, and which has the specified number of indexed (variable)
	 * slots.
	 *
	 * @param indexedSlotCount
	 *   The number of variable slots to include.
	 * @return
	 *   The new uninitialized [object][AvailObject].
	 */
	fun create (indexedSlotCount: Int) =
		newIndexedDescriptor(indexedSlotCount, this)

	/**
	 * Create a new [object][AvailObject] whose [descriptor][AbstractDescriptor]
	 * is the receiver, and which has no indexed (variable) slots.
	 *
	 * @return
	 *   The new uninitialized [object][AvailObject].
	 */
	fun create (): AvailObject = newIndexedDescriptor(0, this)

	/**
	 * Print the [object][AvailObject] to the [StringBuilder]. By default show
	 * it as the descriptor's name and a line-by-line list of fields. If the
	 * indent is beyond the [maximumIndent], indicate it's too deep without
	 * recursing. If the object is in the specified recursion list, indicate a
	 * recursive print and return.
	 *
	 * @param self
	 *   The object to print (its descriptor is me).
	 * @param builder
	 *   Where to print the object.
	 * @param recursionMap
	 *   Which ancestor objects are currently being printed.
	 * @param indent
	 *   What level to indent subsequent lines.
	 */
	@ThreadSafe
	open fun printObjectOnAvoidingIndent (
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int) =
	with(builder) {
		append('a')
		val className = this@AbstractDescriptor.javaClass.simpleName
		val shortenedName = className.substring(
			0,
			className.length - 10)
		when (shortenedName.codePointAt(0))
		{
			'A'.toInt(),
			'E'.toInt(),
			'I'.toInt(),
			'O'.toInt(),
			'U'.toInt() ->
				append('n')
			else -> {}
		}
		append(' ')
		append(shortenedName)
		if (isMutable)
		{
			// Circled Latin capital letter M.
			append('\u24C2')
		}
		else if (isShared)
		{
			// Circled Latin capital letter S.
			append('\u24C8')
		}
		val cls: Class<Descriptor> = cast(this@AbstractDescriptor.javaClass)
		val loader = cls.classLoader
		val intEnumClass: Class<out IntegerSlotsEnum>? = nullableCast(
			try
			{
				loader.loadClass("${cls.canonicalName}\$IntegerSlots")
			}
			catch (e: ClassNotFoundException)
			{
				null
			})
		val intSlots: Array<out IntegerSlotsEnum> =
			if (intEnumClass !== null) intEnumClass.enumConstants
			else arrayOf()
		run {
			var i = 1
			val limit = self.integerSlotsCount()
			while (i <= limit)
			{
				val ordinal = min(i, intSlots.size) - 1
				val slot = intSlots[ordinal]
				if (getAnnotation(
						slot as Enum<*>,
						HideFieldInDebugger::class.java) === null
					&& getAnnotation(
						slot as Enum<*>,
						HideFieldJustForPrinting::class.java) === null)
				{
					newlineTab(builder, indent)
					val slotName = slot.fieldName()
					val bitFields = bitFieldsFor(slot)
					if (slotName[slotName.length - 1] == '_')
					{
						val subscript = i - intSlots.size + 1
						append(slotName, 0, slotName.length - 1)
						append('[')
						append(subscript)
						append(']')
					}
					else
					{
						val value = self.slot(slot)
						if (bitFields.isEmpty())
						{
							append(slotName)
							append(" = ")
							append(value)
						}
						else
						{
							describeIntegerSlot(
								self, value, slot, bitFields, builder)
						}
					}
				}
				i++
			}
		}
		val objectEnumClass: Class<out ObjectSlotsEnum>? = nullableCast(
			try
			{
				loader.loadClass("${cls.canonicalName}\$ObjectSlots")
			}
			catch (e: ClassNotFoundException)
			{
				null
			})
		val objectSlots: Array<out ObjectSlotsEnum> =
			if (objectEnumClass !== null) objectEnumClass.enumConstants
			else arrayOf()
		var i = 1
		val limit = self.objectSlotsCount()
		while (i <= limit)
		{
			val ordinal = min(i, objectSlots.size) - 1
			val slot = objectSlots[ordinal]
			if (getAnnotation(
					slot as Enum<*>,
					HideFieldInDebugger::class.java) === null
				&& getAnnotation(
					slot as Enum<*>,
					HideFieldJustForPrinting::class.java) === null)
			{
				newlineTab(builder, indent)
				val slotName = slot.fieldName()
				if (slotName[slotName.length - 1] == '_')
				{
					val subscript = i - objectSlots.size + 1
					append(slotName, 0, slotName.length - 1)
					append('[')
					append(subscript)
					append("] = ")
					self.slot(slot, subscript).printOnAvoidingIndent(
						builder,
						recursionMap,
						indent + 1)
				}
				else
				{
					append(slotName)
					append(" = ")
					self.slot(slot).printOnAvoidingIndent(
						builder,
						recursionMap,
						indent + 1)
				}
			}
			i++
		}
	}

	override fun toString () = buildString {
		val thisClass = this@AbstractDescriptor.javaClass
		append(thisClass.simpleName)
		val supers = ArrayList<Class<*>>()
		var cls: Class<*> = thisClass
		while (cls != Any::class.java)
		{
			supers.add(0, cls) // top-down
			cls = cls.superclass
		}
		var fieldCount = 0
		for (sup in supers)
		{
			for (f in sup.declaredFields)
			{
				if (!Modifier.isStatic(f.modifiers))
				{
					fieldCount++
					if (fieldCount == 1)
					{
						append("(")
					}
					else
					{
						append(", ")
					}
					append(f.name)
					append("=")
					try
					{
						append(f[this])
					}
					catch (e: IllegalArgumentException)
					{
						append("(inaccessible)")
					}
					catch (e: IllegalAccessException)
					{
						append("(inaccessible)")
					}
				}
			}
		}
		if (fieldCount > 0)
		{
			append(")")
		}
		return toString()
	}

	/**
	 * Throw an
	 * [unsupported&#32;operation&#32;exception][AvailUnsupportedOperationException]
	 * suitable to be thrown by the sender.
	 *
	 * The exception indicates that the receiver does not meaningfully implement
	 * the method that immediately invoked this.  This is a strong indication
	 * that the wrong kind of object is being used somewhere.
	 *
	 * @throws AvailUnsupportedOperationException
	 */
	fun unsupportedOperation (): Nothing
	{
		val callerName =
			try
			{
				throw Exception("just want the caller's frame")
			}
			catch (e: Exception)
			{
				e.stackTrace[1].methodName
			}
		throw AvailUnsupportedOperationException(javaClass, callerName)
	}

	/**
	 * This read-only property can be used in place of [unsupportedOperation].
	 * Using the getter produces almost the same diagnostic stack trace when
	 * executed, but is a much shorter expression.
	 */
	val unsupported: Nothing
		inline get() = unsupportedOperation()

	/**
	 * Throw an
	 * [unsupported&#32;operation&#32;exception][AvailUnsupportedOperationException]
	 * suitable to be thrown by the sender.
	 *
	 * The exception indicates that the receiver does not meaningfully implement
	 * the method that immediately invoked this.  This is a strong indication
	 * that the wrong kind of object is being used somewhere.
	 *
	 * This is a variant on [unsupported] to support Java implementations of
	 * [AbstractDescriptor] until porting of the hierarchy is finished. It
	 * should be thrown by Java callers, which cannot correctly reason about
	 * flow around non-returning functions.
	 *
	 * @return
	 *   Never returns anything; always throws.
	 * @throws AvailUnsupportedOperationException
	 *   Always.
	 */
	fun unsupportedOperationException (): AvailUnsupportedOperationException
	{
		val callerName =
			try
			{
				throw Exception("just want the caller's frame")
			}
			catch (e: Exception)
			{
				e.stackTrace[1].methodName
			}
		throw AvailUnsupportedOperationException(javaClass, callerName)
	}

	/**
	 * Answer whether the [argument&#32;types][AvailObject.argsTupleType]
	 * supported by the specified [function&#32;type][FunctionTypeDescriptor]
	 * are acceptable argument types for invoking a
	 * [function][FunctionDescriptor] whose type is `self`.
	 *
	 * @param self
	 *   A function type.
	 * @param functionType
	 *   A function type.
	 * @return
	 *   `true` if the arguments of self are, pairwise, more general than those
	 *   of `functionType`, `false` otherwise.
	 * @see AvailObject.acceptsArgTypesFromFunctionType
	 */
	abstract fun o_AcceptsArgTypesFromFunctionType (
		self: AvailObject,
		functionType: A_Type): Boolean

	/**
	 * Answer whether these are acceptable [argument&#32;types][TypeDescriptor]
	 * for invoking a [function][FunctionDescriptor] whose type is `self`.
	 *
	 * @param self
	 *   The receiver.
	 * @param argTypes
	 *   A list containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than those within the `argTypes` list, `false` otherwise.
	 * @see AvailObject.acceptsListOfArgTypes
	 */
	abstract fun o_AcceptsListOfArgTypes (
		self: AvailObject,
		argTypes: List<A_Type>): Boolean

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] whose type is `self`.
	 *
	 * @param self
	 *   The receiver.
	 * @param argValues
	 *   A list containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the values within the `argValues` list, `false`
	 *   otherwise.
	 * @see AvailObject.acceptsListOfArgValues
	 */
	abstract fun o_AcceptsListOfArgValues (
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean

	/**
	 * Answer whether these are acceptable [argument&#32;types][TypeDescriptor]
	 * for invoking a [function][FunctionDescriptor] that is an instance of
	 * self. There may be more entries in the [tuple][TupleDescriptor] than are
	 * required by the [function&#32;type][FunctionTypeDescriptor].
	 *
	 * @param self
	 *   The receiver.
	 * @param argTypes
	 *   A tuple containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the corresponding elements of the `argTypes` tuple, `false`
	 *   otherwise.
	 * @see AvailObject.acceptsTupleOfArgTypes
	 */
	abstract fun o_AcceptsTupleOfArgTypes (
		self: AvailObject,
		argTypes: A_Tuple): Boolean

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] that is an instance of self. There may be
	 * more entries in the [tuple][TupleDescriptor] than are required by the
	 * [function&#32;type][FunctionTypeDescriptor].
	 *
	 * @param self
	 *   The receiver.
	 * @param arguments
	 *   A tuple containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the corresponding elements of the `arguments` tuple,
	 *   `false` otherwise.
	 * @see AvailObject.acceptsTupleOfArguments
	 */
	abstract fun o_AcceptsTupleOfArguments (
		self: AvailObject,
		arguments: A_Tuple): Boolean

	/**
	 * Record the fact that the given [L2Chunk] depends on the object not
	 * changing in some way peculiar to the kind of object.  Most typically,
	 * this is applied to [A_Method]s, triggering invalidation if
	 * [A_Definition]s are added to or removed from the method, but at some
	 * point we may also support slowly-changing variables.
	 *
	 * @param self
	 *   The object responsible for invalidating dependent chunks when it
	 *   changes.
	 * @param chunk
	 *   A chunk that should be invalidated if the object changes.
	 * @see AvailObject.addDependentChunk
	 */
	abstract fun o_AddDependentChunk (self: AvailObject, chunk: L2Chunk)

	/**
	 * Add a [definition][DefinitionDescriptor] to the receiver. Causes
	 * dependent chunks to be invalidated.
	 *
	 * @param self
	 *   The receiver.
	 * @param definition
	 *   The definition to be added.
	 * @throws SignatureException
	 *   If the definition could not be added.
	 * @see AvailObject.methodAddDefinition
	 */
	@Throws(SignatureException::class)
	abstract fun o_MethodAddDefinition (
		self: AvailObject,
		definition: A_Definition)

	/**
	 * Add a [grammatical&#32;restriction][GrammaticalRestrictionDescriptor] to
	 * the receiver.
	 *
	 * @param self
	 *   The receiver, a [message&#32;bundle][MessageBundleDescriptor].
	 * @param grammaticalRestriction
	 *   The grammatical restriction to be added.
	 * @see A_Bundle.addGrammaticalRestriction
	 */
	abstract fun o_ModuleAddGrammaticalRestriction (
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction)

	/**
	 * Add the [operands][AvailObject] and answer the result.
	 *
	 * This method should only be called from
	 * [plusCanDestroy][AvailObject.plusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param sign
	 *   The [sign][Sign] of the infinity.
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 * @see AvailObject.addToInfinityCanDestroy
	 */
	abstract fun o_AddToInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number

	/**
	 * Add the [operands][AvailObject] and answer the result.
	 *
	 * This method should only be called from
	 * [plusCanDestroy][AvailObject.plusCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 * @see AvailObject.addToIntegerCanDestroy
	 */
	abstract fun o_AddToIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number

	/**
	 * @param self
	 * @param grammaticalRestriction
	 * @see AvailObject.moduleAddGrammaticalRestriction
	 */
	abstract fun o_AddGrammaticalRestriction (
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction)

	/**
	 * @param self
	 * @param definition
	 * @see AvailObject.moduleAddDefinition
	 */
	abstract fun o_ModuleAddDefinition (
		self: AvailObject,
		definition: A_Definition)

	abstract fun o_AddDefinitionParsingPlan (
		self: AvailObject,
		plan: A_DefinitionParsingPlan)

	abstract fun o_AddImportedName (self: AvailObject, trueName: A_Atom)

	abstract fun o_AddImportedNames (self: AvailObject, trueNames: A_Set)

	abstract fun o_IntroduceNewName (self: AvailObject, trueName: A_Atom)

	abstract fun o_AddPrivateName (self: AvailObject, trueName: A_Atom)

	abstract fun o_BinElementAt (self: AvailObject, index: Int): AvailObject

	abstract fun o_SetBreakpointBlock (self: AvailObject, value: AvailObject)

	abstract fun o_BuildFilteredBundleTree (self: AvailObject): A_BundleTree

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of
	 * another object. The size of the subrange of both objects is determined by
	 * the index range supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *   The other object used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the other object's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithStartingAt
	 */
	abstract fun o_CompareFromToWithStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [tuple][TupleDescriptor]. The size of the subrange of both objects
	 * is determined by the index range supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *   The tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see A_Tuple.compareFromToWithAnyTupleStartingAt
	 */
	abstract fun o_CompareFromToWithAnyTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [byte&#32;string][ByteStringDescriptor]. The size of the subrange
	 * of both objects is determined by the index range supplied for the
	 * receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *   The byte string used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the byte string's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithByteStringStartingAt
	 */
	abstract fun o_CompareFromToWithByteStringStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [byte&#32;tuple][ByteTupleDescriptor]. The size of the subrange of
	 * both objects is determined by the index range supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *   The byte tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the byte tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithByteTupleStartingAt
	 */
	abstract fun o_CompareFromToWithByteTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [integer&#32;interval&#32;tuple][IntegerIntervalTupleDescriptor].
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anIntegerIntervalTuple
	 *   The integer interval tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the integer interval tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithByteTupleStartingAt
	 */
	abstract fun o_CompareFromToWithIntegerIntervalTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given
	 * [small&#32;integer&#32;interval&#32;tuple][SmallIntegerIntervalTupleDescriptor].
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aSmallIntegerIntervalTuple
	 *   The small integer interval tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the small integer interval tuple's
	 *   subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithByteTupleStartingAt
	 */
	abstract fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [repeated&#32;element&#32;tuple][RepeatedElementTupleDescriptor].
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aRepeatedElementTuple
	 *   The repeated element tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the repeated element tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithByteTupleStartingAt
	 */
	abstract fun o_CompareFromToWithRepeatedElementTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [nybble&#32;tuple][NybbleTupleDescriptor]. The size of the subrange
	 * of both objects is determined by the index range supplied for the
	 * receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *   The nybble tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the nybble tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithNybbleTupleStartingAt
	 */
	abstract fun o_CompareFromToWithNybbleTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [object&#32;tuple][ObjectTupleDescriptor]. The size of the subrange
	 * of both objects is determined by the index range supplied for the
	 * receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *   The object tuple used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the object tuple's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithObjectTupleStartingAt
	 */
	abstract fun o_CompareFromToWithObjectTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean

	/**
	 * Compare a subrange of the [receiver][AvailObject] with a subrange of the
	 * given [two-byte&#32;string][TwoByteStringDescriptor]. The size of the
	 * subrange of both objects is determined by the index range supplied for
	 * the receiver.
	 *
	 * @param self
	 *   The receiver.
	 * @param startIndex1
	 *   The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *   The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *   The two-byte string used in the comparison.
	 * @param startIndex2
	 *   The inclusive lower bound of the two-byte string's subrange.
	 * @return
	 *   `true` if the contents of the subranges match exactly, `false`
	 *   otherwise.
	 * @see AvailObject.compareFromToWithTwoByteStringStartingAt
	 */
	abstract fun o_CompareFromToWithTwoByteStringStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int): Boolean

	abstract fun o_ComputeHashFromTo (
		self: AvailObject,
		start: Int,
		end: Int): Int

	/**
	 * Compute this object's [TypeTag], having failed to extract it from the
	 * descriptor directly in [AvailObjectRepresentation.typeTag].
	 *
	 * @param self
	 * @return
	 */
	abstract fun o_ComputeTypeTag (self: AvailObject): TypeTag

	abstract fun o_ConcatenateTuplesCanDestroy (
		self: AvailObject,
		canDestroy: Boolean): A_Tuple

	abstract fun o_SetContinuation (
		self: AvailObject,
		value: A_Continuation)

	abstract fun o_CopyTupleFromToCanDestroy (
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple

	abstract fun o_CouldEverBeInvokedWith (
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean

	/**
	 * Answer a fiber's internal debug log.
	 *
	 * @param self
	 *   The [A_Fiber].
	 * @return
	 *   The fiber's debug log, a [StringBuilder].
	 */
	abstract fun o_DebugLog (self: AvailObject): StringBuilder

	/**
	 * Divide the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [divideIntoIntegerCanDestroy][A_Number.divideIntoIntegerCanDestroy] or
	 * [divideIntoInfinityCanDestroy][AvailObject.divideIntoInfinityCanDestroy],
	 * where actual implementations of the division operation should reside.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return The [result][AvailObject] of dividing the operands.
	 * @see AvailObject.divideCanDestroy
	 */
	abstract fun o_DivideCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number

	/**
	 * Divide an infinity with the given [sign][Sign] by the
	 * [object][AvailObject] and answer the [result][AvailObject].
	 *
	 * This method should only be called from
	 * [divideCanDestroy][AvailObject.divideCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   The divisor, an integral numeric.
	 * @param sign
	 *   The sign of the infinity.
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return The [result][AvailObject] of dividing the operands.
	 * @see AvailObject.divideIntoInfinityCanDestroy
	 */
	abstract fun o_DivideIntoInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number

	/**
	 * Divide the [operands][AvailObject] and answer the result.
	 *
	 * This method should only be called from
	 * [divideCanDestroy][AvailObject.divideCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   The divisor, an integral numeric.
	 * @param anInteger
	 *   The dividend, an [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return The result of dividing the operands.
	 * @see AvailObject.divideIntoIntegerCanDestroy
	 */
	abstract fun o_DivideIntoIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number

	abstract fun o_SetExecutionState (self: AvailObject, value: ExecutionState)

	abstract fun o_ExtractNybbleFromTupleAt (
		self: AvailObject,
		index: Int): Byte

	abstract fun o_FilterByTypes (
		self: AvailObject,
		argTypes: List<A_Type>): List<A_Definition>

	/**
	 * Answer whether the [receiver][AvailObject] contains the specified
	 * element.
	 *
	 * @param self
	 *   The receiver.
	 * @param elementObject
	 *   The element.
	 * @return
	 *   `true` if the receiver contains the element, `false` otherwise.
	 * @see AvailObject.hasElement
	 */
	abstract fun o_HasElement (
		self: AvailObject,
		elementObject: A_BasicObject): Boolean

	abstract fun o_HashFromTo (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): Int

	abstract fun o_SetHashOrZero (
		self: AvailObject,
		value: Int)

	abstract fun o_HasKey (
		self: AvailObject,
		keyObject: A_BasicObject): Boolean

	abstract fun o_DefinitionsAtOrBelow (
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): List<A_Definition>

	abstract fun o_IncludesDefinition (
		self: AvailObject,
		definition: A_Definition): Boolean

	abstract fun o_SetInterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag)

	abstract fun o_CountdownToReoptimize (self: AvailObject, value: Int)

	abstract fun o_IsSubsetOf (self: AvailObject, another: A_Set): Boolean

	abstract fun o_IsSubtypeOf (self: AvailObject, aType: A_Type): Boolean

	abstract fun o_IsSupertypeOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): Boolean

	abstract fun o_IsSupertypeOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): Boolean

	abstract fun o_IsSupertypeOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean

	abstract fun o_IsSupertypeOfFiberType (
		self: AvailObject,
		aType: A_Type): Boolean

	abstract fun o_IsSupertypeOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): Boolean

	abstract fun o_IsSupertypeOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean

	abstract fun o_IsSupertypeOfMapType (
		self: AvailObject,
		aMapType: AvailObject): Boolean

	abstract fun o_IsSupertypeOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): Boolean

	abstract fun o_IsSupertypeOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): Boolean

	abstract fun o_IsSupertypeOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): Boolean

	abstract fun o_IsSupertypeOfPrimitiveTypeEnum (
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): Boolean

	abstract fun o_IsSupertypeOfSetType (
		self: AvailObject,
		aSetType: A_Type): Boolean

	abstract fun o_IsSupertypeOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): Boolean

	abstract fun o_IsSupertypeOfEnumerationType (
		self: AvailObject,
		anEnumerationType: A_Type): Boolean

	abstract fun o_LevelTwoChunkOffset (
		self: AvailObject,
		chunk: L2Chunk,
		offset: Int)

	abstract fun o_LiteralAt (self: AvailObject, index: Int): AvailObject

	abstract fun o_FrameAt (self: AvailObject, index: Int): AvailObject

	abstract fun o_FrameAtPut (
		self: AvailObject,
		index: Int,
		value: AvailObject): AvailObject

	abstract fun o_LocalTypeAt (self: AvailObject, index: Int): A_Type

	abstract fun o_ConstantTypeAt (self: AvailObject, index: Int): A_Type

	@Throws(MethodDefinitionException::class)
	abstract fun o_LookupByTypesFromTuple (
		self: AvailObject,
		argumentTypeTuple: A_Tuple): A_Definition

	@Throws(MethodDefinitionException::class)
	abstract fun o_LookupByValuesFromList (
		self: AvailObject,
		argumentList: List<A_BasicObject>): A_Definition

	abstract fun o_MapAt (
		self: AvailObject,
		keyObject: A_BasicObject): AvailObject

	abstract fun o_MapAtPuttingCanDestroy (
		self: AvailObject,
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Map

	abstract fun o_MapAtReplacingCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		canDestroy: Boolean): A_Map

	abstract fun o_MapWithoutKeyCanDestroy (
		self: AvailObject,
		keyObject: A_BasicObject,
		canDestroy: Boolean): A_Map

	/**
	 * Difference the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [subtractFromIntegerCanDestroy][A_Number.subtractFromIntegerCanDestroy]
	 * or
	 * [subtractFromInfinityCanDestroy][AvailObject.subtractFromInfinityCanDestroy],
	 * where actual implementations of the subtraction operation should reside.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of differencing the operands.
	 * @see AvailObject.minusCanDestroy
	 */
	abstract fun o_MinusCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number

	/**
	 * Multiply the [operands][AvailObject] and answer the result.
	 *
	 * This method should only be called from
	 * [timesCanDestroy][AvailObject.timesCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param sign
	 *   The [Sign] of the [infinity][InfinityDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of multiplying the operands. If the
	 *   [operands][AvailObject] were [zero][IntegerDescriptor.zero] and
	 *   [infinity][InfinityDescriptor].
	 * @see AvailObject.multiplyByInfinityCanDestroy
	 */
	abstract fun o_MultiplyByInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number

	/**
	 * Multiply the [operands][AvailObject] and answer the result.
	 *
	 * This method should only be called from
	 * [timesCanDestroy][AvailObject.timesCanDestroy]. It exists for
	 * double-dispatch only.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of multiplying the operands.
	 * @see AvailObject.multiplyByIntegerCanDestroy
	 */
	abstract fun o_MultiplyByIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number

	abstract fun o_NameVisible (self: AvailObject, trueName: A_Atom): Boolean

	abstract fun o_OptionallyNilOuterVar (
		self: AvailObject,
		index: Int): Boolean

	abstract fun o_OuterTypeAt (self: AvailObject, index: Int): A_Type

	abstract fun o_OuterVarAt (self: AvailObject, index: Int): AvailObject

	abstract fun o_OuterVarAtPut (
		self: AvailObject,
		index: Int,
		value: AvailObject)

	/**
	 * Add the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [addToIntegerCanDestroy][A_Number.addToIntegerCanDestroy] or
	 * [addToInfinityCanDestroy][AvailObject.addToInfinityCanDestroy], where
	 * actual implementations of the addition operation should reside.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 */
	abstract fun o_PlusCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_SetPriority (self: AvailObject, value: Int)

	abstract fun o_SetFiberGlobals (self: AvailObject, globals: A_Map)

	abstract fun o_RawByteForCharacterAt (self: AvailObject, index: Int): Short

	abstract fun o_RawShortForCharacterAt (self: AvailObject, index: Int): Int

	abstract fun o_RawShortForCharacterAtPut (
		self: AvailObject,
		index: Int,
		anInteger: Int)

	abstract fun o_RawSignedIntegerAt (self: AvailObject, index: Int): Int

	abstract fun o_RawSignedIntegerAtPut (
		self: AvailObject,
		index: Int,
		value: Int)

	abstract fun o_RawUnsignedIntegerAt (self: AvailObject, index: Int): Long

	abstract fun o_RawUnsignedIntegerAtPut (
		self: AvailObject,
		index: Int,
		value: Int)

	abstract fun o_RemoveDependentChunk (self: AvailObject, chunk: L2Chunk)

	abstract fun o_RemoveFrom (
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit)

	abstract fun o_RemoveDefinition (
		self: AvailObject,
		definition: A_Definition)

	abstract fun o_RemoveGrammaticalRestriction (
		self: AvailObject,
		obsoleteRestriction: A_GrammaticalRestriction)

	abstract fun o_ResolveForward (
		self: AvailObject,
		forwardDefinition: A_BasicObject)

	abstract fun o_SetIntersectionCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set

	abstract fun o_SetMinusCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set

	abstract fun o_SetUnionCanDestroy (
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean): A_Set

	@Throws(VariableSetException::class)
	abstract fun o_SetValue (
		self: AvailObject,
		newValue: A_BasicObject)

	abstract fun o_SetValueNoCheck (
		self: AvailObject,
		newValue: A_BasicObject)

	abstract fun o_SetWithElementCanDestroy (
		self: AvailObject,
		newElementObject: A_BasicObject,
		canDestroy: Boolean): A_Set

	abstract fun o_SetWithoutElementCanDestroy (
		self: AvailObject,
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean): A_Set

	abstract fun o_StackAt (self: AvailObject, slotIndex: Int): AvailObject

	abstract fun o_SetStartingChunkAndReoptimizationCountdown (
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long)

	/**
	 * Difference the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [subtractFromIntegerCanDestroy][A_Number.subtractFromIntegerCanDestroy]
	 * or
	 * [subtractFromInfinityCanDestroy][AvailObject.subtractFromInfinityCanDestroy],
	 * where actual implementations of the subtraction operation should reside.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param sign
	 *   The [Sign] of the [infinity][InfinityDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of differencing the operands.
	 * @see AvailObject.subtractFromInfinityCanDestroy
	 */
	abstract fun o_SubtractFromInfinityCanDestroy (
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean): A_Number

	/**
	 * Difference the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [subtractFromIntegerCanDestroy][A_Number.subtractFromIntegerCanDestroy]
	 * or
	 * [subtractFromInfinityCanDestroy][AvailObject.subtractFromInfinityCanDestroy],
	 * where actual implementations of the subtraction operation should reside.
	 *
	 * @param self
	 *   An integral numeric.
	 * @param anInteger
	 *   An [integer][IntegerDescriptor].
	 * @param canDestroy
	 *   `true` if the operation may modify either [operand][AvailObject],
	 *   `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of differencing the operands.
	 * @see AvailObject.subtractFromIntegerCanDestroy
	 */
	abstract fun o_SubtractFromIntegerCanDestroy (
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean): A_Number

	/**
	 * Multiply the [operands][AvailObject] and answer the result.
	 *
	 * Implementations may double-dispatch to
	 * [multiplyByIntegerCanDestroy][A_Number.multiplyByIntegerCanDestroy] or
	 * [multiplyByInfinityCanDestroy][AvailObject.multiplyByInfinityCanDestroy],
	 * where actual implementations of the multiplication operation should
	 * reside.  Other implementations may exist for other type families (e.g.,
	 * floating point).
	 *
	 * @param self
	 *   A [numeric][AbstractNumberDescriptor] value.
	 * @param aNumber
	 *   Another [numeric][AbstractNumberDescriptor] value.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of multiplying the operands.
	 * @see AvailObject.timesCanDestroy
	 */
	abstract fun o_TimesCanDestroy (
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_TrueNamesForStringName (
		self: AvailObject,
		stringName: A_String): A_Set

	abstract fun o_TupleAt (self: AvailObject, index: Int): AvailObject

	abstract fun o_TupleAtPuttingCanDestroy (
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple

	abstract fun o_TupleIntAt (self: AvailObject, index: Int): Int

	abstract fun o_TupleReverse (self: AvailObject): A_Tuple

	abstract fun o_TypeAtIndex (self: AvailObject, index: Int): A_Type

	abstract fun o_TypeIntersection (
		self: AvailObject,
		another: A_Type): A_Type

	abstract fun o_TypeIntersectionOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfFiberType (
		self: AvailObject,
		aFiberType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfMapType (
		self: AvailObject,
		aMapType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): A_Type

	abstract fun o_TypeIntersectionOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfSetType (
		self: AvailObject,
		aSetType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): A_Type

	abstract fun o_TypeUnion (self: AvailObject, another: A_Type): A_Type

	abstract fun o_TypeUnionOfFiberType (
		self: AvailObject,
		aFiberType: A_Type): A_Type

	abstract fun o_TypeUnionOfFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): A_Type

	abstract fun o_TypeUnionOfVariableType (
		self: AvailObject,
		aVariableType: A_Type): A_Type

	abstract fun o_TypeUnionOfContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): A_Type

	abstract fun o_TypeUnionOfCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type

	abstract fun o_TypeUnionOfIntegerRangeType (
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type

	abstract fun o_TypeUnionOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): A_Type

	abstract fun o_TypeUnionOfMapType (
		self: AvailObject,
		aMapType: A_Type): A_Type

	abstract fun o_TypeUnionOfObjectType (
		self: AvailObject,
		anObjectType: AvailObject): A_Type

	abstract fun o_TypeUnionOfPojoType (
		self: AvailObject,
		aPojoType: A_Type): A_Type

	abstract fun o_TypeUnionOfSetType (
		self: AvailObject,
		aSetType: A_Type): A_Type

	abstract fun o_TypeUnionOfTupleType (
		self: AvailObject,
		aTupleType: A_Type): A_Type

	abstract fun o_UnionOfTypesAtThrough (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type

	/**
	 * Construct a Java [string][String] from the given Avail
	 * [string][StringDescriptor].
	 *
	 * @param self
	 *   An Avail string.
	 * @return
	 *   The corresponding Java string.
	 * @see AvailObject.asNativeString
	 */
	abstract fun o_AsNativeString (self: AvailObject): String

	/**
	 * Construct a Java [set][Set] from the given [tuple][TupleDescriptor].
	 *
	 * @param self
	 *   A tuple.
	 * @return
	 *   A set containing each element in the tuple.
	 * @see AvailObject.asSet
	 */
	abstract fun o_AsSet (self: AvailObject): A_Set

	/**
	 * Construct a [tuple][TupleDescriptor] from the given [set][SetDescriptor].
	 * Element ordering in the tuple will be arbitrary and unstable.
	 *
	 * @param self
	 *   A set.
	 * @return
	 *   A tuple containing each element in the set.
	 * @see AvailObject.asTuple
	 */
	abstract fun o_AsTuple (self: AvailObject): A_Tuple

	abstract fun o_BitsPerEntry (self: AvailObject): Int

	abstract fun o_BodyBlock (self: AvailObject): A_Function

	abstract fun o_BodySignature (self: AvailObject): A_Type

	abstract fun o_BreakpointBlock (self: AvailObject): A_BasicObject

	abstract fun o_Caller (self: AvailObject): A_Continuation

	@Throws(VariableGetException::class, VariableSetException::class)
	abstract fun o_AtomicAddToMap (
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject)

	@Throws(VariableGetException::class)
	abstract fun o_VariableMapHasKey (
		self: AvailObject,
		key: A_BasicObject): Boolean

	abstract fun o_ClearValue (self: AvailObject)

	abstract fun o_Function (self: AvailObject): A_Function

	abstract fun o_FunctionType (self: AvailObject): A_Type

	abstract fun o_Code (self: AvailObject): A_RawFunction

	abstract fun o_CodePoint (self: AvailObject): Int

	abstract fun o_LazyComplete (self: AvailObject): A_Set

	abstract fun o_ConstantBindings (self: AvailObject): A_Map

	abstract fun o_ContentType (self: AvailObject): A_Type

	abstract fun o_Continuation (self: AvailObject): A_Continuation

	abstract fun o_CopyAsMutableIntTuple (self: AvailObject): A_Tuple

	abstract fun o_CopyAsMutableObjectTuple (self: AvailObject): A_Tuple

	abstract fun o_DefaultType (self: AvailObject): A_Type

	abstract fun o_EnsureMutable (self: AvailObject): A_Continuation

	abstract fun o_ExecutionState (self: AvailObject): ExecutionState

	abstract fun o_Expand (self: AvailObject, module: A_Module)

	abstract fun o_ExtractBoolean (self: AvailObject): Boolean

	abstract fun o_ExtractUnsignedByte (self: AvailObject): Short

	abstract fun o_ExtractDouble (self: AvailObject): Double

	abstract fun o_ExtractFloat (self: AvailObject): Float

	abstract fun o_ExtractInt (self: AvailObject): Int

	/**
	 * Extract a 64-bit signed Java `long` from the specified Avail
	 * [integer][IntegerDescriptor].
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   A 64-bit signed Java `long`
	 */
	abstract fun o_ExtractLong (self: AvailObject): Long

	abstract fun o_ExtractNybble (self: AvailObject): Byte

	abstract fun o_FieldMap (self: AvailObject): A_Map

	abstract fun o_FieldTypeMap (self: AvailObject): A_Map

	@Throws(VariableGetException::class)
	abstract fun o_GetValue (self: AvailObject): AvailObject

	abstract fun o_HashOrZero (self: AvailObject): Int

	abstract fun o_HasGrammaticalRestrictions (self: AvailObject): Boolean

	abstract fun o_DefinitionsTuple (self: AvailObject): A_Tuple

	abstract fun o_LazyIncomplete (self: AvailObject): A_Map

	abstract fun o_DecrementCountdownToReoptimize (
		self: AvailObject,
		continuation: (Boolean) -> Unit)

	abstract fun o_IsAbstract (self: AvailObject): Boolean

	abstract fun o_IsAbstractDefinition (self: AvailObject): Boolean

	abstract fun o_IsFinite (self: AvailObject): Boolean

	abstract fun o_IsForwardDefinition (self: AvailObject): Boolean

	abstract fun o_IsInstanceMeta (self: AvailObject): Boolean

	abstract fun o_IsMethodDefinition (self: AvailObject): Boolean

	abstract fun o_IsPositive (self: AvailObject): Boolean

	abstract fun o_IsSupertypeOfBottom (self: AvailObject): Boolean

	abstract fun o_KeysAsSet (self: AvailObject): A_Set

	abstract fun o_KeyType (self: AvailObject): A_Type

	abstract fun o_LevelTwoChunk (self: AvailObject): L2Chunk

	abstract fun o_LevelTwoOffset (self: AvailObject): Int

	abstract fun o_Literal (self: AvailObject): AvailObject

	abstract fun o_LowerBound (self: AvailObject): A_Number

	abstract fun o_LowerInclusive (self: AvailObject): Boolean

	abstract fun o_MapSize (self: AvailObject): Int

	abstract fun o_MaxStackDepth (self: AvailObject): Int

	abstract fun o_Message (self: AvailObject): A_Atom

	abstract fun o_MessageParts (self: AvailObject): A_Tuple

	abstract fun o_MethodDefinitions (self: AvailObject): A_Set

	abstract fun o_AtomName (self: AvailObject): A_String

	abstract fun o_ImportedNames (self: AvailObject): A_Map

	abstract fun o_NewNames (self: AvailObject): A_Map

	/**
	 * Answer how many arguments my instances expect.  This is applicable to
	 * both [methods][MethodDescriptor] and
	 * [compiled&#32;code][CompiledCodeDescriptor].
	 *
	 * @param self
	 *   The method or compiled code.
	 * @return
	 *   The number of arguments expected.
	 */
	abstract fun o_NumArgs (self: AvailObject): Int

	abstract fun o_NumSlots (self: AvailObject): Int

	abstract fun o_NumConstants (self: AvailObject): Int

	abstract fun o_NumLiterals (self: AvailObject): Int

	abstract fun o_NumLocals (self: AvailObject): Int

	abstract fun o_NumOuters (self: AvailObject): Int

	abstract fun o_NumOuterVars (self: AvailObject): Int

	abstract fun o_Nybbles (self: AvailObject): A_Tuple

	abstract fun o_Parent (self: AvailObject): A_BasicObject

	abstract fun o_Pc (self: AvailObject): Int

	abstract fun o_PrimitiveNumber (self: AvailObject): Int

	abstract fun o_Priority (self: AvailObject): Int

	abstract fun o_PrivateNames (self: AvailObject): A_Map

	abstract fun o_FiberGlobals (self: AvailObject): A_Map

	abstract fun o_GrammaticalRestrictions (self: AvailObject): A_Set

	abstract fun o_ReturnType (self: AvailObject): A_Type

	abstract fun o_SetSize (self: AvailObject): Int

	abstract fun o_SizeRange (self: AvailObject): A_Type

	abstract fun o_LazyActions (self: AvailObject): A_Map

	abstract fun o_Stackp (self: AvailObject): Int

	abstract fun o_Start (self: AvailObject): Int

	abstract fun o_StartingChunk (self: AvailObject): L2Chunk

	abstract fun o_String (self: AvailObject): A_String

	abstract fun o_TokenType (self: AvailObject): TokenDescriptor.TokenType

	abstract fun o_TrimExcessInts (self: AvailObject)

	abstract fun o_TupleSize (self: AvailObject): Int

	abstract fun o_TypeTuple (self: AvailObject): A_Tuple

	abstract fun o_UpperBound (self: AvailObject): A_Number

	abstract fun o_UpperInclusive (self: AvailObject): Boolean

	abstract fun o_Value (self: AvailObject): AvailObject

	abstract fun o_ValuesAsTuple (self: AvailObject): A_Tuple

	abstract fun o_ValueType (self: AvailObject): A_Type

	abstract fun o_VariableBindings (self: AvailObject): A_Map

	abstract fun o_VisibleNames (self: AvailObject): A_Set

	/**
	 * Answer whether the arguments, both [objects][AvailObject], are equal in
	 * value.
	 *
	 * @param self
	 *   The receiver.
	 * @param another
	 *   The second object used in the comparison.
	 * @return
	 *   `true` if the two objects are of equal value, `false` otherwise.
	 * @see AvailObject.equals
	 */
	abstract fun o_Equals (self: AvailObject, another: A_BasicObject): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [tuple][TupleDescriptor], are equal in value.
	 *
	 * @param self
	 *   The receiver.
	 * @param aTuple
	 *   The tuple used in the comparison.
	 * @return
	 *   `true` if the receiver is a tuple and of value equal to the argument,
	 *   `false` otherwise.
	 * @see AvailObject.equalsAnyTuple
	 */
	abstract fun o_EqualsAnyTuple (
		self: AvailObject,
		aTuple: A_Tuple): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [byte&#32;string][ByteStringDescriptor], are equal in value.
	 *
	 * @param self
	 *   The receiver.
	 * @param aByteString
	 *   The byte string used in the comparison.
	 * @return
	 *   `true` if the receiver is a byte string and of value equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsByteString
	 */
	abstract fun o_EqualsByteString (
		self: AvailObject,
		aByteString: A_String): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject], and a
	 * [byte&#32;tuple][ByteTupleDescriptor], are equal in value.
	 *
	 * @param self
	 *   The receiver.
	 * @param aByteTuple
	 *   The byte tuple used in the comparison.
	 * @return
	 *   `true` if the receiver is a byte tuple and of value equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsByteString
	 */
	abstract fun o_EqualsByteTuple (
		self: AvailObject,
		aByteTuple: A_Tuple): Boolean

	/**
	 * Answer whether the receiver, an [object][AvailObject], is a character
	 * with a code point equal to the integer argument.
	 *
	 * @param self
	 *   The receiver.
	 * @param aCodePoint
	 *   The code point to be compared to the receiver.
	 * @return
	 *   `true` if the receiver is a character with a code point equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsCharacterWithCodePoint
	 */
	abstract fun o_EqualsCharacterWithCodePoint (
		self: AvailObject,
		aCodePoint: Int): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [fiber&#32;type][FiberTypeDescriptor], are equal in value.
	 *
	 * @param self
	 *   The receiver.
	 * @param aFiberType
	 *   A fiber type.
	 * @return
	 *   `true` if the receiver is a fiber type and of value equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsFiberType
	 */
	abstract fun o_EqualsFiberType (
		self: AvailObject,
		aFiberType: A_Type): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [function][FunctionDescriptor], are equal in value.
	 *
	 * @param self
	 *   The receiver.
	 * @param aFunction
	 *   The function used in the comparison.
	 * @return
	 *   `true` if the receiver is a function and of value equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsFunction
	 */
	abstract fun o_EqualsFunction (
		self: AvailObject,
		aFunction: A_Function): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [function&#32;type][FunctionTypeDescriptor], are equal.
	 *
	 * @param self
	 *   The receiver.
	 * @param aFunctionType
	 *   The function type used in the comparison.
	 * @return
	 *   `true` IFF the receiver is also a function type and:
	 *   * The [argument&#32;types][AvailObject.argsTupleType] correspond,
	 *   * The [return&#32;types][AvailObject.returnType] correspond, and
	 *   * The [raise&#32;types][AvailObject.declaredExceptions] correspond.
	 * @see AvailObject.equalsFunctionType
	 */
	abstract fun o_EqualsFunctionType (
		self: AvailObject,
		aFunctionType: A_Type): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [compiled&#32;code][CompiledCodeDescriptor], are equal.
	 *
	 * @param self
	 *   The receiver.
	 * @param aCompiledCode
	 *   The compiled code used in the comparison.
	 * @return
	 *   `true` if the receiver is a compiled code and of value equal to the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsCompiledCode
	 */
	abstract fun o_EqualsCompiledCode (
		self: AvailObject,
		aCompiledCode: A_RawFunction): Boolean

	/**
	 * Answer whether the arguments, an [object][AvailObject] and a
	 * [variable][VariableDescriptor], are the exact same object, comparing by
	 * address (Java object identity). There's no need to traverse the objects
	 * before comparing addresses, because this message was a double-dispatch
	 * that would have skipped (and stripped) the indirection objects in either
	 * path.
	 *
	 * @param self
	 *   The receiver.
	 * @param aVariable
	 *   The variable used in the comparison.
	 * @return
	 *   `true` if the receiver is a variable with the same identity as the
	 *   argument, `false` otherwise.
	 * @see AvailObject.equalsVariable
	 */
	abstract fun o_EqualsVariable (
		self: AvailObject,
		aVariable: A_Variable): Boolean

	abstract fun o_EqualsVariableType (
		self: AvailObject,
		aType: A_Type): Boolean

	abstract fun o_EqualsContinuation (
		self: AvailObject,
		aContinuation: A_Continuation): Boolean

	abstract fun o_EqualsContinuationType (
		self: AvailObject,
		aContinuationType: A_Type): Boolean

	abstract fun o_EqualsCompiledCodeType (
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean

	abstract fun o_EqualsDouble (self: AvailObject, aDouble: Double): Boolean

	abstract fun o_EqualsFloat (self: AvailObject, aFloat: Float): Boolean

	/**
	 * Answer whether the [receiver][AvailObject] is an
	 * [infinity][InfinityDescriptor] with the specified [Sign].
	 *
	 * @param self
	 *   The receiver.
	 * @param sign
	 *   The type of infinity for comparison.
	 * @return
	 *   `true` if the receiver is an infinity of the specified sign, `false`
	 *   otherwise.
	 * @see A_Number.equalsInfinity
	 */
	abstract fun o_EqualsInfinity (self: AvailObject, sign: Sign): Boolean

	abstract fun o_EqualsInteger (
		self: AvailObject,
		anAvailInteger: AvailObject): Boolean

	abstract fun o_EqualsIntegerRangeType (
		self: AvailObject,
		another: A_Type): Boolean

	abstract fun o_EqualsMap (self: AvailObject, aMap: A_Map): Boolean

	abstract fun o_EqualsMapType (self: AvailObject, aMapType: A_Type): Boolean

	abstract fun o_EqualsNybbleTuple (
		self: AvailObject,
		aTuple: A_Tuple): Boolean

	abstract fun o_EqualsObject (
		self: AvailObject,
		anObject: AvailObject): Boolean

	abstract fun o_EqualsObjectTuple (
		self: AvailObject,
		aTuple: A_Tuple): Boolean

	abstract fun o_EqualsPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): Boolean

	abstract fun o_EqualsPojo (
		self: AvailObject,
		aPojo: AvailObject): Boolean

	abstract fun o_EqualsPojoType (
		self: AvailObject,
		aPojoType: AvailObject): Boolean

	abstract fun o_EqualsPrimitiveType (
		self: AvailObject,
		aPrimitiveType: A_Type): Boolean

	abstract fun o_EqualsRawPojoFor (
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any?): Boolean

	abstract fun o_EqualsReverseTuple (
		self: AvailObject,
		aTuple: A_Tuple): Boolean

	abstract fun o_EqualsSet (
		self: AvailObject,
		aSet: A_Set): Boolean

	abstract fun o_EqualsSetType (
		self: AvailObject,
		aSetType: A_Type): Boolean

	abstract fun o_EqualsTupleType (
		self: AvailObject,
		aTupleType: A_Type): Boolean

	abstract fun o_EqualsTwoByteString (
		self: AvailObject,
		aString: A_String): Boolean

	abstract fun o_HasObjectInstance (
		self: AvailObject,
		potentialInstance: AvailObject): Boolean

	/**
	 * Given two objects that are known to be equal, is the first one in a
	 * better form (more compact, more efficient, older generation) than the
	 * second one?
	 *
	 * @param self
	 *   The first object.
	 * @param anotherObject
	 *   The second object, equal to the first object.
	 * @return
	 *   Whether the first object is the better representation to keep.
	 */
	abstract fun o_IsBetterRepresentationThan (
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean

	/**
	 * Given two objects that are known to be equal, the second of which is in
	 * the form of a tuple type, is the first one in a better form than the
	 * second one?
	 *
	 * @param self
	 *   The first object.
	 * @return
	 *   Whether the first object is a better representation to keep.
	 */
	abstract fun o_RepresentationCostOfTupleType (self: AvailObject): Int

	abstract fun o_IsInstanceOfKind (self: AvailObject, aType: A_Type): Boolean

	abstract fun o_Hash (self: AvailObject): Int

	/**
	 * Is the specified [AvailObject] an Avail function?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a function, `false` otherwise.
	 * @see AvailObject.isFunction
	 */
	abstract fun o_IsFunction (self: AvailObject): Boolean

	abstract fun o_MakeImmutable (self: AvailObject): AvailObject

	abstract fun o_MakeSubobjectsImmutable (self: AvailObject): AvailObject

	abstract fun o_MakeShared (self: AvailObject): AvailObject

	abstract fun o_MakeSubobjectsShared (self: AvailObject): AvailObject

	abstract fun o_Kind (self: AvailObject): A_Type

	/**
	 * Is the specified [AvailObject] an Avail boolean?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a boolean, `false` otherwise.
	 * @see AvailObject.isBoolean
	 */
	abstract fun o_IsBoolean (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail byte tuple?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a byte tuple, `false` otherwise.
	 * @see AvailObject.isByteTuple
	 */
	abstract fun o_IsByteTuple (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail character?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a character, `false` otherwise.
	 * @see AvailObject.isCharacter
	 */
	abstract fun o_IsCharacter (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail string?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is an Avail string, `false` otherwise.
	 * @see AvailObject.isString
	 */
	abstract fun o_IsString (self: AvailObject): Boolean

	abstract fun o_Traversed (self: AvailObject): AvailObject

	/**
	 * Is the specified [AvailObject] an Avail map?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a map, `false` otherwise.
	 * @see AvailObject.isMap
	 */
	abstract fun o_IsMap (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail unsigned byte?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is an unsigned byte, `false` otherwise.
	 * @see AvailObject.isUnsignedByte
	 */
	abstract fun o_IsUnsignedByte (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail nybble?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a nybble, `false` otherwise.
	 * @see AvailObject.isNybble
	 */
	abstract fun o_IsNybble (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail set?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a set, `false` otherwise.
	 * @see AvailObject.isSet
	 */
	abstract fun o_IsSet (self: AvailObject): Boolean

	abstract fun o_SetBinAddingElementHashLevelCanDestroy (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean): A_BasicObject

	abstract fun o_BinHasElementWithHash (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int): Boolean

	abstract fun o_BinRemoveElementHashLevelCanDestroy (
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean): AvailObject

	abstract fun o_IsBinSubsetOf (
		self: AvailObject,
		potentialSuperset: A_Set): Boolean

	abstract fun o_SetBinHash (self: AvailObject): Int

	abstract fun o_SetBinSize (self: AvailObject): Int

	abstract fun o_MapBinSize (self: AvailObject): Int

	/**
	 * Is the specified [AvailObject] an Avail tuple?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is a tuple, `false` otherwise.
	 * @see AvailObject.isTuple
	 */
	abstract fun o_IsTuple (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail atom?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is an atom, `false` otherwise.
	 * @see AvailObject.isAtom
	 */
	abstract fun o_IsAtom (self: AvailObject): Boolean

	/**
	 * Is the specified [AvailObject] an Avail extended integer?
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   `true` if the argument is an extended integer, `false` otherwise.
	 * @see AvailObject.isExtendedInteger
	 */
	abstract fun o_IsExtendedInteger (self: AvailObject): Boolean

	abstract fun o_IsIntegerIntervalTuple (self: AvailObject): Boolean

	abstract fun o_IsSmallIntegerIntervalTuple (self: AvailObject): Boolean

	abstract fun o_IsRepeatedElementTuple (self: AvailObject): Boolean

	abstract fun o_IsIntegerRangeType (self: AvailObject): Boolean

	abstract fun o_IsMapType (self: AvailObject): Boolean

	abstract fun o_IsSetType (self: AvailObject): Boolean

	abstract fun o_IsTupleType (self: AvailObject): Boolean

	abstract fun o_IsType (self: AvailObject): Boolean

	abstract fun o_ScanSubobjects (
		self: AvailObject,
		visitor: AvailSubobjectVisitor)

	/**
	 * Answer an [iterator][Iterator] suitable for traversing the elements of
	 * the [object][AvailObject] with a Java *foreach* construct.
	 *
	 * @param self
	 *   An [AvailObject].
	 * @return
	 *   An [iterator][Iterator].
	 */
	abstract fun o_Iterator (self: AvailObject): Iterator<AvailObject>

	abstract fun o_Spliterator (self: AvailObject): Spliterator<AvailObject>

	abstract fun o_Stream (self: AvailObject): Stream<AvailObject>

	abstract fun o_ParallelStream (self: AvailObject): Stream<AvailObject>

	abstract fun o_ParsingInstructions (self: AvailObject): A_Tuple

	abstract fun o_Expression (self: AvailObject): A_Phrase

	abstract fun o_Variable (self: AvailObject): A_Phrase

	abstract fun o_ArgumentsTuple (self: AvailObject): A_Tuple

	abstract fun o_StatementsTuple (self: AvailObject): A_Tuple

	abstract fun o_ResultType (self: AvailObject): A_Type

	abstract fun o_NeededVariables (
		self: AvailObject,
		neededVariables: A_Tuple)

	abstract fun o_NeededVariables (self: AvailObject): A_Tuple

	abstract fun o_Primitive (self: AvailObject): Primitive?

	abstract fun o_DeclaredType (self: AvailObject): A_Type

	abstract fun o_DeclarationKind (self: AvailObject): DeclarationKind

	abstract fun o_TypeExpression (self: AvailObject): A_Phrase

	abstract fun o_InitializationExpression (self: AvailObject): AvailObject

	abstract fun o_LiteralObject (self: AvailObject): A_BasicObject

	abstract fun o_Token (self: AvailObject): A_Token

	abstract fun o_MarkerValue (self: AvailObject): A_BasicObject

	abstract fun o_Bundle (self: AvailObject): A_Bundle

	abstract fun o_ExpressionsTuple (self: AvailObject): A_Tuple

	abstract fun o_Declaration (self: AvailObject): A_Phrase

	abstract fun o_ExpressionType (self: AvailObject): A_Type

	abstract fun o_EmitEffectOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator)

	abstract fun o_EmitValueOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator)

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 *
	 * @param self
	 * @param transformer
	 */
	abstract fun o_ChildrenMap (
		self: AvailObject,
		transformer: (A_Phrase) -> A_Phrase)

	/**
	 * Visit my child phrases with aBlock.
	 *
	 * @param self
	 * @param action
	 */
	abstract fun o_ChildrenDo (
		self: AvailObject,
		action: (A_Phrase) -> Unit)

	abstract fun o_ValidateLocally (
		self: AvailObject,
		parent: A_Phrase?)

	abstract fun o_GenerateInModule (
		self: AvailObject,
		module: A_Module): A_RawFunction

	abstract fun o_CopyWith (
		self: AvailObject,
		newPhrase: A_Phrase): A_Phrase

	abstract fun o_CopyConcatenating (
		self: AvailObject,
		newListPhrase: A_Phrase): A_Phrase

	abstract fun o_IsLastUse (self: AvailObject, isLastUse: Boolean)

	abstract fun o_IsLastUse (self: AvailObject): Boolean

	abstract fun o_IsMacroDefinition (self: AvailObject): Boolean

	abstract fun o_CopyMutablePhrase (self: AvailObject): A_Phrase

	abstract fun o_BinUnionKind (self: AvailObject): A_Type

	abstract fun o_OutputPhrase (self: AvailObject): A_Phrase

	abstract fun o_ApparentSendName (self: AvailObject): A_Atom

	abstract fun o_Statements (self: AvailObject): A_Tuple

	abstract fun o_FlattenStatementsInto (
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>)

	abstract fun o_LineNumber (self: AvailObject): Int

	abstract fun o_AllParsingPlansInProgress (self: AvailObject): A_Map

	abstract fun o_IsSetBin (self: AvailObject): Boolean

	abstract fun o_MapIterable (self: AvailObject): MapIterable

	abstract fun o_DeclaredExceptions (self: AvailObject): A_Set

	abstract fun o_IsInt (self: AvailObject): Boolean

	abstract fun o_IsLong (self: AvailObject): Boolean

	abstract fun o_ArgsTupleType (self: AvailObject): A_Type

	abstract fun o_EqualsInstanceTypeFor (
		self: AvailObject,
		anObject: AvailObject): Boolean

	abstract fun o_Instances (self: AvailObject): A_Set

	abstract fun o_EqualsEnumerationWithSet (
		self: AvailObject,
		aSet: A_Set): Boolean

	abstract fun o_IsEnumeration (self: AvailObject): Boolean

	abstract fun o_IsInstanceOf (self: AvailObject, aType: A_Type): Boolean

	abstract fun o_EnumerationIncludesInstance (
		self: AvailObject,
		potentialInstance: AvailObject): Boolean

	abstract fun o_ComputeSuperkind (self: AvailObject): A_Type

	abstract fun o_SetAtomProperty (
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject)

	abstract fun o_GetAtomProperty (self: AvailObject, key: A_Atom): AvailObject

	abstract fun o_EqualsEnumerationType (
		self: AvailObject,
		another: A_BasicObject): Boolean

	abstract fun o_ReadType (self: AvailObject): A_Type

	abstract fun o_WriteType (self: AvailObject): A_Type

	abstract fun o_SetVersions (self: AvailObject, versionStrings: A_Set)

	abstract fun o_Versions (self: AvailObject): A_Set

	abstract fun o_TypeUnionOfPhraseType (
		self: AvailObject,
		aPhraseType: A_Type): A_Type

	abstract fun o_PhraseKind (self: AvailObject): PhraseKind

	abstract fun o_PhraseKindIsUnder (
		self: AvailObject,
		expectedPhraseKind: PhraseKind): Boolean

	abstract fun o_IsRawPojo (self: AvailObject): Boolean

	abstract fun o_AddSemanticRestriction (
		self: AvailObject,
		restriction: A_SemanticRestriction)

	abstract fun o_RemoveSemanticRestriction (
		self: AvailObject,
		restriction: A_SemanticRestriction)

	/**
	 * Return the [method][MethodDescriptor]'s [tuple][TupleDescriptor] of
	 * [functions][FunctionDescriptor] that statically restrict call sites by
	 * argument type.
	 *
	 * @param self
	 *   The method.
	 * @return
	 *   The semantic restrictions.
	 */
	abstract fun o_SemanticRestrictions (self: AvailObject): A_Set

	abstract fun o_AddSealedArgumentsType (
		self: AvailObject,
		typeTuple: A_Tuple)

	abstract fun o_RemoveSealedArgumentsType (
		self: AvailObject,
		typeTuple: A_Tuple)

	abstract fun o_SealedArgumentsTypesTuple (self: AvailObject): A_Tuple

	abstract fun o_ModuleAddSemanticRestriction (
		self: AvailObject,
		semanticRestriction: A_SemanticRestriction)

	abstract fun o_AddConstantBinding (
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable)

	abstract fun o_AddVariableBinding (
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable)

	abstract fun o_IsMethodEmpty (self: AvailObject): Boolean

	abstract fun o_IsPojoSelfType (self: AvailObject): Boolean

	abstract fun o_PojoSelfType (self: AvailObject): A_Type

	abstract fun o_JavaClass (self: AvailObject): AvailObject

	abstract fun o_IsUnsignedShort (self: AvailObject): Boolean

	abstract fun o_ExtractUnsignedShort (self: AvailObject): Int

	abstract fun o_IsFloat (self: AvailObject): Boolean

	abstract fun o_IsDouble (self: AvailObject): Boolean

	abstract fun o_RawPojo (self: AvailObject): AvailObject

	abstract fun o_IsPojo (self: AvailObject): Boolean

	abstract fun o_IsPojoType (self: AvailObject): Boolean

	abstract fun o_NumericCompare (self: AvailObject, another: A_Number): Order

	abstract fun o_NumericCompareToDouble (
		self: AvailObject,
		aDouble: Double): Order

	abstract fun o_NumericCompareToInteger (
		self: AvailObject,
		anInteger: AvailObject): Order

	abstract fun o_NumericCompareToInfinity (
		self: AvailObject,
		sign: Sign): Order

	abstract fun o_AddToDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_AddToFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_SubtractFromDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_SubtractFromFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_MultiplyByDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_MultiplyByFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_DivideIntoDoubleCanDestroy (
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_DivideIntoFloatCanDestroy (
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_LazyPrefilterMap (self: AvailObject): A_Map

	abstract fun o_SerializerOperation (self: AvailObject): SerializerOperation

	abstract fun o_MapBinAtHashPutLevelCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean): A_MapBin

	abstract fun o_MapBinRemoveKeyHashCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean): A_MapBin

	abstract fun o_MapBinAtHashReplacingLevelCanDestroy (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean): A_MapBin

	abstract fun o_MapBinKeyUnionKind (self: AvailObject): A_Type

	abstract fun o_MapBinValueUnionKind (self: AvailObject): A_Type

	abstract fun o_IsHashedMapBin (self: AvailObject): Boolean

	abstract fun o_MapBinAtHash (
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int): AvailObject?

	abstract fun o_MapBinKeysHash (self: AvailObject): Int

	abstract fun o_MapBinValuesHash (self: AvailObject): Int

	abstract fun o_IssuingModule (self: AvailObject): A_Module

	abstract fun o_IsPojoFusedType (self: AvailObject): Boolean

	abstract fun o_IsSupertypeOfPojoBottomType (
		self: AvailObject,
		aPojoType: A_Type): Boolean

	abstract fun o_EqualsPojoBottomType (self: AvailObject): Boolean

	abstract fun o_JavaAncestors (self: AvailObject): AvailObject

	abstract fun o_TypeIntersectionOfPojoFusedType (
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfPojoUnfusedType (
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type

	abstract fun o_TypeUnionOfPojoFusedType (
		self: AvailObject,
		aFusedPojoType: A_Type): A_Type

	abstract fun o_TypeUnionOfPojoUnfusedType (
		self: AvailObject,
		anUnfusedPojoType: A_Type): A_Type

	abstract fun o_IsPojoArrayType (self: AvailObject): Boolean

	abstract fun o_MarshalToJava (
		self: AvailObject,
		classHint: Class<*>?): Any?

	abstract fun o_TypeVariables (self: AvailObject): A_Map

	abstract fun o_EqualsPojoField (
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject): Boolean

	abstract fun o_IsSignedByte (self: AvailObject): Boolean

	abstract fun o_IsSignedShort (self: AvailObject): Boolean

	abstract fun o_ExtractSignedByte (self: AvailObject): Byte

	abstract fun o_ExtractSignedShort (self: AvailObject): Short

	abstract fun o_EqualsEqualityRawPojo (
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?): Boolean

	/**
	 * Answer a pojo's java object.  The type is not statically checkable in
	 * Java, but at least making it generic avoids an explicit cast expression
	 * at each call site.
	 *
	 * @param self
	 *   The Avail pojo object.
	 * @param T
	 *   The type of Java [Object] to return.
	 * @return
	 *   The actual Java object, which may be {code null}.
	 * @see AvailObject.javaObject
	 */
	abstract fun <T> o_JavaObject (self: AvailObject): T?

	abstract fun o_AsBigInteger (self: AvailObject): BigInteger

	abstract fun o_AppendCanDestroy (
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple

	abstract fun o_LazyIncompleteCaseInsensitive (self: AvailObject): A_Map

	abstract fun o_LowerCaseString (self: AvailObject): A_String

	abstract fun o_Instance (self: AvailObject): AvailObject

	abstract fun o_InstanceCount (self: AvailObject): A_Number

	abstract fun o_TotalInvocations (self: AvailObject): Long

	abstract fun o_TallyInvocation (self: AvailObject)

	abstract fun o_FieldTypeTuple (self: AvailObject): A_Tuple

	abstract fun o_FieldTuple (self: AvailObject): A_Tuple

	abstract fun o_ArgumentsListNode (self: AvailObject): A_Phrase

	abstract fun o_LiteralType (self: AvailObject): A_Type

	abstract fun o_TypeIntersectionOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): A_Type

	abstract fun o_TypeIntersectionOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type

	abstract fun o_TypeUnionOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): A_Type

	abstract fun o_TypeUnionOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type

	abstract fun o_IsTokenType (self: AvailObject): Boolean

	abstract fun o_IsLiteralTokenType (self: AvailObject): Boolean

	abstract fun o_IsLiteralToken (self: AvailObject): Boolean

	abstract fun o_IsSupertypeOfTokenType (
		self: AvailObject,
		aTokenType: A_Type): Boolean

	abstract fun o_IsSupertypeOfLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean

	abstract fun o_EqualsTokenType (
		self: AvailObject,
		aTokenType: A_Type): Boolean

	abstract fun o_EqualsLiteralTokenType (
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean

	abstract fun o_EqualsObjectType (
		self: AvailObject,
		anObjectType: AvailObject): Boolean

	abstract fun o_EqualsToken (self: AvailObject, aToken: A_Token): Boolean

	abstract fun o_BitwiseAnd (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_BitwiseOr (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_BitwiseXor (
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_AddSeal (
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple)

	abstract fun o_SetMethodName (self: AvailObject, methodName: A_String)

	abstract fun o_StartingLineNumber (self: AvailObject): Int

	abstract fun o_OriginatingPhrase (self: AvailObject): A_Phrase

	abstract fun o_Module (self: AvailObject): A_Module

	abstract fun o_MethodName (self: AvailObject): A_String

	abstract fun o_NameForDebugger (self: AvailObject): String

	open fun o_ShowValueInNameForDebugger (self: AvailObject): Boolean = true

	abstract fun o_BinElementsAreAllInstancesOfKind (
		self: AvailObject,
		kind: A_Type): Boolean

	abstract fun o_SetElementsAreAllInstancesOfKind (
		self: AvailObject,
		kind: AvailObject): Boolean

	abstract fun o_MapBinIterable (self: AvailObject): MapIterable

	abstract fun o_RangeIncludesInt (self: AvailObject, anInt: Int): Boolean

	abstract fun o_BitShiftLeftTruncatingToBits (
		self: AvailObject,
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_SetBinIterator (self: AvailObject): SetIterator

	abstract fun o_BitShift (
		self: AvailObject,
		shiftFactor: A_Number,
		canDestroy: Boolean): A_Number

	abstract fun o_EqualsPhrase (self: AvailObject, aPhrase: A_Phrase): Boolean

	abstract fun o_StripMacro (self: AvailObject): A_Phrase

	abstract fun o_DefinitionMethod (self: AvailObject): A_Method

	abstract fun o_PrefixFunctions (self: AvailObject): A_Tuple

	abstract fun o_EqualsByteArrayTuple (
		self: AvailObject,
		aByteArrayTuple: A_Tuple): Boolean

	abstract fun o_CompareFromToWithByteArrayTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int): Boolean

	abstract fun o_ByteArray (self: AvailObject): ByteArray

	abstract fun o_IsByteArrayTuple (self: AvailObject): Boolean

	abstract fun o_UpdateForNewGrammaticalRestriction (
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress,
		treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>)

	abstract fun <T> o_Lock (self: AvailObject, body: () -> T): T

	abstract fun o_ModuleName (self: AvailObject): A_String

	abstract fun o_BundleMethod (self: AvailObject): A_Method

	@Throws(VariableGetException::class, VariableSetException::class)
	abstract fun o_GetAndSetValue (
		self: AvailObject,
		newValue: A_BasicObject): AvailObject

	@Throws(VariableGetException::class, VariableSetException::class)
	abstract fun o_CompareAndSwapValues (
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject): Boolean

	@Throws(VariableGetException::class, VariableSetException::class)
	abstract fun o_FetchAndAddValue (
		self: AvailObject,
		addend: A_Number): A_Number

	abstract fun o_FailureContinuation (
		self: AvailObject): (Throwable) -> Unit

	abstract fun o_ResultContinuation (
		self: AvailObject): (AvailObject) -> Unit

	abstract fun o_AvailLoader (self: AvailObject): AvailLoader?

	abstract fun o_SetAvailLoader (self: AvailObject, loader: AvailLoader?)

	abstract fun o_InterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag): Boolean

	abstract fun o_GetAndClearInterruptRequestFlag (
		self: AvailObject,
		flag: InterruptRequestFlag): Boolean

	abstract fun o_GetAndSetSynchronizationFlag (
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean): Boolean

	abstract fun o_FiberResult (self: AvailObject): AvailObject

	abstract fun o_SetFiberResult (self: AvailObject, result: A_BasicObject)

	abstract fun o_JoiningFibers (self: AvailObject): A_Set

	abstract fun o_WakeupTask (self: AvailObject): TimerTask?

	abstract fun o_SetWakeupTask (self: AvailObject, task: TimerTask?)

	abstract fun o_SetJoiningFibers (self: AvailObject, joiners: A_Set)

	abstract fun o_HeritableFiberGlobals (self: AvailObject): A_Map

	abstract fun o_SetHeritableFiberGlobals (self: AvailObject, globals: A_Map)

	abstract fun o_GeneralFlag (self: AvailObject, flag: GeneralFlag): Boolean

	abstract fun o_SetGeneralFlag (self: AvailObject, flag: GeneralFlag)

	abstract fun o_ClearGeneralFlag (self: AvailObject, flag: GeneralFlag)

	abstract fun o_EqualsByteBufferTuple (
		self: AvailObject,
		aByteBufferTuple: A_Tuple): Boolean

	abstract fun o_CompareFromToWithByteBufferTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int): Boolean

	abstract fun o_ByteBuffer (self: AvailObject): ByteBuffer

	abstract fun o_IsByteBufferTuple (self: AvailObject): Boolean

	abstract fun o_FiberName (self: AvailObject): A_String

	abstract fun o_FiberNameSupplier (
		self: AvailObject,
		supplier: () -> A_String)

	abstract fun o_Bundles (self: AvailObject): A_Set

	abstract fun o_MethodAddBundle (self: AvailObject, bundle: A_Bundle)

	abstract fun o_DefinitionModule (self: AvailObject): A_Module

	abstract fun o_DefinitionModuleName (self: AvailObject): A_String

	@Throws(MalformedMessageException::class)
	abstract fun o_BundleOrCreate (self: AvailObject): A_Bundle

	abstract fun o_BundleOrNil (self: AvailObject): A_Bundle

	abstract fun o_EntryPoints (self: AvailObject): A_Map

	abstract fun o_AddEntryPoint (
		self: AvailObject,
		stringName: A_String,
		trueName: A_Atom)

	abstract fun o_AllAncestors (self: AvailObject): A_Set

	abstract fun o_AddAncestors (self: AvailObject, moreAncestors: A_Set)

	abstract fun o_ArgumentRestrictionSets (self: AvailObject): A_Tuple

	abstract fun o_RestrictedBundle (self: AvailObject): A_Bundle

	abstract fun o_AdjustPcAndStackp (self: AvailObject, pc: Int, stackp: Int)

	abstract fun o_TreeTupleLevel (self: AvailObject): Int

	abstract fun o_ChildCount (self: AvailObject): Int

	abstract fun o_ChildAt (self: AvailObject, childIndex: Int): A_Tuple

	abstract fun o_ConcatenateWith (
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple

	abstract fun o_ReplaceFirstChild (
		self: AvailObject,
		newFirst: A_Tuple): A_Tuple

	abstract fun o_IsByteString (self: AvailObject): Boolean

	abstract fun o_IsTwoByteString (self: AvailObject): Boolean

	abstract fun o_EqualsIntegerIntervalTuple (
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple): Boolean

	abstract fun o_EqualsIntTuple (
		self: AvailObject,
		anIntTuple: A_Tuple): Boolean

	abstract fun o_EqualsSmallIntegerIntervalTuple (
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple): Boolean

	abstract fun o_EqualsRepeatedElementTuple (
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple): Boolean

	abstract fun o_AddWriteReactor (
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor)

	@Throws(AvailException::class)
	abstract fun o_RemoveWriteReactor (self: AvailObject, key: A_Atom)

	abstract fun o_TraceFlag (self: AvailObject, flag: TraceFlag): Boolean

	abstract fun o_SetTraceFlag (self: AvailObject, flag: TraceFlag)

	abstract fun o_ClearTraceFlag (self: AvailObject, flag: TraceFlag)

	abstract fun o_RecordVariableAccess (
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean)

	abstract fun o_VariablesReadBeforeWritten (self: AvailObject): A_Set

	abstract fun o_VariablesWritten (self: AvailObject): A_Set

	abstract fun o_ValidWriteReactorFunctions (self: AvailObject): A_Set

	abstract fun o_ReplacingCaller (
		self: AvailObject,
		newCaller: A_Continuation): A_Continuation

	abstract fun o_WhenContinuationIsAvailableDo (
		self: AvailObject,
		whenReified: (A_Continuation) -> Unit)

	abstract fun o_GetAndClearReificationWaiters (self: AvailObject): A_Set

	abstract fun o_IsBottom (self: AvailObject): Boolean

	abstract fun o_IsVacuousType (self: AvailObject): Boolean

	abstract fun o_IsTop (self: AvailObject): Boolean

	abstract fun o_IsAtomSpecial (self: AvailObject): Boolean

	abstract fun o_AddPrivateNames (self: AvailObject, trueNames: A_Set)

	abstract fun o_HasValue (self: AvailObject): Boolean

	abstract fun o_AddUnloadFunction (
		self: AvailObject,
		unloadFunction: A_Function)

	abstract fun o_ExportedNames (self: AvailObject): A_Set

	abstract fun o_IsInitializedWriteOnceVariable (self: AvailObject): Boolean

	abstract fun o_TransferIntoByteBuffer (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)

	abstract fun o_TupleElementsInRangeAreInstancesOf (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean

	abstract fun o_IsNumericallyIntegral (self: AvailObject): Boolean

	abstract fun o_TextInterface (self: AvailObject): TextInterface

	abstract fun o_SetTextInterface (
		self: AvailObject,
		textInterface: TextInterface)

	abstract fun o_WriteTo (self: AvailObject, writer: JSONWriter)

	abstract fun o_WriteSummaryTo (self: AvailObject, writer: JSONWriter)

	abstract fun o_TypeIntersectionOfPrimitiveTypeEnum (
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	abstract fun o_TypeUnionOfPrimitiveTypeEnum (
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	abstract fun o_TupleOfTypesFromTo (
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple

	abstract fun o_List (self: AvailObject): A_Phrase

	abstract fun o_Permutation (self: AvailObject): A_Tuple

	abstract fun o_EmitAllValuesOn (
		self: AvailObject,
		codeGenerator: AvailCodeGenerator)

	abstract fun o_SuperUnionType (self: AvailObject): A_Type

	abstract fun o_HasSuperCast (self: AvailObject): Boolean

	abstract fun o_MacroDefinitionsTuple (self: AvailObject): A_Tuple

	abstract fun o_LookupMacroByPhraseTuple (
		self: AvailObject,
		argumentPhraseTuple: A_Tuple): A_Tuple

	abstract fun o_ExpressionAt (self: AvailObject, index: Int): A_Phrase

	abstract fun o_ExpressionsSize (self: AvailObject): Int

	abstract fun o_ParsingPc (self: AvailObject): Int

	abstract fun o_IsMacroSubstitutionNode (self: AvailObject): Boolean

	abstract fun o_LastExpression (self: AvailObject): A_Phrase

	abstract fun o_MessageSplitter (self: AvailObject): MessageSplitter

	abstract fun o_StatementsDo (
		self: AvailObject,
		continuation: (A_Phrase) -> Unit)

	abstract fun o_MacroOriginalSendNode (self: AvailObject): A_Phrase

	abstract fun o_EqualsInt (self: AvailObject, theInt: Int): Boolean

	abstract fun o_Tokens (self: AvailObject): A_Tuple

	abstract fun o_ChooseBundle (
		self: AvailObject,
		currentModule: A_Module): A_Bundle

	abstract fun o_ValueWasStablyComputed (self: AvailObject): Boolean

	abstract fun o_SetValueWasStablyComputed (
		self: AvailObject,
		wasStablyComputed: Boolean)

	abstract fun o_Definition (self: AvailObject): A_Definition

	abstract fun o_NameHighlightingPc (self: AvailObject): String

	abstract fun o_SetIntersects (self: AvailObject, otherSet: A_Set): Boolean

	abstract fun o_RemovePlanForDefinition (
		self: AvailObject,
		definition: A_Definition)

	abstract fun o_DefinitionParsingPlans (self: AvailObject): A_Map

	abstract fun o_EqualsListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): Boolean

	abstract fun o_SubexpressionsTupleType (self: AvailObject): A_Type

	abstract fun o_IsSupertypeOfListNodeType (
		self: AvailObject,
		aListNodeType: A_Type): Boolean

	abstract fun o_UniqueId (self: AvailObject): Long

	abstract fun o_LazyTypeFilterTreePojo (self: AvailObject): A_BasicObject

	abstract fun o_AddPlanInProgress (
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress)

	abstract fun o_ParsingSignature (self: AvailObject): A_Type

	abstract fun o_RemovePlanInProgress (
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress)

	abstract fun o_ModuleSemanticRestrictions (self: AvailObject): A_Set

	abstract fun o_ModuleGrammaticalRestrictions (self: AvailObject): A_Set

	abstract fun o_FieldAt (self: AvailObject, field: A_Atom): AvailObject

	abstract fun o_FieldAtOrNull (
		self: AvailObject,
		field: A_Atom): AvailObject?

	abstract fun o_FieldAtPuttingCanDestroy (
		self: AvailObject,
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean): A_BasicObject

	abstract fun o_FieldTypeAt (self: AvailObject, field: A_Atom): A_Type

	abstract fun o_FieldTypeAtOrNull (self: AvailObject, field: A_Atom): A_Type?

	abstract fun o_ParsingPlan (self: AvailObject): A_DefinitionParsingPlan

	abstract fun o_CompareFromToWithIntTupleStartingAt (
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int): Boolean

	abstract fun o_IsIntTuple (self: AvailObject): Boolean

	abstract fun o_LexerMethod (self: AvailObject): A_Method

	abstract fun o_LexerFilterFunction (self: AvailObject): A_Function

	abstract fun o_LexerBodyFunction (self: AvailObject): A_Function

	abstract fun o_SetLexer (self: AvailObject, lexer: A_Lexer)

	abstract fun o_AddLexer (self: AvailObject, lexer: A_Lexer)

	abstract fun o_NextLexingState (self: AvailObject): LexingState

	abstract fun o_NextLexingStatePojo (self: AvailObject): AvailObject

	abstract fun o_SetNextLexingStateFromPrior (
		self: AvailObject,
		priorLexingState: LexingState)

	abstract fun o_TupleCodePointAt (self: AvailObject, index: Int): Int

	abstract fun o_IsGlobal (self: AvailObject): Boolean

	abstract fun o_GlobalModule (self: AvailObject): A_Module

	abstract fun o_GlobalName (self: AvailObject): A_String

	abstract fun o_CreateLexicalScanner (self: AvailObject): LexicalScanner

	abstract fun o_Lexer (self: AvailObject): A_Lexer

	abstract fun o_SetSuspendingFunction (
		self: AvailObject,
		suspendingFunction: A_Function)

	abstract fun o_SuspendingFunction (self: AvailObject): A_Function

	abstract fun o_IsBackwardJump (self: AvailObject): Boolean

	abstract fun o_LatestBackwardJump (self: AvailObject): A_BundleTree

	abstract fun o_HasBackwardJump (self: AvailObject): Boolean

	abstract fun o_IsSourceOfCycle (self: AvailObject): Boolean

	abstract fun o_IsSourceOfCycle (self: AvailObject, isSourceOfCycle: Boolean)

	abstract fun o_ReturnerCheckStat (self: AvailObject): Statistic

	abstract fun o_ReturneeCheckStat (self: AvailObject): Statistic

	abstract fun o_NumNybbles (self: AvailObject): Int

	abstract fun o_LineNumberEncodedDeltas (self: AvailObject): A_Tuple

	abstract fun o_CurrentLineNumber (self: AvailObject): Int

	abstract fun o_FiberResultType (self: AvailObject): A_Type

	abstract fun o_TestingTree (
		self: AvailObject): LookupTree<A_Definition, A_Tuple>

	abstract fun o_ForEach (
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit)

	abstract fun o_ForEachInMapBin (
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit)

	abstract fun o_SetSuccessAndFailureContinuations (
		self: AvailObject,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit)

	abstract fun o_ClearLexingState (self: AvailObject)

	abstract fun o_RegisterDump (self: AvailObject): AvailObject

	companion object
	{
		/**
		 * The [CheckedMethod] for [isMutable].
		 */
		val isMutableMethod: CheckedMethod = CheckedMethod.instanceMethod(
			AbstractDescriptor::class.java,
			"isMutable",
			Boolean::class.javaPrimitiveType!!)

		/**
		 * Note: This is a logical shift *without* Java's implicit modulus on
		 * the shift amount.
		 *
		 * @param value
		 *   The value to shift.
		 * @param leftShift
		 *   The amount to shift left. If negative, shift right by the
		 *   corresponding positive amount.
		 * @return
		 *   The shifted integer, modulus 2^32 then cast to `int`.
		 */
		fun bitShiftInt (value: Int, leftShift: Int): Int
		{
			return when
			{
				leftShift >= 32 -> 0
				leftShift >= 0 -> value shl leftShift
				leftShift > -32 -> value ushr -leftShift
				else -> 0
			}
		}

		/**
		 * Note: This is a logical shift *without* Java's implicit modulus on
		 * the shift amount.
		 *
		 * @param value
		 *   The value to shift.
		 * @param leftShift
		 *   The amount to shift left. If negative, shift right by the
		 *   corresponding positive amount.
		 * @return
		 *   The shifted integer, modulus 2^64 then cast to `long`.
		 */
		fun bitShiftLong (value: Long, leftShift: Int): Long
		{
			return when
			{
				leftShift >= 64 -> 0L
				leftShift >= 0 -> value shl leftShift
				leftShift > -64 -> value ushr -leftShift
				else -> 0L
			}
		}

		/**
		 * Note: This is an arithmetic (i.e., signed) shift *without* Java's
		 * implicit modulus on the shift amount.
		 *
		 * @param value
		 *   The value to shift.
		 * @param leftShift
		 *   The amount to shift left. If negative, shift right by the
		 *   corresponding positive amount.
		 * @return
		 *   The shifted integer, modulus 2^64 then cast to `long`.
		 */
		fun arithmeticBitShiftLong (value: Long, leftShift: Int): Long
		{
			return when
			{
				leftShift >= 64 -> 0L
				leftShift >= 0 -> value shl leftShift
				leftShift > -64 -> value shr -leftShift
				else -> value shr 63
			}
		}

		/** A reusable empty array for when field checking is disabled. */
		private val emptyDebugObjectSlots: Array<Array<ObjectSlotsEnum>?> =
			arrayOf()

		/** A reusable empty array for when field checking is disabled.  */
		private val emptyDebugIntegerSlots: Array<Array<IntegerSlotsEnum>?> =
			arrayOf()

		/**
		 * Look up the specified [Annotation] from the [Enum] constant. If the
		 * enumeration constant does not have an annotation of that type then
		 * answer null.
		 *
		 * @param A
		 *   The `Annotation` type.
		 * @param enumConstant
		 *   The `Enum` value.
		 * @param annotationClass
		 *   The [Class] of the `Annotation` type.
		 * @return
		 *   The requested annotation or null.
		 */
		private fun <A : Annotation?> getAnnotation (
				enumConstant: Enum<out Enum<*>>,
				annotationClass: Class<A>): A? =
			try
			{
				enumConstant.javaClass
					.getField(enumConstant.name)
					.getAnnotation(annotationClass)
			}
			catch (e: NoSuchFieldException)
			{
				throw RuntimeException(
					"Enum class didn't recognize its own instance",
					e)
			}

		/**
		 * A static cache of mappings from [integer&#32;slots][IntegerSlotsEnum]
		 * to [List]s of [BitField]s.  Access to the map must be synchronized,
		 * which isn't much of a penalty since it only affects the default
		 * object printing mechanism.
		 */
		private val bitFieldsCache =
			mutableMapOf<IntegerSlotsEnum, List<BitField>>()

		/** A [ReadWriteLock] that protects the [bitFieldsCache]. */
		private val bitFieldsLock = ReentrantReadWriteLock()

		/**
		 * Describe the integer field onto the provided [StringBuilder]. The
		 * pre-extracted `long` value is provided, as well as the containing
		 * [AvailObject] and the [IntegerSlotsEnum] instance. Take into account
		 * annotations on the slot enumeration object which may define the way
		 * it should be described.
		 *
		 * @param self
		 *   The object containing the `int` value in some slot.
		 * @param value
		 *   The `long` value of the slot.
		 * @param slot
		 *   The [integer&#32;slot][IntegerSlotsEnum] definition.
		 * @param bitFields
		 *   The slot's [BitField]s, if any.
		 * @param builder
		 *   Where to write the description.
		 */
		fun describeIntegerSlot (
			self: AvailObject,
			value: Long,
			slot: IntegerSlotsEnum,
			bitFields: List<BitField>,
			builder: StringBuilder)
		{
			try
			{
				val slotName = slot.fieldName()
				if (bitFields.isEmpty())
				{
					val slotMirror = slot.javaClass.getField(slotName)
					val enumAnnotation =
						slotMirror.getAnnotation(EnumField::class.java)
					var numBits = 64
					if (enumAnnotation !== null)
					{
						val enumClass = enumAnnotation.describedBy.java
						val enumValues = enumClass.enumConstants
						numBits = 64 - java.lang.Long.numberOfLeadingZeros(
							enumValues.size.toLong())
					}
					builder.append(" = ")
					describeIntegerField(
						value, numBits, enumAnnotation, builder)
				}
				else
				{
					builder.append("(")
					var first = true
					for (bitField in bitFields)
					{
						if (!first)
						{
							builder.append(", ")
						}
						builder.append(bitField.name)
						builder.append("=")
						describeIntegerField(
							self.slot(bitField).toLong(),
							bitField.bits,
							bitField.enumField,
							builder)
						first = false
					}
					builder.append(")")
				}
			}
			catch (e: SecurityException)
			{
				throw RuntimeException(e)
			}
			catch (e: IllegalArgumentException)
			{
				throw RuntimeException(e)
			}
			catch (e: ReflectiveOperationException)
			{
				throw RuntimeException(e)
			}
		}

		/**
		 * Extract the [integer&#32;slot][IntegerSlotsEnum]'s [List] of
		 * [BitField]s, excluding ones marked with the annotation
		 * @[HideFieldInDebugger].
		 *
		 * @param slot
		 *   The integer slot.
		 * @return
		 *   The slot's bit fields.
		 */
		fun bitFieldsFor (slot: IntegerSlotsEnum): List<BitField>
		{
			bitFieldsLock.read {
				// Vast majority of cases.
				val bitFields = bitFieldsCache[slot]
				if (bitFields !== null)
				{
					return@bitFieldsFor bitFields
				}
			}
			bitFieldsLock.write {
				// Try again, this time holding the write lock to avoid multiple
				// threads trying to populate the cache.
				var bitFields = bitFieldsCache[slot]
				if (bitFields !== null)
				{
					return@bitFieldsFor bitFields
				}
				val slotAsEnum = slot as Enum<*>
				val slotClass = slotAsEnum::class.java.declaringClass
				bitFields = ArrayList()
				for (field in slotClass.declaredFields)
				{
					if (Modifier.isStatic(field.modifiers)
						&& BitField::class.java.isAssignableFrom(field.type))
					{
						try
						{
							val bitField = cast<Any, BitField>(field[null])
							if (bitField.integerSlot === slot)
							{
								if (field.getAnnotation(
										HideFieldInDebugger::class.java)
									=== null)
								{
									bitField.enumField = field.getAnnotation(
										EnumField::class.java)
									bitField.name = field.name
									bitFields.add(bitField)
								}
							}
						}
						catch (e: IllegalAccessException)
						{
							throw RuntimeException(e)
						}
					}
				}
				val sorted =
					if (bitFields.isEmpty()) emptyList()
					else bitFields.sorted()
				bitFieldsCache[slot] = sorted
				return@bitFieldsFor sorted
			}
		}

		/**
		 * Write a description of an integer field to the [StringBuilder].
		 *
		 * @param value
		 *   The value of the field, a `long`.
		 * @param numBits
		 *   The number of bits to show for this field.
		 * @param enumAnnotation
		 *   The optional [EnumField] annotation that was found on the field.
		 * @param builder
		 *   Where to write the description.
		 * @throws ReflectiveOperationException
		 *   If the [EnumField.lookupMethodName] is incorrect.
		 */
		@Throws(ReflectiveOperationException::class)
		private fun describeIntegerField (
			value: Long,
			numBits: Int,
			enumAnnotation: EnumField?,
			builder: StringBuilder) =
		with(builder) {
			if (enumAnnotation !== null)
			{
				val describingClass: Class<Enum<*>> =
					cast(enumAnnotation.describedBy)
				val lookupName = enumAnnotation.lookupMethodName
				if (lookupName.isEmpty())
				{
					// Look it up by ordinal (must be an actual Enum).
					val allValues: Array<AbstractSlotsEnum> =
						cast(describingClass.enumConstants)
					if (value in allValues.indices)
					{
						append(allValues[value.toInt()].fieldName())
					}
					else
					{
						append("(enum out of range: ")
						describeLong(value, numBits, builder)
						append(")")
					}
				}
				else
				{
					// Look it up via the specified static lookup method.  It's
					// only required to be an IntegerEnumSlotDescriptionEnum in
					// this case, not necessarily an Enum.
					val lookupMethod = describingClass.getMethod(
						lookupName, Int::class.javaPrimitiveType)
					when (val lookedUp =
						lookupMethod.invoke(null, value.toInt()))
					{
						is IntegerEnumSlotDescriptionEnum ->
							append(lookedUp.fieldName())
						else ->
							append("null")
					}
				}
			}
			else
			{
				describeLong(value, numBits, builder)
			}
		}

		/**
		 * Write a description of this [Long] to the builder, taking note that
		 * the value is constrained to contain only numBits of content.  Use
		 * conventions such as grouping into groups of at most four hex digits.
		 *
		 * @param value
		 *   The [Long] to output.
		 * @param numBits
		 *   The number of bits contained in value.
		 * @param builder
		 *   Where to describe the number.
		 */
		@JvmStatic
		fun describeLong (
			value: Long,
			numBits: Int,
			builder: StringBuilder): Unit =
		with(builder) {
			// Present signed byte as unsigned, and unsigned byte unchanged.
			if (numBits <= 8 && -0x80 <= value && value <= 0xFF)
			{
				append(String.format("0x%02X", value and 0xFF))
				return
			}
			// Present signed short as unsigned, and unsigned short unchanged.
			if (numBits <= 16 && -0x8000 <= value && value <= 0xFFFF)
			{
				append(String.format("0x%04X", value and 0xFFFF))
				return
			}
			// Present signed int as unsigned, and unsigned int unchanged.
			if (numBits <= 32 && -0x80000000 <= value && value <= 0xFFFFFFFFL)
			{
				append(String.format(
					"0x%04X_%04X",
					value ushr 16 and 0xFFFF,
					value and 0xFFFF))
				return
			}
			// Present a long as unsigned.
			append(String.format(
				"0x%04X_%04X_%04X_%04X",
				value ushr 48 and 0xFFFF,
				value ushr 32 and 0xFFFF,
				value ushr 16 and 0xFFFF,
				value and 0xFFFF))
		}
	}
}
