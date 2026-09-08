/*
 * L2ValueManifest.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
 * POSSIBILITY OF SUCH DAMAGE."
 */
package avail.optimizer.manifest

import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantFromId
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceTag
import avail.descriptor.representation.A_Type.Companion.objectTypeVariant
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.restrictionForTagRestriction
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Instruction.InstructionEquivalence
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.topRestriction
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_NOP
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.numbers.L2_ADD_INT_TO_INT
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.L2Synonym
import avail.optimizer.ValueClass
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyBoxedRepresentation
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyFloatRepresentation
import avail.optimizer.manifest.L2ValueManifest.Representation.Companion.emptyIntRepresentation
import avail.optimizer.manifest.L2ValueManifest.ValueState.Companion.newEmptyState
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticDummy
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticObjectVariantId
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.utility.Mutable
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.mapToSet
import avail.utility.notNullAnd
import kotlin.LazyThreadSafetyMode.NONE

/**
 * The [L2ValueManifest] maintains information about which [L2SemanticValue]s
 * hold equivalent values at this point, the [TypeRestriction]s for those
 * semantic values, and the list of [L2WriteOperand]s that are visible
 * definitions of those values.  It also tracks postponed instructions, which
 * are only generated into real instructions when a semantic value is read, but
 * there is not yet a [L2WriteOperand] that populates that semantic value.
 *
 * To avoid reevaluating primitives with the same values, a manifest also tracks
 * [L2Register]s that hold values representing which [L2SemanticValue]s,
 * specifically using [L2Synonym]s as the binding mechanism.
 *
 * The basic structure of the manifest is two maps: One from semantic value to
 * synonym (kept exactly in sync with each synonym's membership), and  a mapping
 * from synonym to [Constraint], which contains a [TypeRestriction] that must
 * hold for the value, and the [L2Register]s that hold that value.  The
 * [Constraint]s are mutable, and are copied when cloning a manifest.
 *
 * During code [generation][L2Generator] or [regeneration][L2Regenerator],
 * control flow merges usually create [phi][L2_PHI] instructions in the
 * destination block, partitioning incoming synonyms so that only semantic
 * values that are in the same synonyms in *all* incoming edges will be in the
 * same synonym at the destination.  The restriction for a semantic value is the
 * union of the incoming restrictions for that value.  The set of registers is
 * the intersection, which leads to phi creation only if it is empty (otherwise
 * there's already a common register available along all edges, and a phi is
 * unnecessary).  Phi instructions are eventually replaced with moves to a
 * common register at the end of each predecessor block.
 *
 * During some optimization passes, a manifest can also track postponed
 * instructions that have no side effects, allowing them to propagate to later
 * points in the code, or perhaps only to places where the values they produce
 * are actually needed.  When we're lucky, those places are along reification
 * off-ramps, which are relatively rarely reached.  The [Constraint] associated
 * with an [L2Synonym] holds up to one postponed instruction that will populate
 * any semantic values that don't yet have a visible definition.  Using such a
 * semantic value in a read operand causes the postponed instruction to be
 * emitted first, which, if it itself has reads, may cause other postponed
 * instructions to be emitted recursively.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2ValueManifest
{
	/**
	 * Answer whether this manifest indicates that phis have already been
	 * eliminated, replaced by non-SSA moves in predecessor blocks.
	 */
	val hasEliminatedPhis: Boolean get() = mode != BySemanticValue

	/**
	 * The [GenerationMode] that controls how this monifest should treat
	 * semantic values, synonyms, and registers.  At different phases of
	 * compilation, different aspects will be respected, due to transition from
	 * SSA, replacement of registers after coloring, and removal of same-color
	 * moves.
	 */
	var mode: GenerationMode

	/**
	 * The register-level facts about one value, for a single [RegisterKind]: the
	 * [L2Register]s currently holding it in that kind, and the postponed
	 * [L2Instruction] that would populate them.
	 *
	 * This is the structure that lets one value be described in several
	 * representations at once.  A [ValueClass] contains a separate
	 * [Representation] for each [RegisterKind].
	 *
	 * Keeping the definitions of different kinds in separate representations is
	 * a requirement, not an incidental arrangement.  An earlier incarnation
	 * mixed kinds within one list and it caused problems.
	 *
	 * @property kind
	 *   The [RegisterKind] this representation describes.
	 * @property definitions
	 *   An immutable [List] of [L2Register]s of this [kind] that hold the value.
	 *   The list may be replaced, but not internally modified, and the caller
	 *   must not modify the list after passing it to this constructor.
	 * @property postponedInstruction
	 *   The optional [L2Instruction] that is responsible for populating members
	 *   that do not yet have definitions in this [kind].  It has *not* yet been
	 *   emitted, and might never be, if the values it populates are never read.
	 */
	class Representation<K: RegisterKind<K>>(
		val kind: K,
		val definitions: List<L2Register<K>>,
		val postponedInstruction: L2Instruction?)
	{
		/**
		 * Answer a copy of the receiver with the given postponed instruction.
		 *
		 * @param newPostponed
		 *   The replacement postponed [L2Instruction], or `null`.
		 * @return
		 *   The new [Representation], or the receiver if nothing changed.
		 */
		fun withPostponed(newPostponed: L2Instruction?) = when
		{
			newPostponed === postponedInstruction -> this
			else -> Representation(kind, definitions, newPostponed)
		}

		/**
		 * Answer which of the owning [Constraint]'s members actually have a
		 * defining write **in this representation's [kind]**.
		 *
		 * Definedness is necessarily per-representation: a value can be held in
		 * an int register with no boxed register yet – say the result of an
		 * [L2_ADD_INT_TO_INT] before it is boxed – so "is it defined?" is only
		 * answerable relative to a representation.  Membership, by contrast,
		 * belongs to the [Constraint], which is why it is passed in rather than
		 * duplicated here: three representations each holding their own member
		 * set could drift apart, whereas this is derived on demand and cannot.
		 *
		 * The owner is a parameter rather than a back-pointer deliberately.  A
		 * [Constraint] is replaced wholesale on every update and is shared
		 * between manifests, so a stored back-pointer would need rewriting on
		 * every copy and would otherwise go stale – the same aliasing hazard
		 * that rules out a mutable [L2Synonym].
		 *
		 * @return
		 *   The [L2SemanticValue]s that have a visible defining write of this
		 *   kind.  Deliberately *not* intersected with the owner's members: the
		 *   two being equal is an invariant that [check] tests, and narrowing
		 *   this to the members would quietly satisfy that assertion instead.
		 */
		fun definedMembers(): Set<L2SemanticValue> =
			definitions
				.flatMap(L2Register<K>::definitions)
				.flatMap(L2WriteOperand<K>::semanticValues)
				.toSet()

		/**
		 * Whether this is the [RegisterKind.emptyRepresentation] standing for a
		 * value that is *not held in this kind at all*.
		 *
		 * Distinct from having no definitions, which a real representation has
		 * between the moment a value is introduced and the moment something writes
		 * it.  Both say "no register yet"; only this one says "and no register is
		 * coming, because the value has no life in this kind".
		 */
		val isAbsent: Boolean get() = this === kind.emptyRepresentation

		companion object
		{
			/**
			 * The [Representation] standing for a value that is not held in a
			 * boxed register at all.  Shared, since it says nothing about the
			 * value: it is the *absence* of boxed facts, and a manifest holds an
			 * immense number of those.
			 */
			val emptyBoxedRepresentation =
				Representation(BOXED_KIND, emptyList(), null)

			/** As [emptyBoxedRepresentation], for int registers. */
			val emptyIntRepresentation =
				Representation(INTEGER_KIND, emptyList(), null)

			/** As [emptyBoxedRepresentation], for float registers. */
			val emptyFloatRepresentation =
				Representation(FLOAT_KIND, emptyList(), null)
		}
	}

	/**
	 * The manifest's record of one value: the [L2SemanticValue]s that name
	 * it, the [TypeRestriction] bounding it, and one [Representation] for each
	 * [RegisterKind] in which it is currently held.  It also holds an optional
	 * reference to a [ValueClass] which is its [tagClass], and the inverse set
	 * of [isTagOfClasses].  Similarly for [variantClass] and
	 * [isVariantOfClasses]. The referenced [ValueClass]es have to be [resolve]d
	 * through indirections before use, due to synonym merges.
	 *
	 * This is deliberately *not* generic.  A value can be held in a boxed
	 * register and an int register at the same time, so "what kind is this
	 * value" is not a property of the value at all – it is a property of each
	 * individual question asked about it.  Those questions are asked through a
	 * [Constraint], which is a kind-scoped view of one of these records.
	 *
	 * @property members
	 *   The canonical, boxed [L2SemanticValue]s naming this value.
	 * @property restriction
	 *   The [TypeRestriction] that describes the types, constant values,
	 *   excluded types and excluded values that constrain this value.  It is
	 *   held by the [ValueState], but exposed to each [Constraint] for
	 *   convenience.
	 * @property boxedRepresentation
	 *   The [L2BoxedRegister]s holding this value and the postponed
	 *   [L2Instruction] that would populate them.  A value not held boxed at
	 *   all has [Representation.emptyBoxedRepresentation] here rather than
	 *   nothing, so that every kind can be asked about and answer.
	 * @property intRepresentation
	 *   As [boxedRepresentation], for [L2IntRegister]s.  Non-empty only where the
	 *   value has been established to fit an int register; see [restriction].
	 * @property floatRepresentation
	 *   As [boxedRepresentation], for [L2FloatRegister]s.
	 * @property tagClass
	 *   An optional [ValueClass] identifying the [TypeTag] of the receiver, if
	 *   known.
	 * @property isTagOfClasses
	 *   The set of [ValueClass]es for which the receiver is a [tagClass].
	 * @property variantClass
	 *   An optional [ValueClass] identifying the id of the extracted
	 *   [ObjectLayoutVariant] of the receiver, if known.
	 * @property isVariantOfClasses
	 *   the set of [ValueClass]es for which the receiver is a [variantClass].
	 */
	data class ValueState
	constructor(
		val members: Set<L2SemanticValue>,
		val restriction: TypeRestriction,
		val boxedRepresentation: Representation<BOXED_KIND> =
			emptyBoxedRepresentation,
		val intRepresentation: Representation<INTEGER_KIND> =
			emptyIntRepresentation,
		val floatRepresentation: Representation<FLOAT_KIND> =
			emptyFloatRepresentation,
		val tagClass: ValueClass? = null,
		val isTagOfClasses: Set<ValueClass> = emptySet(),
		val variantClass: ValueClass? = null,
		val isVariantOfClasses: Set<ValueClass> = emptySet())
	{
		/**
		 * Every [Representation] this value currently has, for the [RegisterKind]s
		 * it is held in – the [absent][Representation.isAbsent] ones excluded,
		 * since they describe no register and name no synonym.
		 *
		 * A view over the three slots, for the operations that treat the kinds
		 * symmetrically – folding the registers of every kind together, clearing
		 * every postponed instruction, checking each kind in turn.  The slots are
		 * the storage, so "at most one representation per kind" is structural
		 * rather than an invariant to be checked.
		 */
		val representations: List<Representation<*>> get() =
			listOf(boxedRepresentation, intRepresentation, floatRepresentation)
				.filterNot(Representation<*>::isAbsent)

		/**
		 * Every [L2Register] holding this value, across all of its
		 * [Representation]s.
		 *
		 * Callers wanting *a particular kind's* registers must ask that kind for
		 * its [RegisterKind.representationIn] instead.  This is for the aggregate
		 * questions – "which registers does this manifest mention at all" – where
		 * taking only one kind's would silently under-report once a value has more
		 * than one representation.
		 */
		val allDefinitions: List<L2Register<*>> get() =
			representations.flatMap(Representation<*>::definitions)

		/** Whether this value is impossible to satisfy with any value. */
		val isImpossible get() = restriction.isImpossible

		/** The synonym, created lazily and cached. */
		val synonym: L2Synonym by lazy(NONE) { L2Synonym(members) }

		/**
		 * Answer a copy of the receiver with the given members, [restriction],
		 * and one replaced [Representation].
		 *
		 * Any [Representation] of a *different* [RegisterKind] is preserved: a
		 * value held in an int register as well as a boxed one must not lose the
		 * int register merely because a boxed-scoped update rebuilt the record.
		 *
		 * @param newMembers
		 *   The replacement members, in any single kind's spelling; they are
		 *   canonicalized to boxed on the way in.
		 * @param newRestriction
		 *   The replacement [TypeRestriction].
		 * @param newRepresentation
		 *   The [Representation] to install, replacing any existing one of the
		 *   same [RegisterKind].
		 * @return
		 *   The new [ValueState].
		 */
		fun <K: RegisterKind<K>> updated(
			newMembers: Set<L2SemanticValue>,
			newRestriction: TypeRestriction,
			newRepresentation: Representation<K>
		): ValueState = newRepresentation.kind.stateWith(
			newMembers, newRestriction, newRepresentation, this)

		/**
		 * Answer a copy of the receiver naming the given members instead.
		 *
		 * @param newMembers
		 *   The replacement members, in any single kind's spelling.
		 * @return
		 *   The new [ValueState].
		 */
		fun withMembers(
			newMembers: Set<L2SemanticValue>
		): ValueState = ValueState(
			newMembers,
			restriction,
			boxedRepresentation,
			intRepresentation,
			floatRepresentation)


		/**
		 * Answer a copy of the receiver with the given [Representation]
		 * installed, replacing any existing one of the same [RegisterKind].
		 *
		 * @param newRepresentation
		 *   The [Representation] to install.
		 * @return
		 *   The new [ValueState].
		 */
		fun <K: RegisterKind<K>> withRepresentation(
			newRepresentation: Representation<K>
		): ValueState = updated(members, restriction, newRepresentation)

		/**
		 * Answer a copy of the receiver with no postponed [L2Instruction] in any
		 * of its [Representation]s.
		 *
		 * @return
		 *   The new [ValueState], or the receiver if nothing was postponed.
		 */
		fun withoutPostponed(): ValueState = when
		{
			representations.none { it.postponedInstruction !== null } -> this
			else -> ValueState(
				members,
				restriction,
				boxedRepresentation.withPostponed(null),
				intRepresentation.withPostponed(null),
				floatRepresentation.withPostponed(null))
		}

		/**
		 * Answer a [ValueState] that combines the receiver and [other], for the
		 * set [allMembers] of semantic values, narrowed to [newRestriction].
		 *
		 * @param other
		 *   The [ValueState] being merged away.
		 * @param allMembers
		 *   The complete set of [L2SemanticValue]s to include in the
		 *   merged [ValueState].
		 * @param newRestriction
		 *   The [TypeRestriction] bounding the merged value, normally the
		 *   intersection of the two.
		 * @return
		 *   The merged [ValueState].
		 */
		fun mergedWith(
			other: ValueState,
			allMembers: Set<L2SemanticValue>,
			newRestriction: TypeRestriction
		): ValueState
		{
			// A constant can be reconstructed in any kind, so it never needs an
			// instruction kept for it.
			val isConstant = allMembers.any(L2SemanticValue::isConstant)
			return ValueState(
				allMembers,
				newRestriction,
				mergeRepresentations(
					boxedRepresentation, other.boxedRepresentation, isConstant),
				mergeRepresentations(
					intRepresentation, other.intRepresentation, isConstant),
				mergeRepresentations(
					floatRepresentation, other.floatRepresentation, isConstant))
		}

		/**
		 * The [Constraint] views of this record, indexed by [RegisterKind]'s
		 * [ordinal][RegisterKind.ordinal], and populated on demand.
		 *
		 * Caching them keeps a view's own memoization – notably
		 * [Constraint.synonym] – useful, and is safe for the same reason that
		 * memoization was safe when it lived directly on the constraint: a view
		 * is immutable, and every computation of it produces an equal result.
		 */
		private val viewsCache =
			arrayOfNulls<Constraint<*>>(RegisterKind.all.size)

		/**
		 * Answer the [Constraint] presenting this record in the given
		 * [RegisterKind].
		 *
		 * @param kind
		 *   The [RegisterKind] to scope the view to.
		 * @return
		 *   The kind-scoped [Constraint].
		 */
		fun <K: RegisterKind<K>> viewFor(kind: K): Constraint<K> =
			viewsCache[kind.ordinal]?.cast()
				?: Constraint(this, kind).also { viewsCache[kind.ordinal] = it }

		/**
		 * Answer the [Constraint] presenting this record in the [RegisterKind]
		 * of the given [Representation], which is not known statically.  The
		 * kind answers for itself, so this needs no cast.
		 *
		 * @param representation
		 *   The [Representation] whose kind should scope the view.
		 * @return
		 *   The kind-scoped [Constraint].
		 */
		@Deprecated("Redundant")
		fun viewOf(
			representation: Representation<*>
		): Constraint<*> = viewFor(representation.kind)

		val views: List<Constraint<*>>
			get() = representations.map { viewFor(it.kind) }

		/**
		 * The [Constraint] presenting this record in the [RegisterKind] of its
		 * one and only [Representation].
		 *
		 * Scaffolding, and it fails outright if a value is held in more than
		 * one kind.  Reaching for it means the calling code has no kind in
		 * hand, and is therefore still assuming that a value has exactly one –
		 * which is what makes these the sites to revisit when a value's
		 * spellings come to share a [ValueClass].
		 */
		val soleView: Constraint<*> get() = views.single()

		init
		{
			representations.forEach { representation ->
				val registers = representation.definitions
				val postponed = representation.postponedInstruction
				assert(registers.size == registers.toSet().size)
				assert(
					postponed.isNullOr {
						writeOperands.single().semanticValues().isEmpty()
					})
				// Detect a move from a not-defined value.
				if (postponed is L2_MOVE<*>)
				{
					val sourceValue = postponed.source.semanticValue()
					assert(registers.any { reg ->
						reg.definitions().any { write ->
							sourceValue in write.semanticValues()
						}
					})
				}
			}
		}

		override fun toString(): String = buildString {
			when
			{
				representations.all { it.definitions.isEmpty() } ->
					append("⌛️️POSTPONED")
				restriction.isImpossible -> append("⛔️Impossible")
				else -> allDefinitions.joinTo(this)
			}
			append(": ")
			append(restriction)
		}

		companion object
		{
			/**
			 * Create a [ValueState] without any [Representation].
			 *
			 * @param members
			 *   The [L2SemanticValue]s naming this value.
			 * @param restriction
			 *   The [TypeRestriction] constraining it.
			 * @return
			 *   The new [ValueState].
			 */
			fun newEmptyState(
				members: Set<L2SemanticValue>,
				restriction: TypeRestriction,
			): ValueState = ValueState(
				members,
				restriction,
				emptyBoxedRepresentation,
				emptyIntRepresentation,
				emptyFloatRepresentation)

			/**
			 * Create a record with a single [Representation], taking its
			 * [RegisterKind] from the members.  This is the shape that code
			 * which creates a value from scratch still uses, since a value is
			 * born in exactly one kind.
			 *
			 * @param kind
			 *   The [RegisterKind] of [Representation] to construct, which is
			 *   in aggrement with the provided [definitions], if any.
			 * @param members
			 *   The [L2SemanticValue]s naming this value.
			 * @param definitions
			 *   The [L2Register]s holding it, of the given [kind].
			 * @param restriction
			 *   The [TypeRestriction] bounding it.
			 * @param postponedInstruction
			 *   The postponed [L2Instruction] that would populate it with the
			 *   given [kind], or `null`.
			 * @return
			 *   The new [ValueState].
			 */
			fun <K: RegisterKind<K>> newState(
				kind: K,
				members: Set<L2SemanticValue>,
				definitions: List<L2Register<K>>,
				restriction: TypeRestriction,
				postponedInstruction: L2Instruction?
			): ValueState = kind.stateWith(
				members,
				restriction,
				Representation(kind, definitions, postponedInstruction),
				null)

			/**
			 * Reconcile two [Representation]s of one [RegisterKind], both of
			 * which describe a value that has just been shown to be a single
			 * value.
			 *
			 * @param first
			 *   One [Representation].
			 * @param second
			 *   The other [Representation], of the same [RegisterKind].
			 * @param valueIsConstant
			 *   Whether the merged value is a known constant, and therefore
			 *   reconstructible in any kind without a postponed instruction.
			 * @return
			 *   The reconciled [Representation].
			 */
			private fun <K: RegisterKind<K>> mergeRepresentations(
				first: Representation<K>,
				second: Representation<K>,
				valueIsConstant: Boolean
			): Representation<K> = when
			{
				// Absence is the identity here: merging with a kind the other
				// record was not held in must not make the result held in it,
				// so the surviving representation is answered unchanged.
				first.isAbsent -> second
				second.isAbsent -> first
				else ->
				{
					// Just concatenate the two lists, as this essentially
					// preserves earliest definition order.
					val definitions = first.definitions + second.definitions
					Representation(
						kind = first.kind,
						definitions = definitions,
						postponedInstruction = when
						{
							// Something already writes it in this kind.
							definitions.isNotEmpty() -> null
							valueIsConstant -> null
							// In theory, if both postponed instructions are
							// present we could decide which to keep and augment
							// with the other synonym, but for now we can just
							// choose arbitrarily, since they yield equivalent
							// values.
							else -> first.postponedInstruction
								?: second.postponedInstruction
						})
				}
			}
		}
	}

	/**
	 * A kind-scoped view of a [ValueState], presenting that record as though
	 * the value existed only in this one [RegisterKind]: the members in that
	 * kind's spelling, the restriction projected into that kind, and only that
	 * kind's registers and postponed instruction.
	 *
	 * A view is a transient wrapper, obtained from the [ValueState] it
	 * describes and discarded.  Never store one: a manifest replaces a record
	 * wholesale on every update, so a retained view silently describes a
	 * value's past.
	 *
	 * @property state
	 *   The [ValueState] being viewed.
	 * @property kind
	 *   The [RegisterKind] this view is scoped to.
	 */
	class Constraint<K: RegisterKind<K>> internal constructor(
		val state: ValueState,
		val kind: K)
	{
		/**
		 * This kind's [Representation] of the value, which is
		 * [absent][Representation.isAbsent] if the value is not held in this kind.
		 */
		val representation: Representation<K>
			get() = kind.representationIn(state)

		/** The [L2Register]s of this kind that hold the value. */
		val definitions: List<L2Register<K>> get() = representation.definitions

		/**
		 * The postponed [L2Instruction] that would populate this value in this
		 * kind.  It has *not* yet been emitted, and might never be, if the
		 * values it populates are never read.
		 */
		val postponedInstruction: L2Instruction?
			get() = representation.postponedInstruction

		/** The [TypeRestriction] bounding the value, in this kind. */
		val restriction: TypeRestriction
			get() = state.restriction

		/** The [L2SemanticValue]s naming this value, spelled in this kind. */
		val members: Set<L2SemanticValue> get() = state.members

		/** An [L2Synonym] view of this constraint's [members]. */
		val synonym: L2Synonym get() = state.synonym

		/**
		 * Answer the set of semantic values that have been defined in this kind,
		 * meaning they appear in registers of this kind with at least one write
		 * operand.
		 *
		 * Definedness is necessarily per-kind: a value can be available as an
		 * int with no boxed register yet, so the aggregate over all kinds is a
		 * different question, and conflating the two would treat "available
		 * boxed" and "available as an int" as the same thing.
		 *
		 * @return
		 *   The set of [L2SemanticValue]s that have visible definitions.
		 */
		fun definedSemanticValues(): Set<L2SemanticValue> =
			representation.definedMembers()

		override fun toString(): String = buildString {
			when
			{
				definitions.isEmpty() -> append("⌛️️POSTPONED")
				restriction.isImpossible -> append("⛔️Impossible")
				else -> definitions.joinTo(this)
			}
			append(": ")
			append(restriction)
		}

		/**
		 * Answer `true` iff this constraint is impossible to satisfy with any
		 * value.
		 */
		val isImpossible get() = state.isImpossible
	}

	/**
	 * A mutable variation of [Constraint], suitable for use in circumstances
	 * where a value's record needs to be updated by a lambda, without breaking
	 * the sharing of [ValueState]s between manifests.
	 *
	 * The builder is scoped to the same [RegisterKind] as the [Constraint] it
	 * was modeled on, so [toValueState] replaces only that kind's
	 * [Representation]; see [ValueState.updated].
	 *
	 * @param constraint
	 *   The [Constraint] view on which to model the mutable builder.
	 * @param synonym
	 *   The [L2Synonym] for which this constraint is being built.  This can be
	 *   quite convenient during constraint updates.
	 */
	class ConstraintBuilder<K: RegisterKind<K>>(
		constraint: Constraint<K>,
		val synonym: L2Synonym)
	{
		/** Capture the original [ValueState]. */
		private val originalState = constraint.state

		/** Extract the [Constraint]'s [kind]. */
		private val kind = constraint.kind

		/** Track changes. */
		private var modified = false

		var definitions: List<L2Register<K>> = constraint.definitions
			set(value)
			{
				if (value != field)
				{
					field = value
					modified = true
				}
			}

		var restriction: TypeRestriction = constraint.restriction
		set(value)
		{
			if (value != field)
			{
				field = value
				modified = true
			}
		}

		var postponedInstruction: L2Instruction? = constraint.postponedInstruction
			set(value)
			{
				if (value != field)
				{
					field = value
					modified = true
				}
			}

		/**
		 * Either reuse the original [ValueState] if the receiver hasn't been
		 * [modified], or synthesize a new one.
		 */
		fun toValueState(): ValueState = when
		{
			!modified -> originalState
			else -> originalState.updated(
				synonym.semanticValues(),
				restriction,
				Representation(kind, definitions, postponedInstruction))
		}
	}


	/**
	 * The [ValueClass] that each [L2SemanticValue] currently belongs to.
	 * The class recorded here may have been merged away since, so every read
	 * must go through [resolve]; use [classOrNull] or [classFor] rather than
	 * indexing this map directly.
	 *
	 * Unboxed semantic values can be looked up by their equivalent boxed form.
	 *
	 * It is always present, even in the modes where the graph is held together
	 * by registers and semantic values are not tracked.  Whether to consult it
	 * is decided by [caresAboutSemanticValues], which follows the current
	 * [mode] – the map's mere existence cannot decide it, since [mode] is
	 * reassigned on a long-lived manifest as the phases progress, while this
	 * map is created once.
	 */
	private val classOf: MutableMap<L2SemanticValue, ValueClass> =
		mutableMapOf()

	/**
	 * Where merged [ValueClass]es forward to.  This is the union-find, and it
	 * lives here – per manifest – rather than in [ValueClass], so that a merge
	 * performed along one edge of a branch cannot be seen along the other.
	 *
	 * It grows monotonically within a manifest and is compacted lazily by the
	 * path compression in [resolve].
	 */
	private val forward: MutableMap<ValueClass, ValueClass>

	/**
	 * The [ValueClass] holding the [TypeTag] extracted from each base
	 * [ValueClass], where such a value is known.  This is the *forward* edge of
	 * the derivation relation.
	 *
	 * Derived values such as `Tag(x)` currently name their base by spelling it
	 * into an [L2SemanticExtractedTag], which relates them to `x` only by the
	 * shape of the semantic value.  Nothing structural stops `x` from being
	 * dropped while `Tag(x)` survives – see [checkDerivedValuesHaveTheirBases].
	 * These edges are the beginning of making that relation real, so that a
	 * derived class cannot outlive the class it describes.
	 */
	@Deprecated("Use ValueState.tagClass")
	private val tagOf: MutableMap<ValueClass, ValueClass>

	/**
	 * The [ValueClass] holding the [ObjectLayoutVariant] id extracted from each
	 * base [ValueClass], where such a value is known.  The variant counterpart
	 * of [tagOf].
	 */
	@Deprecated("Use ValueState.variantClass")
	private val variantIdOf: MutableMap<ValueClass, ValueClass>

	/**
	 * The base [ValueClass] that each derived [ValueClass] describes – the
	 * *backward* edge of [tagOf] and [variantIdOf].  Narrowing a derived value
	 * constrains its base, so the relation has to be navigable in both
	 * directions.
	 */
	@Deprecated("Use ValueState.isTagOfClasses/isVariantOfClasses")
	private val derivedFrom: MutableMap<ValueClass, ValueClass>

	/**
	 * A map from each [ValueClass] to the [ValueState] recording what this
	 * manifest knows about that value: its membership, its [TypeRestriction],
	 * and one [Representation] – the [L2Register]s holding it and any postponed
	 * [L2Instruction] – for each [RegisterKind] it is held in.
	 *
	 * A [ValueState] answers only questions that are independent of
	 * [RegisterKind]; anything kind-specific is asked of a [Constraint] view
	 * obtained from it.
	 *
	 * Keys are always resolved; a class that has been merged away has no entry.
	 */
	private val states: MutableMap<ValueClass, ValueState>

	/**
	 * An index from the [ValueClass] that some postponed [L2Instruction]
	 * *reads*, to the [ValueClass]es under which those postponed instructions
	 * are recorded.  It lets a narrowing of the read find the postponed
	 * instructions that consume it, without scanning every [Constraint].
	 *
	 * This is keyed by [ValueClass] rather than by [L2SemanticValue] for a
	 * reason that is easy to get wrong: narrowing is reported for whichever
	 * member of a synonym happens to be [L2Synonym.pickSemanticValue], which
	 * is very often *not* the member that the postponed instruction reads.  An
	 * index keyed by the exact semantic value silently misses in that case, and
	 * the specialization never happens.
	 *
	 * Keys are merged in [forwardClass]; values are resolved lazily on use, so
	 * a merge costs one map operation rather than a scan.
	 */
	private val postponedReaders:
		MutableMap<ValueClass, MutableSet<ValueClass>>

	/**
	 * The number of constraints in the manifest that are impossible, which is
	 * the case when the constraint's restriction is [bottomRestriction].
	 */
	private var impossibleRestrictionCount = 0

	/**
	 * The depth of nested [renarrowPostponedConsumersOf] activations, used to
	 * bound the mutual recursion between narrowing a value and re-narrowing the
	 * postponed instructions that consume it.  Narrowing is monotone, so the
	 * recursion always terminates, but a deeply chained set of postponed
	 * instructions could otherwise recurse further than is comfortable.
	 */
	private var renarrowDepth = 0

	/**
	 * The [ValueClass.id] to give the next [ValueClass] created here.
	 *
	 * Numbering is per manifest rather than global, since a [ValueClass] never
	 * escapes the manifest that created it.  It is carried across by
	 * [adoptEverythingFrom], so a manifest cloned or inherited from another keeps
	 * minting ids that are distinct from the ones it inherited.
	 */
	private var nextValueClassId = 1

	/**
	 * Answer a [ValueClass] distinct from every other in this manifest.
	 *
	 * @return
	 *   The new [ValueClass].
	 */
	private fun newValueClass() = ValueClass(nextValueClassId++)

	/**
	 * Answer whether there are any impossible restrictions in this manifest.
	 */
	val hasImpossibleRestriction: Boolean get() = impossibleRestrictionCount > 0

	/**
	 * First [resolve] the given [valueClass], then look that up in the map of
	 * [states].  It must be present.
	 */
	private fun stateFromClass(valueClass: ValueClass): ValueState =
		states[resolve(valueClass)]!!

	/**
	 * Repeatedly reduce the postponed instructions until no more reductions are
	 * available.
	 */
	fun rewriteAllPostponed()
	{
		do
		{
			var changed = false
			states.entries.toList().forEach { (valueClass, state) ->
				// A previous rewrite may have disrupted the synonym structure,
				// so we have to check it here.  It can only have had an effect
				// if it answered true, in which case we'll do another pass to
				// make sure we get every rewrite that we can.
				if (valueClass in states)
				{
					state.representations.forEach { representation ->
						representation.postponedInstruction?.let { instruction ->
							val newChange = instruction.run {
								rewritePostponed(state.synonym)
							}
							changed = newChange or changed
						}
					}
				}
			}
		} while (changed)
	}

	/**
	 * In later passes, the control flow graph is effectively held together by
	 * registers, rather than semantic values.  Answer whether we still care
	 * about semantic values in this manifest.
	 */
	val caresAboutSemanticValues: Boolean
		get() = mode == BySemanticValue

	/**
	 * Create a new empty manifest.
	 *
	 * @param mode
	 *   The [GenerationMode] that interprets semantic values and registers.
	 */
	constructor(mode: GenerationMode)
	{
		this.mode = mode
		forward = mutableMapOf()
		tagOf = mutableMapOf()
		variantIdOf = mutableMapOf()
		derivedFrom = mutableMapOf()
		states = mutableMapOf()
		postponedReaders = mutableMapOf()
	}

	/**
	 * Copy an existing manifest.  Clone the maps; the [ValueState]s they contain
	 * are immutable and are shared rather than copied.
	 *
	 * @param original
	 *   The original [L2ValueManifest].
	 */
	constructor(original: L2ValueManifest) : this(original.mode)
	{
		adoptEverythingFrom(original)
	}

	/**
	 * Copy every piece of state that another manifest holds into the receiver,
	 * which must be empty.
	 *
	 * This is the one place that knows what a manifest is made of, so that a
	 * manifest inherited wholesale cannot quietly lose part of what the original
	 * knew.  Losing the derivation edges this way was invisible, because
	 * [derivedFormOf] falls back to a search when an edge is missing and
	 * therefore still found the right answer, just more slowly and only while
	 * that fallback exists.
	 *
	 * @param original
	 *   The manifest to copy.  It is left unchanged: the maps are cloned, and
	 *   the [ValueState]s within them are immutable and safely shared.
	 */
	private fun adoptEverythingFrom(original: L2ValueManifest)
	{
		classOf.putAll(original.classOf)
		forward.putAll(original.forward)
		tagOf.putAll(original.tagOf)
		variantIdOf.putAll(original.variantIdOf)
		derivedFrom.putAll(original.derivedFrom)
		states.putAll(original.states)
		original.postponedReaders.forEach { (readClass, consumers) ->
			postponedReaders[readClass] = consumers.toMutableSet()
		}
		impossibleRestrictionCount = original.impossibleRestrictionCount
		// Continue the original's numbering, so that a class minted here cannot
		// collide with one inherited from it.
		nextValueClassId = original.nextValueClassId
	}

	/**
	 * Produce a manifest based on the reciver, but without any information
	 * about registers or postponed instructions.  This will be plugged into a
	 * block's [L2BasicBlock.postPhiMap].
	 *
	 * Leave out constants that have no other semantic value.
	 */
	fun extractPostPhiMap(): Map<L2Synonym, TypeRestriction> =
		states.values
			// Leave off synonyms built from only a constant.
			.filterNot {
				it.members.singleOrNull().notNullAnd { isConstant }
			}
			.associate { L2Synonym(it.members) to it.restriction }

	/**
	 * Record an [L2Instruction] suitable for subsequent emission, if necessary,
	 * to produce its output values.  The sole writeOperand should have no
	 * target semantic values, as these will be supplied by the current synonym
	 * at emission time.
	 *
	 * The writeOperand's
	 *
	 * @param semanticValue
	 *   A semantic value that will be defined by the instruction.  This is used
	 *   to locate the synonym under which to record the [instruction].
	 * @param instruction
	 *   The instruction to record for later emission.  It must have no target
	 *   semantic values in its sole writeOperand.
	 */
	fun recordPostponedInstruction(
		semanticValue: L2SemanticValue,
		instruction: L2Instruction)
	{
		assert(instruction.canBePostponed)
		assert(!instruction.hasBeenEmitted)
		val originalWrite = instruction.writeOperands.single()
		assert(originalWrite.semanticValues().isEmpty())
		if (instruction is L2_MOVE<*>)
		{
			val source = instruction.source.semanticValue()
			// Moves should pre-merge the source and destination synonyms.
			agglomerateSynonym(
				setOf(semanticValue, source),
				originalWrite.restriction())
			// Never replace an existing postponed instruction with a new move.
			// The existing instruction will automatically write to any semantic
			// values that get added to the synonym.
			if (postponedInstructionFor(semanticValue, originalWrite.kind) != null) return
		}
		installOrFoldPostponed(semanticValue, instruction)
	}

	/**
	 * Either record the given [L2Instruction] as the postponed instruction for
	 * the synonym containing [semanticValue], or – if its result is already
	 * known to be a particular constant – skip it entirely, letting the synonym
	 * be folded into the corresponding constant synonym instead.  In the latter
	 * case [L2Generator.ensureDefinedOrEmitMove] will subsequently emit a
	 * constant move, since it tests for a constant restriction before falling
	 * back to a postponed instruction.
	 *
	 * This is shared between the original recording of a postponed instruction
	 * and the re-recording performed by [renarrowPostponedConsumersOf] once a
	 * narrowing has made the instruction's result more precise, so that the two
	 * cannot disagree about when folding happens.
	 *
	 * @param semanticValue
	 *   A semantic value that the instruction would define.
	 * @param instruction
	 *   The postponable instruction.
	 */
	private fun installOrFoldPostponed(
		semanticValue: L2SemanticValue,
		instruction: L2Instruction)
	{
		assert(instruction !is L2_MOVE<*>)
		assert(instruction !is L2_MOVE_CONSTANT<*, *>)
		val originalWrite = instruction.writeOperands.single()
		val constant = originalWrite.restriction().constantOrNull
		if (constant != null)
		{
			// Ensure postponable instructions that produce a constant simply
			// augment an existing synonym, or at worst become a constant move.
			agglomerateSynonym(
				setOf(constant(constant)),
				originalWrite.restriction())
			return
		}
		updateConstraint(
			semanticValueToSynonym(semanticValue),
			originalWrite.kind
		) {
			postponedInstruction = instruction
		}
		// Index the instruction under the class of everything it reads, so
		// that narrowing any member of those classes can find it again.
		val target = classFor(semanticValue)
		instruction.readOperands.forEach { read ->
			classOrNull(read.semanticValue())?.let { readClass ->
				postponedReaders
					.getOrPut(readClass, ::mutableSetOf)
					.add(target)
			}
		}
	}

	/**
	 * The restriction on [narrowed] has just been tightened.  Find any
	 * postponed [L2Instruction]s that read it, re-derive their read
	 * restrictions, and if that makes an instruction's result more precise,
	 * replace the postponed instruction with the narrowed clone and tighten the
	 * restriction on the synonym it will populate.
	 *
	 * That tightening re-enters [updateRestriction], which is what ultimately
	 * introduces an [L2SemanticConstant] and lets the value be emitted as a
	 * constant move rather than as the original computation.
	 *
	 * @param narrowed
	 *   The [L2SemanticValue] whose restriction just became stronger.
	 */
	private fun renarrowPostponedConsumersOf(narrowed: L2SemanticValue)
	{
		if (!caresAboutSemanticValues) return
		// Bound the mutual recursion with narrowing.  Chains of postponed
		// instructions are short; anything deeper simply waits until the value
		// is actually needed, when the emit-time refresh handles it.
		val narrowedClass = classOrNull(narrowed) ?: return
		val targets = postponedReaders[narrowedClass]
		if (targets === null) return
		// Copy, since re-narrowing can modify the index and the constraints.
		renarrowDepth++
		try
		{
			targets.toList().forEach { staleTarget ->
				// Values are resolved lazily, and the target may since have
				// been forgotten or had its instruction emitted.
				val targetClass = resolve(staleTarget)
				val state = states[targetClass] ?: return@forEach
				// A value can have a postponed instruction per kind, and the
				// narrowing may sharpen any of them.
				state.representations.forEach inner@ { representation ->
					val postponed =
						representation.postponedInstruction ?: return@inner
					if (postponed.writeOperands.size != 1) return@inner
					val narrowedClone = postponed.narrowedForManifest(this)
					if (narrowedClone === null) return@inner
					val implied = narrowedClone.impliedWriteRestriction(
						narrowedClone.readOperands.map { it.restriction() })
					narrowedClone.writeOperands.single().restrict { implied }
					val synonym = state.synonym
					val target = synonym.pickSemanticValue()
					updateConstraint(synonym, representation.kind) {
						postponedInstruction = narrowedClone
					}
					// isStrongerThan is reflexive, so test for an actual change.
					val existing = restrictionFor(target)
					if (implied.intersection(existing) != existing)
					{
						// This may make the value constant, in which case the
						// synonym gains an L2SemanticConstant and the postponed
						// instruction becomes unnecessary.
						updateRestriction(target) { implied }
						if (restrictionFor(target).isConstant)
						{
							updateConstraint(
								semanticValueToSynonym(target),
								representation.kind
							) {
								postponedInstruction = null
							}
						}
					}
				}
			}
		}
		finally
		{
			renarrowDepth--
		}
	}

	/**
	 * Answer a set of [InstructionEquivalence]s, one for each postponed
	 * [L2Instruction] in the entire manifest, capturing the [L2Synonym] that
	 * it will populate.
	 *
	 * @return
	 *   A [Set] of [InstructionEquivalence]s.
	 */
	fun allPostponedInstructions(): Set<InstructionEquivalence> =
		states.values.flatMap { state ->
			state.representations.mapNotNull { representation ->
				representation.postponedInstruction?.let { instruction ->
					InstructionEquivalence(instruction, state.synonym)
				}
			}
		}.toSet()

	/**
	 * If there's a postponed instruction for the synonym containing the given
	 * semantic value, answer that instruction, otherwise `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   The postponed [L2Instruction] for the given semantic value, or `null`
	 *   if none exists.
	 */
	fun <K: RegisterKind<K>> postponedInstructionFor(
		semanticValue: L2SemanticValue,
		kind: K
	): L2Instruction? = constraint(semanticValue, kind).postponedInstruction

	/**
	 * If there's a postponed instruction for the synonym containing the given
	 * semantic value, answer that instruction, removing it from the manifest.
	 * Otherwise answer `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param kind
	 *   The [RegisterKind] that would be produced by the instruction to be
	 *   removed.
	 * @return
	 *   The postponed [L2Instruction] for the given semantic value, or `null`
	 *   if none existed.
	 */
	fun <K: RegisterKind<K>> removePostponedInstructionFor(
		semanticValue: L2SemanticValue,
		kind: K
	): L2Instruction?
	{
		if (!caresAboutSemanticValues) return null
		if (!hasSemanticValue(semanticValue)) return null
		val instruction = postponedInstructionFor(semanticValue, kind)
		if (instruction == null)
		{
			// Synthesize a postponed instruction to return.
			val values = stateOrNull(semanticValue)!!.members
			val defined = getDefinitions(semanticValue, kind)
				.flatMap(L2Register<*>::definitions)
				.flatMap(L2WriteOperand<*>::semanticValues)
			val notDefined = values - defined
			val restriction = restrictionFor(semanticValue)
			return when
			{
				// Everything is defined, so there's no instruction.
				notDefined.isEmpty() -> null
				restriction.isImpossible ->
				{
					// A contradiction was discovered.  We can't do a move from
					// it, but it doesn't matter – we just have to ensure the
					// manifest has recorded a contradiction for the notDefined
					// values, which we accomplish by just augmenting the
					// synonym.  There won't be an *instruction* that would
					// reconstruct this on subsequent regenerations, but the
					// impossible restriction will avoid code generation for
					// this basic block anyhow.
					agglomerateSynonym(values, restriction)
					L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW()
				}
				// Return a move from a defined value.
				defined.isNotEmpty() ->
					kind.dynamicMove(
						defined.first(), emptySet(), this, restriction)
				restriction.isConstant ->
					kind.moveConstant(
						restriction.constantOrNull!!,
						emptySet())
				else -> null
			}
		}
		updateConstraint(semanticValueToSynonym(semanticValue), kind) {
			postponedInstruction = null
		}
		return instruction
	}

	/** Remove all postponed instructions. */
	fun clearPostponedInstructions()
	{
		postponedReaders.clear()
		states.entries.forEach { entry ->
			entry.setValue(entry.value.withoutPostponed())
		}
	}

	/**
	 * Update the [Constraint] associated with the given [L2Synonym].
	 *
	 * @param synonym
	 *   The [L2Synonym] to look up.
	 * @param body
	 *   What to perform with the looked up [Constraint] as the receiver.
	 */
	private fun <K: RegisterKind<K>, Result> updateConstraint(
		synonym: L2Synonym,
		kind: K,
		body: ConstraintBuilder<K>.() -> Result
	): Result
	{
		val valueClass = classOrNull(synonym.pickSemanticValue())
			?: newValueClass().also { fresh ->
				bind(synonym.semanticValues(), fresh)
			}
		var state = states[valueClass]
		if (state == null)
		{
			state = ValueState.newState(
				kind,
				members = synonym.semanticValues(),
				definitions = emptyList(),
				restriction = bottomRestriction,
				postponedInstruction = null)
			impossibleRestrictionCount++
			bind(synonym.semanticValues(), valueClass)
		}
		val builder = ConstraintBuilder(state.viewFor(kind), synonym)
		val oldRestriction = state.restriction
		val result = builder.body()
		assert(caresAboutSemanticValues || builder.postponedInstruction == null)
		states[valueClass] = builder.toValueState()
		val newRestriction = states[valueClass]!!.restriction
		if (newRestriction != oldRestriction)
		{
			if (oldRestriction == bottomRestriction)
				impossibleRestrictionCount--
			if (newRestriction == bottomRestriction)
				impossibleRestrictionCount++
			propagateForRestrictionChange(synonym.pickSemanticValue())
		}
		return result
	}

	/**
	 * Find the [ValueState] associated with the given [valueClass], and replace
	 * it with one having a restriction intersected with the value returned by
	 * [body].
	 */
	private fun updateRestriction(
		valueClass: ValueClass,
		body: TypeRestriction.() -> TypeRestriction)
	{
		// TODO; A bit cheesy for now – this should be primary and the one
		//  taking a semanticValue should call it.
		updateRestriction(
			stateFromClass(valueClass).synonym.pickSemanticValue(),
			body)
	}

	/**
	 * Update the [TypeRestriction] in the [Constraint] associated with the
	 * [L2Synonym] containing the given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   How to transform the restriction, taking the old one as the receiver
	 *   and answering the new one.
	 */
	fun updateRestriction(
		semanticValue: L2SemanticValue,
		body: TypeRestriction.() -> TypeRestriction)
	{
		if (!hasSemanticValue(semanticValue))
		{
			val equivalent = equivalentSemanticValue(semanticValue)
			introduceSynonym(
				setOf(semanticValue), semanticValue.defaultRestriction)
			if (equivalent != null)
			{
				mergeExistingSemanticValues(semanticValue, equivalent)
			}
		}
		// After the phase where we replace constant-valued registers with
		// definitionless constants, we may still encounter places where
		// comparison operations are attempting to narrow the restriction.
		// Ignore such attempts.  In fact, ignore anything that attempts to
		// narrow the restriction on a *semantic constant*.
		val valueClass = classOrNull(semanticValue)
			?: newValueClass().also { fresh ->
				bind(setOf(semanticValue), fresh)
			}
		var state = states[valueClass]
		if (state == null)
		{
			state = newEmptyState(
				members = setOf(semanticValue),
				restriction = bottomRestriction)
			impossibleRestrictionCount++
			bind(state.members, valueClass)
		}
		val oldRestriction = state.restriction
		val newRestriction = oldRestriction.intersection(oldRestriction.body())
		if (newRestriction != oldRestriction)
		{
			state = ValueState(
				members = state.members,
				restriction = newRestriction,
				boxedRepresentation = state.boxedRepresentation,
				intRepresentation = state.intRepresentation,
				floatRepresentation = state.floatRepresentation)
			states[valueClass] = state
		}
		if (newRestriction != oldRestriction)
		{
			if (oldRestriction == bottomRestriction)
				impossibleRestrictionCount--
			if (newRestriction == bottomRestriction)
				impossibleRestrictionCount++
			propagateForRestrictionChange(semanticValue)
		}
		newRestriction.constantOrNull?.let { constant ->
			val semanticConstant = constant(constant)
			if (semanticConstant !in
				semanticValueToSynonym(semanticValue).semanticValues())
			{
				agglomerateSynonym(
					listOf(semanticValue, semanticConstant),
					newRestriction)
			}
		}
	}

	/**
	 * The restriction for a [semanticValue] has just changed.  Find any related
	 * semantic values (boxed/unboxed, tags, object variants, primitive
	 * identities, covariant/contravariant relationships), and attempt to
	 * further constrain them.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose restriction was just changed.
	 */
	private fun propagateForRestrictionChange(
		semanticValue: L2SemanticValue)
	{
		val restriction = restrictionFor(semanticValue)
		// Any postponed instruction that reads this value may now compute a
		// more precise result – possibly a constant, which lets it be emitted
		// as a constant move instead of as the original computation.
		renarrowPostponedConsumersOf(semanticValue)
		// If we're at the point that we're only considering registers, don't
		// automatically introduce constant moves.
		if (caresAboutSemanticValues)
		{
			restriction.constantOrNull?.let { constant ->
				val semanticConstant= constant(constant)
				val synonym = semanticValueToSynonym(semanticValue)
				val constSynonym: L2Synonym? =
					semanticValueToSynonymOrNull(semanticConstant)
				when (constSynonym) {
					null -> extendSynonym(synonym, semanticConstant)
					else -> mergeExistingSemanticValues(
						semanticValue, semanticConstant)
				}
			}
		}
		stateOrNull(semanticValue)?.let { state ->
			state.tagClass?.let { tagClass ->
				updateRestriction(tagClass) {
					val tagRangeType = when
					{
						// Original value is impossible, so the tag is also
						// impossible.
						restriction == bottomRestriction -> bottom
						else -> restriction.type.instanceTag.tagRangeType
					}
					intersectionWithType(tagRangeType)
				}
			}
		}
		stateOrNull(semanticValue)?.let { state ->
			state.variantClass?.let { variantClass ->
				updateRestriction(variantClass) {
					val const = constantOrNull
					val tagRangeType = when
					{
						// Original value is impossible, so the variant is also
						// impossible.
						restriction == bottomRestriction -> bottom
						// Exact objectMeta is known – so is the variant.
						const != null && const.isInstanceMeta ->
							instanceType(
								fromInt(
									const.instance.objectTypeVariant.variantId))
						// Exact objectType is known – so is the variant.
						const != null ->
							instanceType(
								fromInt(const.objectTypeVariant.variantId))
						else -> i31
					}
					intersectionWithType(tagRangeType)
				}
			}
		}
		stateOrNull(semanticValue)?.let { state ->
			state.isTagOfClasses.forEach { sourceValueClass ->
				// Propagate the tighter tag restriction to a tighter
				// restriction on the source object.
				updateRestriction(sourceValueClass) {
					restrictionForTagRestriction(restriction)
				}
			}
		}
		stateOrNull(semanticValue)?.let { state ->
			// Only strengthen the source value if the variant has been narrowed
			// to a constant.
			val variantId = state.restriction.constantOrNull ?: return@let
			val variant = variantFromId(variantId.extractInt) ?: return@let
			state.isVariantOfClasses.forEach { sourceValueClass ->
				// Propagate the tighter variant restriction to a tighter
				// restriction on the source object/objectType.
				updateRestriction(sourceValueClass) {
					when
					{
						restriction.containedByType(mostGeneralObjectType) ->
							restrictionForType(
								variant.mostGeneralObjectType
							).intersectionWithObjectVariant(variant)
						restriction.containedByType(mostGeneralObjectMeta) ->
							restrictionForType(
								variant.mostGeneralObjectMeta
							).intersectionWithObjectTypeVariant(variant)
						else -> restriction
					}
				}
			}
		}
		// Let every primitive invocation in the synonym have a chance to narrow
		// related restrictions.
		stateOrNull(semanticValue)?.let { state ->
			state.members
				.filterIsInstance<L2SemanticPrimitiveInvocation>()
				.forEach { primInvocation ->
					primInvocation.primitive.propagateManifestRestrictions(
						primInvocation.argumentSemanticValues,
						this,
						state.restriction
					)
				}
		}
	}

	/**
	 * Update the [List] of [L2Register] definitions in the [Constraint]
	 * associated with the [L2Synonym] containing the given [L2SemanticValue].
	 * Note that [Constraint]s are shared between manifests, so always update a
	 * copy.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   How to transform the list of definitions, taking the old list as the
	 *   receiver and answering the new list.
	 */
	fun <K: RegisterKind<K>> updateDefinitions(
		semanticValue: L2SemanticValue,
		kind: K,
		body: List<L2Register<K>>.() -> List<L2Register<K>>
	): Unit = updateConstraint(semanticValueToSynonym(semanticValue), kind) {
		definitions = definitions.body()
	}

	/**
	 * Verify that this manifest is internally consistent.
	 */
	fun check()
	{
		if (!deepManifestDebugCheck) return
		checkUniqueConstantSynonyms()
		if (caresAboutSemanticValues)
		{
			assert(
				classOf.values.mapTo(mutableSetOf(), ::resolve) ==
					states.keys)
			checkDerivedValuesHaveTheirBases()

			// Check each value's representations for consistency with its
			// synonym, one kind at a time. Postponed instructions are now
			// source-only (no explicit targets), with targets derived
			// contextually from the synonym's not-defined semantic values. There
			// is no special case for constant restrictions - they require an
			// explicit postponed constant move instruction.
			for (constraint in states.values.flatMap(ValueState::views))
			{
				val synonym = constraint.synonym
				// Collect the semantic values that have been defined.
				val defined = constraint.definedSemanticValues()

				// All defined semantic values must be in the synonym.
				assert(synonym.semanticValues().containsAll(defined))
				{
					val extraValues = defined - synonym.semanticValues()
					buildString {
						append("Constraint has defined semantic values not " +
							"in synonym.")
						append("\n  Synonym: ${synonym.semanticValues()}")
						append("\n  Extra defined values: $extraValues")
					}
				}

				// Compute which semantic values in the synonym are not yet
				// defined (lack visible defining writes).
				val notDefined = synonym.semanticValues() - defined

				// If there are not-defined semantic values, there must be an
				// explicit postponed instruction.  If there no defined values,
				// it must be a constant restriction and a constant-move,
				// otherwise it must be a regular move from a defined value.
				var postponed = constraint.postponedInstruction

				// Check for problems with the postponed instruction.
				postponed?.let {
					assert(it.writeOperands.single().semanticValues().isEmpty())
				}
				when
				{
					notDefined.isEmpty() -> assert(postponed == null)
					defined.isNotEmpty() ->
					{
						// Allow the postponed instruction to be null, to allow
						// subsequent code to set it up.
						if (postponed is L2_MOVE<*>)
						{
							assert(postponed.source.semanticValue() in defined)
						}
					}
					postponed == null -> Unit
					else -> assert(postponed !is L2_MOVE<*>)
				}
			}
		}
		// Aggregate: every register in the manifest, of every kind.
		val registers = states.values.flatMap(ValueState::allDefinitions)
		if (mode !is WithFixedRegisterMap)
		{
			assert(registers.size == registers.toSet().size)
		}
		// A synonym of mixed kind used to be checked for here.  It is no longer
		// representable: members are stored as canonical boxed values, and each
		// kind's spelling of them is synthesized by the corresponding
		// [Constraint], so every synonym this manifest can hand out is of a
		// single kind by construction.
		val count = states.values.count(ValueState::isImpossible)
		assert(count == impossibleRestrictionCount)
		{
			"Incorrect value for hasImpossibleRestriction."
		}
	}

	/**
	 * Answer the set of [L2SemanticValue]s that are known to this manifest,
	 * whether currently assigned to registers, or ready to be populated by a
	 * postponed instruction, automatic move, or automatic constant move.
	 */
	fun liveOrPostponedSemanticValues(): Set<L2SemanticValue> =
		states.values
			.flatMap(ValueState::views)
			.filter { constraint ->
				constraint.postponedInstruction != null
					|| constraint.restriction.isConstant
					|| constraint.definitions.any { reg ->
						reg.definitions().isNotEmpty()
					}
					|| constraint.members
						.any(L2SemanticValue::isConstant)
			}
			.flatMapTo(mutableSetOf(), Constraint<*>::members)
			.toSet()

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym]
	 * that's bound to it.  Answer `null` if it's not found.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @return
	 *   The [L2Synonym] bound to that semantic value, or `null`.
	 */
	fun semanticValueToSynonymOrNull(
		semanticValue: L2SemanticValue
	): L2Synonym? = stateOrNull(semanticValue)?.synonym

	/**
	 * Answer the [ValueClass] that the given class has been merged into,
	 * following and compacting the chain of [forward] links.
	 *
	 * @param valueClass
	 *   A possibly stale [ValueClass].
	 * @return
	 *   The live [ValueClass] representing the same value.
	 */
	fun resolve(valueClass: ValueClass): ValueClass
	{
		var target = forward[valueClass] ?: return valueClass
		while (true)
		{
			target = forward[target] ?: break
		}
		// Path compression.  Rewrite every link on the way to the root.
		var link = valueClass
		while (link != target)
		{
			val next = forward[link]!!
			forward[link] = target
			link = next
		}
		return target
	}

	/**
	 * Answer the live [ValueClass] of the given [L2SemanticValue], or
	 * `null` if this manifest doesn't know the value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [ValueClass], or `null`.
	 */
	private fun classOrNull(
		semanticValue: L2SemanticValue
	): ValueClass? = classOf[semanticValue]?.let(::resolve)

	/**
	 * Answer the live [ValueClass] of the given [L2SemanticValue].  Fail
	 * if the value is unknown to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [ValueClass].
	 */
	private fun classFor(
		semanticValue: L2SemanticValue
	): ValueClass = classOrNull(semanticValue)!!

	/**
	 * Answer the [ValueState] recording the given [L2SemanticValue], or
	 * `null` if this manifest doesn't know the value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [ValueState], or `null`.
	 */
	fun stateOrNull(
		semanticValue: L2SemanticValue
	): ValueState? = classOrNull(semanticValue)?.let(states::get)

	/**
	 * Bind the given [L2SemanticValue]s to the given [ValueClass],
	 * replacing any prior binding.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to bind.
	 * @param valueClass
	 *   The [ValueClass] to bind them to.
	 */
	private fun bind(
		semanticValues: Iterable<L2SemanticValue>,
		valueClass: ValueClass)
	{
		semanticValues.forEach { semanticValue ->
			classOf[semanticValue] = valueClass
			linkDerivation(semanticValue, valueClass)
		}
	}

	/**
	 * If the given [L2SemanticValue] is a derived value – a [TypeTag] or an
	 * [ObjectLayoutVariant] id extracted from some base value – record the edges
	 * relating [valueClass] to the class of that base.
	 *
	 * This is called for every member as it is bound, which is the one place
	 * every class membership passes through, so the edges cannot drift out of
	 * step with the memberships they describe.
	 *
	 * The base may not be known to this manifest, which is the very
	 * inconsistency [checkDerivedValuesHaveTheirBases] reports; in that case
	 * there is no edge to record and the situation is left for that assertion to
	 * complain about.
	 *
	 * @param semanticValue
	 *   The member just bound.
	 * @param valueClass
	 *   The [ValueClass] it was bound to.
	 */
	private fun linkDerivation(
		semanticValue: L2SemanticValue,
		valueClass: ValueClass
	) = semanticValue.recordDerivationIn(this, valueClass)

	/**
	 * Record that [derivedClass] holds the [TypeTag] of [base].  Called by
	 * [L2SemanticExtractedTag] as it is bound; see
	 * [L2SemanticValue.recordDerivationIn].
	 *
	 * @param base
	 *   The [L2SemanticValue] whose tag this is.
	 * @param derivedClass
	 *   The [ValueClass] holding the tag.
	 */
	fun recordTagDerivation(
		base: L2SemanticValue,
		derivedClass: ValueClass
	) = recordDerivation(tagOf, base, derivedClass) { tagRestriction ->
		// A known tag is a fact about the base, so a base introduced here starts
		// from what its tag already says rather than from nothing.
		restrictionForTagRestriction(tagRestriction)
	}

	/**
	 * Record that [derivedClass] holds the [ObjectLayoutVariant] id of [base].
	 * Called by [L2SemanticObjectVariantId] as it is bound; see
	 * [L2SemanticValue.recordDerivationIn].
	 *
	 * @param base
	 *   The [L2SemanticValue] whose variant id this is.
	 * @param derivedClass
	 *   The [ValueClass] holding the variant id.
	 */
	fun recordVariantIdDerivation(
		base: L2SemanticValue,
		derivedClass: ValueClass
	) = recordDerivation(variantIdOf, base, derivedClass) {
		// There is no backward map from a variant id to its variant, so a variant
		// id says nothing here that could narrow a base being introduced.
		topRestriction
	}

	/**
	 * Relate a derived [ValueClass] to the class of the value it describes,
	 * **introducing that base class if this manifest does not have one**.
	 *
	 * Keeping the base is the whole point.  A derived class is only meaningful
	 * relative to its base, and the base's class is what carries the synonymy
	 * that makes two derived values equal.  If `x` and `y` have had their
	 * variants computed separately and are then discovered to be equal, merging
	 * their classes is what makes `variant(x)` and `variant(y)` synonymous; if
	 * `x` and `y` were subsequently dropped for being dead, that synonymy could
	 * only survive in a class common to both.  So the base class is retained
	 * whether or not anything reads it, and is anchored by the base semantic
	 * value that the derived value names.
	 *
	 * The base is introduced with no definition – an anchor, not a value anyone
	 * will read – but *not* with a blank restriction.  A default restriction
	 * asserts that nothing at all is known about the value, which is both untrue
	 * and contagious: everything computed from the base inherits the ⊤, and a
	 * primitive asked what it returns for a ⊤ argument can do no better than ⊤
	 * in turn.  The derivation itself constrains the base – knowing a value's
	 * [TypeTag] says a great deal about the value – so that is where the
	 * introduced restriction comes from, with narrowing continuing to propagate
	 * into it from the derived value in the usual way afterwards.
	 *
	 * @param edges
	 *   Either [tagOf] or [variantIdOf].
	 * @param base
	 *   The [L2SemanticValue] the derived class describes.
	 * @param derivedClass
	 *   The derived [ValueClass].
	 * @param baseRestrictionFromDerived
	 *   What the derived value's [TypeRestriction] implies about the base, used
	 *   only when the base has to be introduced here.  Answer [topRestriction]
	 *   for a derivation that implies nothing.
	 */
	private fun recordDerivation(
		edges: MutableMap<ValueClass, ValueClass>,
		base: L2SemanticValue,
		derivedClass: ValueClass,
		baseRestrictionFromDerived: (TypeRestriction) -> TypeRestriction)
	{
		if (!caresAboutSemanticValues) return
		val baseClass = classOrNull(base)
			?: run {
				// The base is not (yet) known here.  That happens when a value is
				// named only by something derived from it - a tag computed for a
				// dispatch on a value that has no register of its own yet - and at
				// a merge, where mergeIncomingPostponedInstructions brings a
				// postponed instruction's derived value across before
				// populateForMerge populates the ordinary values.
				val derived = states[resolve(derivedClass)]
				introduceSynonym(
					setOf(base),
					when (derived)
					{
						null -> base.defaultRestriction
						else -> base.defaultRestriction.intersection(
							baseRestrictionFromDerived(derived.restriction))
					})
				classFor(base)
			}
		edges[baseClass] = derivedClass
		derivedFrom[derivedClass] = baseClass
	}

	/**
	 * Record that [loser] has been merged into [winner], so that any
	 * [L2SemanticValue] still bound to [loser] resolves to [winner].  The
	 * loser's entry in [states] is removed; the caller is responsible for
	 * having already folded its contents into the winner's.
	 *
	 * @param winner
	 *   The surviving [ValueClass].
	 * @param loser
	 *   The [ValueClass] being merged away.
	 */
	private fun forwardClass(winner: ValueClass, loser: ValueClass)
	{
		if (winner == loser) return
		states.remove(loser)
		forward[loser] = winner
		postponedReaders.remove(loser)?.let { consumers ->
			postponedReaders.getOrPut(winner, ::mutableSetOf).addAll(consumers)
		}
		// Congruence: if the two bases are the same value, so are the values
		// derived from them.  Unify the derived classes rather than letting the
		// loser's edges dangle.  Note that this direction only – merging bases
		// merges their derived values, never the converse, since neither tags
		// nor variant ids are injective.
		mergeDerivationEdges(tagOf, winner, loser)
		mergeDerivationEdges(variantIdOf, winner, loser)
		derivedFrom.remove(loser)?.let { base ->
			derivedFrom[winner] = resolve(base)
		}
	}

	/**
	 * Fold [loser]'s entry in a derivation edge map into [winner]'s.  If both
	 * had a derived class, those two classes describe the same value and are
	 * therefore merged.
	 *
	 * @param edges
	 *   Either [tagOf] or [variantIdOf].
	 * @param winner
	 *   The surviving base [ValueClass].
	 * @param loser
	 *   The base [ValueClass] being merged away.
	 */
	private fun mergeDerivationEdges(
		edges: MutableMap<ValueClass, ValueClass>,
		winner: ValueClass,
		loser: ValueClass)
	{
		val losersDerived = edges.remove(loser) ?: return
		when (val winnersDerived = edges[winner])
		{
			null -> edges[winner] = resolve(losersDerived)
			else -> mergeValueClasses(
				resolve(winnersDerived), resolve(losersDerived))
		}
	}

	/**
	 * Merge two [ValueClass]es that have been shown to describe the same value,
	 * folding the second into the first.
	 *
	 * @param winner
	 *   The surviving [ValueClass].
	 * @param loser
	 *   The [ValueClass] to merge away.
	 */
	private fun mergeValueClasses(winner: ValueClass, loser: ValueClass)
	{
		if (winner == loser) return
		val winnerState = states[winner] ?: return
		val loserState = states[loser] ?: return
		// This recurses back through forwardClass if the merged classes have
		// derived values of their own, which terminates because every merge
		// strictly reduces the number of classes.
		//
		// The members are taken in the spelling of each record's sole
		// representation, which is what a derived value has: a tag or a variant
		// id is reached as an int.  Once a value's spellings share a class this
		// must become the canonical boxed members instead.
		agglomerateSynonym(
			winnerState.soleView.members + loserState.soleView.members,
			// The stored restrictions, which are boxed, rather than the views'
			// projections of them.  A tag or variant class is a boxed value
			// with only its int aspect in play, so projecting to int here and
			// boxing again on the way back into storage would discard what the
			// boxed restriction knows.
			winnerState.restriction.intersection(loserState.restriction))
	}

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym]
	 * that's bound to it.  Fail if it's not found.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @return
	 *   The [L2Synonym] bound to that semantic value.
	 */
	fun semanticValueToSynonym(
		semanticValue: L2SemanticValue
	): L2Synonym = semanticValueToSynonymOrNull(semanticValue)!!

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym]
	 * that's bound to it.  If not found, evaluate the lambda to produce an
	 * optional `L2Synonym` or `null`.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @param elseSupplier
	 *   The code to run if the semantic value was not found.
	 * @return
	 *   The [L2Synonym] bound to that semantic value, or `null`.
	 */
	private fun <K: RegisterKind<K>> semanticValueToSynonymOrElse(
		semanticValue: L2SemanticValue,
		elseSupplier: ()->L2Synonym
	): L2Synonym = semanticValueToSynonymOrNull(semanticValue) ?: elseSupplier()

	/**
	 * Capture information about a new [L2Synonym] and its [TypeRestriction].
	 * It's an error if any of the provided [L2SemanticValue]s are already
	 * bound to other synonyms in this manifest.
	 *
	 * The values may nonetheless *name* a value this manifest already knows,
	 * when they are the unboxed spelling of one it holds boxed.  That is not a
	 * new value but a new [Representation] of an existing one, so it joins that
	 * [ValueClass] rather than starting one: a value has a single class, and
	 * its boxed and unboxed forms cannot be allowed to drift apart into two.
	 *
	 * @param semanticValues
	 *   The new [L2SemanticValue]s to place in the new synonym.
	 * @param restriction
	 *   The [TypeRestriction] to constrain the new synonym.
	 */
	fun introduceSynonym(
		semanticValues: Iterable<L2SemanticValue>,
		restriction: TypeRestriction)
	{
		assert(semanticValues.none(::hasSemanticValue))
		val valueClass = newValueClass()
		bind(semanticValues, valueClass)
		assert(valueClass !in states)
		states[valueClass] = newEmptyState(
			semanticValues.toSet(),
			restriction)
		updateRestriction(valueClass) { restriction }
	}

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether this semantic value is known to this manifest, due to a
	 *   previous instruction that wrote it, or its synonymy with a previously
	 *   written instruction's written value, or the presence of a postponed
	 *   instruction, or the fact of an [L2BasicBlock.postPhiMap] causing it to
	 *   come into existence.
	 */
	fun hasSemanticValue(semanticValue: L2SemanticValue): Boolean =
		semanticValue in classOf

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest AND
	 * the instructions that compute it have been emitted.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @param kind
	 *   The [RegisterKind] for which a live value is sought.
	 * @return
	 *   Whether there is a register of the requested [kind] known to be holding
	 *   this value, whether it's already written by a previous instruction or
	 *   it would be written by a postponed instruction.
	 */
	fun <K: RegisterKind<K>> hasLiveSemanticValue(
		semanticValue: L2SemanticValue,
		kind: K
	): Boolean = stateOrNull(semanticValue).notNullAnd {
		kind.representationIn(this).definitions.any { reg ->
			reg.definitions().any { semanticValue in it.semanticValues() }
		}
	}

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest AND
	 * it's populated for at least one [RegisterKind].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether there is a register of *any* kind known to be holding this
	 *   value, whether it's already written by a previous instruction or it
	 *   would be written by a postponed instruction.
	 */
	fun hasAnyLiveSemanticValue(
		semanticValue: L2SemanticValue
	): Boolean = stateOrNull(semanticValue).notNullAnd {
		allDefinitions.any { reg ->
			reg.definitions().any { write ->
				semanticValue in write.semanticValues()
			}
		}
	}

	/**
	 * Given an [L2SemanticValue], see if there's already an equivalent one in
	 * this manifest.  If an [L2SemanticPrimitiveInvocation] is supplied, look
	 * for a recursively synonymous one.
	 *
	 * Answer the extant [L2SemanticValue] if found, otherwise answer `null`.
	 * Note that there may be multiple [L2SemanticPrimitiveInvocation]s that are
	 * equivalent, in which case an arbitrary (and not necessarily stable) one
	 * is chosen.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   An [L2SemanticValue] from this manifest which is equivalent to the
	 *   given one, or `null` if no such value is in the manifest.
	 */
	fun <K: RegisterKind<K>> equivalentSemanticValue(
		semanticValue: L2SemanticValue
	): L2SemanticValue?
	{
		if (hasSemanticValue(semanticValue))
		{
			// It already exists in exactly the form given, which is the vast
			// majority of cases.
			return semanticValue
		}
		// Try a slower, far less frequent search.
		val onlyClass = classRestrictingSearchFor(semanticValue)
		return classOf.keys.firstOrNull { other ->
			(onlyClass === null || other.javaClass === onlyClass)
				&& isEquivalentSemanticValue(semanticValue, other)
		}.cast()
	}

	/**
	 * If a search for something equivalent to the given [L2SemanticValue] can
	 * safely be narrowed to candidates having one particular concrete class,
	 * answer that class, otherwise answer `null` to indicate that every
	 * candidate must be considered.
	 *
	 * [isEquivalentSemanticValue] can only relate two semantic values of
	 * differing concrete classes in three ways:
	 *  * the shared-synonym test, which requires the probe to be present in
	 *    this manifest already,
	 *  * the case where the probe is an [L2SemanticConstant], which can match
	 *    any value whose restriction is that same constant, and
	 *  * the case where the *candidate* is an [L2SemanticConstant], which
	 *    likewise requires the probe to be present in this manifest.
	 *
	 * When the probe is absent and is not itself a constant, none of the three
	 * applies, and the remaining cases – equality and the structural
	 * boxed/unboxed, tag, variant, and primitive-invocation recursions – all
	 * require both values to have the same (final) class.  Filtering candidates
	 * preserves their relative order, and any candidate that could have matched
	 * is retained, so the value chosen is unchanged.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] being searched for.
	 * @return
	 *   The only concrete class worth examining, or `null` if all candidates
	 *   must be examined.
	 */
	private fun classRestrictingSearchFor(
		semanticValue: L2SemanticValue
	): Class<out L2SemanticValue>? = when
	{
		semanticValue is L2SemanticConstant -> null
		hasSemanticValue(semanticValue) -> null
		else -> semanticValue.javaClass
	}

	/**
	 * Answer the [L2SemanticValue] naming the [TypeTag] extracted from the
	 * given boxed [L2SemanticValue], if that tag is available in this
	 * manifest, otherwise answer `null`.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose extracted tag is sought.
	 * @return
	 *   The equivalent [L2SemanticValue] holding the tag, or `null`.
	 */
	fun tagFormOf(
		boxed: L2SemanticValue
	): L2SemanticValue? =
		derivedFormOf(tagOf, boxed)
			?: equivalentSemanticValue(L2SemanticExtractedTag(boxed))

	/**
	 * Answer the [L2SemanticValue] naming the [ObjectLayoutVariant] id
	 * extracted from the given [boxed] value, if that id is available in this
	 * manifest; otherwise answer `null`.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose extracted variant id is sought.
	 * @return
	 *   The equivalent [L2SemanticValue] holding the variant id, or
	 *   `null`.
	 */
	fun variantIdFormOf(
		boxed: L2SemanticValue
	): L2SemanticValue? =
		derivedFormOf(variantIdOf, boxed)
			?: equivalentSemanticValue(L2SemanticObjectVariantId(boxed))

	/**
	 * Answer a member of the [ValueClass] reached from the given base by the
	 * given derivation edges, or `null` if there is no such edge or the class
	 * it points at has been forgotten.
	 *
	 * @param edges
	 *   Either the [tagOf] or the [variantIdOf] map.
	 * @param boxed
	 *   The base [L2SemanticValue].
	 * @return
	 *   A member of the derived [ValueClass], or `null`.
	 */
	private fun derivedFormOf(
		edges: Map<ValueClass, ValueClass>,
		boxed: L2SemanticValue
	): L2SemanticValue?
	{
		val baseClass = classOrNull(boxed) ?: return null
		val derivedClass = edges[baseClass]?.let(::resolve) ?: return null
		val state = states[derivedClass] ?: return null
		return state.synonym.pickSemanticValue()
	}

	/**
	 * Given an [L2SemanticValue], see if there's already an equivalent one
	 * in this manifest, but appearing in a definition – i.e., already bound to
	 * a register of the specified [kind][RegisterKind].  If an
	 * [L2SemanticPrimitiveInvocation] is supplied, look for a recursively
	 * synonymous one (that's bound to a register).
	 *
	 * Answer the extant [L2SemanticValue] if found, otherwise answer `null`.
	 * Note that there may be multiple [L2SemanticPrimitiveInvocation]s that are
	 * equivalent, in which case an arbitrary (and not necessarily stable) one
	 * is chosen.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param kind
	 *   The [RegisterKind] for which to look for a definition.
	 * @return
	 *   An [L2SemanticValue] from this manifest which is equivalent to the
	 *   given one, and appearing in a defining write of the requested [kind],
	 *   or `null` if no such value is in the manifest.
	 */
	fun <K: RegisterKind<K>> equivalentPopulatedSemanticValue(
		semanticValue: L2SemanticValue,
		kind: K
	): L2SemanticValue?
	{
		if (isPopulated(semanticValue, kind)) return semanticValue
		// Try a slower, far less frequent search.  Note that the probe may be
		// present in the manifest but unpopulated, in which case the search
		// cannot be narrowed by class, since the shared-synonym test can then
		// match a candidate of some other class.
		val onlyClass = classRestrictingSearchFor(semanticValue)
		return classOf.keys.firstOrNull { other ->
			(onlyClass === null || other.javaClass === onlyClass)
				&& isEquivalentSemanticValue(semanticValue, other)
				&& isPopulated(other, kind)
		}
	}

	/**
	 * Answer whether the given [L2SemanticValue] is populated by having a
	 * visible defining write that wrote to that exact semantic value, using a
	 * register of the specified [kind].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to test.
	 * @param kind
	 *   The [RegisterKind] indicating what kind of definition to look for.
	 * @return
	 *   Whether that semantic value has a visible write to it of that [kind].
	 */
	fun <K: RegisterKind<K>> isPopulated(
		semanticValue: L2SemanticValue,
		kind: K
	): Boolean = (hasSemanticValue(semanticValue)
		&& getDefinitions(semanticValue, kind).any { register ->
			register.definitions().any { write ->
				semanticValue in write.semanticValues()
			}
		})

	/**
	 * Given two [L2SemanticValue]s, see if they represent the same value
	 * in this manifest.  Include checking covariant homomorphisms between
	 * boxed/unboxed forms and primitive invocations.
	 *
	 * @param semanticValue
	 *   The first [L2SemanticValue] to compare.
	 * @param otherSemanticValue
	 *   The second [L2SemanticValue] to compare.
	 * @return
	 *   True iff the two semantic values represent the same value in this
	 *   manifest.
	 */
	tailrec fun isEquivalentSemanticValue(
		semanticValue: L2SemanticValue,
		otherSemanticValue: L2SemanticValue
	): Boolean
	{
		if (semanticValue == otherSemanticValue) return true
		val ownClass = classOrNull(semanticValue)
		if (ownClass != null && ownClass == classOrNull(otherSemanticValue))
		{
			// They're already synonyms of each other.
			return true
		}
		when
		{
			semanticValue is L2SemanticConstant ->
			{
				if (!hasSemanticValue(otherSemanticValue)) return false
				val otherRestriction = restrictionFor(otherSemanticValue)
				if (otherRestriction.constantOrNull
					.notNullAnd { equals(semanticValue.value) })
				{
					return true
				}
			}
			otherSemanticValue is L2SemanticConstant ->
			{
				if (!hasSemanticValue(semanticValue)) return false
				val restriction = restrictionFor(semanticValue)
				if (restriction.constantOrNull
					.notNullAnd { equals(otherSemanticValue.value) })
				{
					return true
				}
			}
			semanticValue is L2SemanticPrimitiveInvocation &&
				otherSemanticValue is L2SemanticPrimitiveInvocation &&
				semanticValue.primitive == otherSemanticValue.primitive ->
			{
				// They're invocations of the same primitive, so see if the
				// arguments happen to correspond.
				return semanticValue.argumentSemanticValues
					.zip(otherSemanticValue.argumentSemanticValues)
					.all { (a, b) ->
						@Suppress("NON_TAIL_RECURSIVE_CALL")
						isEquivalentSemanticValue(a, b)
					}
			}
			semanticValue is L2SemanticExtractedTag &&
				otherSemanticValue is L2SemanticExtractedTag ->
			{
				// Equivalent values have the same tag.
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
			semanticValue is L2SemanticObjectVariantId &&
				otherSemanticValue is L2SemanticObjectVariantId ->
			{
				// Equivalent values have the same object variant id.
				return isEquivalentSemanticValue(
					semanticValue.base, otherSemanticValue.base)
			}
		}
		// We couldn't find a way in which they're equal.
		return false
	}

	/**
	 * Combine what several [ValueState]s know about one [RegisterKind] into the
	 * single [Representation] that the merged value has in that kind.
	 *
	 * The rule is the same for every kind, and is therefore expressed once:
	 * every register of that kind holds the value, and the value needs an
	 * instruction to populate it only where nothing yet writes it.  Only the
	 * *choice* of instruction depends on the kind, and that is dispatched through
	 * the [RegisterKind] itself.
	 *
	 * @param kind
	 *   The [RegisterKind] to reconcile.
	 * @param sources
	 *   The [ValueState]s being merged, including the survivor's.
	 * @param members
	 *   The canonical members of the merged value.
	 * @param restriction
	 *   The boxed [TypeRestriction] bounding the merged value.
	 * @return
	 *   The merged [Representation] for that kind.
	 */
	private fun <K: RegisterKind<K>> agglomeratedRepresentation(
		kind: K,
		sources: List<ValueState>,
		members: Set<L2SemanticValue>,
		restriction: TypeRestriction
	): Representation<K>
	{
		val present = sources
			.map(kind::representationIn)
			.filterNot(Representation<K>::isAbsent)
		val definitions = present.flatMap(Representation<K>::definitions)
		// Reuse any of the existing postponed instructions, since they all will
		// populate the entire synonym.
		val postponedInstructions =
			present.mapNotNull(Representation<K>::postponedInstruction)
		val defined = definitions
			.flatMap(L2Register<K>::definitions)
			.flatMap(L2WriteOperand<K>::semanticValues)
			.intersect(members)
		// An instruction written into a register of this kind needs the
		// restriction as that kind sees it.
		val postponedInstruction = when
		{
			// Don't generate postponed instructions when the graph is held
			// together by registers instead of semantic values.
			!caresAboutSemanticValues -> null
			// If the value is defined for *any*, no instruction is needed,
			// since generation from constants or even conversions between kinds
			// should be an automatic feature of the generator.
			defined.isNotEmpty() -> null
			// If the new restriction is impossible, output an impossibleCode
			// instruction, which should hopefully cause this path to become
			// unreachable from the nearest branch.
			restriction.isImpossible -> null
			// Constants never create an instructions.
			// The value is defined for none.  Check for a constant restriction.
			restriction.isConstant -> null
			// If the value is defined for some and notDefined for others,
			// produce a move.
			defined.isNotEmpty() -> kind.dynamicMove(
				defined.first(), emptySet(), this, restriction)
			// Defined for none, and not constant.  Keep (any) one of the
			// (definitely non-move, non-constant-move) postponed instructions
			// found in the prior synonyms.  Allow there to have been no
			// postponed instruction *just* to simplify intermediate states,
			// where the synonym is built before the defining instruction is
			// added.
			else -> postponedInstructions.firstOrNull()
		}
		return Representation(kind, definitions, postponedInstruction)
	}

	fun agglomerateSynonym(
		semanticValues: Iterable<L2SemanticValue>,
		baseRestriction: TypeRestriction)
	{
		val constant = baseRestriction.constantOrNull
		if (constant != null && semanticValues.none { it.isConstant })
		{
			// Recurse, but with the semantic constant present.
			agglomerateSynonym(
				semanticValues + constant(constant),
				baseRestriction)
			return
		}
		val existingClasses = mutableSetOf<ValueClass>()
		val strandedValues = mutableSetOf<L2SemanticValue>()
		semanticValues.forEach { sv ->
			classOrNull(sv)?.let(existingClasses::add)
				?: strandedValues.add(sv)
		}
		// Common path, synonym already exists.
		if (existingClasses.size == 1 && strandedValues.isEmpty())
		{
			if (!restrictionFor(semanticValues.first())
					.isStrongerThan(baseRestriction))
			{
				updateRestriction(semanticValues.first()) {
					baseRestriction
				}
			}
			return
		}

		// If a semantic constant is provided, use its value as a constant
		// restriction, otherwise compute the intersection of the existing
		// synonyms' restrictions.
		val existingSemanticConstant =
			strandedValues.firstOrNull(L2SemanticValue::isConstant)
		// Intersect the *stored* restrictions, which are boxed, so that tag and
		// variant information survives an agglomeration performed on behalf of
		// an unboxed spelling.
		// The caller's restriction is a constraint on the value too, so it is
		// intersected in rather than being used only when there is nothing else
		// - which is what "may be further strengthened by the restrictions
		// present for existing synonyms" says, and what the single-class path
		// above already does.  Dropping it let a class carrying no real
		// knowledge decide the merged restriction on its own.
		// The caller's restriction arrives in whatever kind the caller was
		// working in - an int move supplies an int-flagged one - while stored
		// restrictions are boxed, and mixing the two in one restriction is
		// forbidden.  Boxing it first is what makes the intersection well
		// formed.
		val newRestriction = existingSemanticConstant?.constantRestrictionOrNull
			?: existingClasses.fold(baseRestriction) {
					restriction, existing ->
				restriction.intersection(states[existing]!!.restriction)
			}

		// Capture the records now, since forwarding the losers below removes them
		// from the map, while their representations are still needed afterwards.
		val existingStates = existingClasses.map { states[it]!! }
		// The kind this call is spelled in is always one of the kinds to
		// reconcile, even when no existing record is held in it yet.
		val members: Set<L2SemanticValue> = existingStates
			.flatMapTo(mutableSetOf(), ValueState::members)
			.plus(strandedValues)
		val tagClasses = existingStates
			.mapNotNullTo(mutableSetOf(), ValueState::tagClass)
		val isTagOfClasses = existingStates
			.flatMapTo(mutableSetOf(), ValueState::isTagOfClasses)
		val variantClasses = existingStates
			.mapNotNullTo(mutableSetOf(), ValueState::variantClass)
		val isVariantOfClasses = existingStates
			.flatMapTo(mutableSetOf(), ValueState::isVariantOfClasses)
		val newState = ValueState(
			members = members,
			restriction = newRestriction,
			boxedRepresentation = agglomeratedRepresentation(
				BOXED_KIND, existingStates, members, newRestriction),
			intRepresentation = agglomeratedRepresentation(
				INTEGER_KIND, existingStates, members, newRestriction),
			floatRepresentation = agglomeratedRepresentation(
				FLOAT_KIND, existingStates, members, newRestriction),
			tagClass = tagClasses.firstOrNull(),  // Merged below.
			isTagOfClasses = isTagOfClasses,
			variantClass = tagClasses.firstOrNull(),  // Merged below
			isVariantOfClasses = isVariantOfClasses)
		// Wire it in.  One of the existing classes survives and absorbs the
		// others, so that anything still referring to a merged-away class
		// resolves to the survivor.
		assert(
			caresAboutSemanticValues
				|| newState.representations.all {
					it.postponedInstruction === null
				})
		val winner = existingClasses.firstOrNull() ?: newValueClass()
		existingClasses.forEach { loser -> forwardClass(winner, loser) }
		states[winner] = newState
		bind(members, winner)
		// Only now deal with multiple tagClasses by merging them.  If we did
		// this earlier, the intermediate changes might be problematic.
		tagClasses.zipWithNext(::mergeValueClasses)
		// And do the same for variants.
		variantClasses.zipWithNext(::mergeValueClasses)
	}

	/**
	 * Merge a new [L2SemanticValue] into an existing [L2Synonym]. Update
	 * the manifest to reflect the merge.
	 *
	 * Note that because the [L2SemanticValue] is new, we don't have to
	 * check for existing [L2SemanticPrimitiveInvocation]s becoming synonyms of
	 * each other, which is much faster than the general case in
	 * [mergeExistingSemanticValues].
	 *
	 * @param existingSynonym
	 *   An existing [L2Synonym].
	 * @param semanticValue
	 *   Another [L2SemanticValue] representing the same value.
	 */
	fun extendSynonym(
		existingSynonym: L2Synonym,
		semanticValue: L2SemanticValue)
	{
		assert(!hasSemanticValue(semanticValue))
		val semanticValues = existingSynonym.semanticValues() + semanticValue
		// The class keeps its identity; only its state is replaced, since the
		// constraint records its own membership.
		val valueClass = classFor(existingSynonym.pickSemanticValue())
		states[valueClass] = states[valueClass]!!.withMembers(semanticValues)
		bind(semanticValues, valueClass)
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 *
	 * @param semanticValue1
	 *   An [L2SemanticValue].
	 * @param semanticValue2
	 *   Another [L2SemanticValue] representing what has just been shown to
	 *   be the same value as [semanticValue1].  They may already be synonyms.
	 */
	fun mergeExistingSemanticValues(
		semanticValue1: L2SemanticValue,
		semanticValue2: L2SemanticValue)
	{
		// When we care about semantic values, introduce a constant definition
		// automaatically.
		if (caresAboutSemanticValues)
		{
			// Deal with the introduction of constant registers that don't yet
			// appear in the manifest.
			if (semanticValue1.isConstant && !hasSemanticValue(semanticValue1)) {
				introduceSynonym(
					setOf(semanticValue1),
					semanticValue1.constantRestrictionOrNull!!)
			}
			if (semanticValue2.isConstant && !hasSemanticValue(semanticValue2)) {
				introduceSynonym(
					setOf(semanticValue2),
					semanticValue2.constantRestrictionOrNull!!)
			}
		}
		val synonym1 = semanticValueToSynonym(semanticValue1)
		val synonym2 = semanticValueToSynonym(semanticValue2)
		if (!privateMergeSynonyms(synonym1, synonym2))
		{
			return
		}

		// Figure out which L2SemanticPrimitiveInvocations have become
		// equivalent due to their arguments being merged into the same
		// synonyms.  Repeat as necessary, alternating collection of newly
		// matched pairs of synonyms with merging them.
		val allSemanticPrimitives = classOf.keys
			.filterIsInstance<L2SemanticPrimitiveInvocation>()
			.groupBy(L2SemanticPrimitiveInvocation::primitive)
		if (allSemanticPrimitives.isEmpty())
		{
			// There are no primitive invocations visible.
			return
		}
		while (true)
		{
			val followupMerges = mutableListOf<
				Pair<
					L2SemanticValue,
					L2SemanticValue>>()
			for (invocations in allSemanticPrimitives.values)
			{
				// It takes at least two primitive invocations (of the same
				// primitive) for there to be a potential merge.
				if (invocations.size <= 1)
				{
					continue
				}
				// Create a map from each distinct input list of synonyms to the
				// set of invocation synonyms.
				val map = mutableMapOf<
					List<L2Synonym?>,
					MutableSet<L2Synonym>>()
				for (invocation in invocations)
				{
					// Note that sometimes an L2SemanticPrimitiveInvocation will
					// be in the manifest, even though some of its argument
					// semantic values are no longer accessible.  Create a
					// singleton synonym for such a semantic value, but don't
					// register it in the manifest.
					val argumentSynonyms: List<L2Synonym?> =
						invocation.argumentSemanticValues
							.map {
								semanticValueToSynonymOrElse(it) {
									L2Synonym(setOf(it))
								}
							}
					val primitiveSynonyms =
						map.computeIfAbsent(argumentSynonyms) { mutableSetOf() }
					val invocationSynonym: L2Synonym =
						semanticValueToSynonym(invocation)
					if (primitiveSynonyms.isNotEmpty()
						&& !primitiveSynonyms.contains(invocationSynonym))
					{
						val sampleSynonym = primitiveSynonyms.first()
						val sampleInvocation = sampleSynonym.pickSemanticValue()
						followupMerges.add(invocation to sampleInvocation)
					}
					primitiveSynonyms.add(invocationSynonym)
				}
			}
			if (followupMerges.isEmpty())
			{
				break
			}
			followupMerges.forEach { (first, second) ->
				privateMergeSynonyms(
					semanticValueToSynonym(first),
					semanticValueToSynonym(second))
			}
		}
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 * Do not yet merge synonyms of [L2SemanticPrimitiveInvocation]s whose
	 * arguments have just become equivalent.
	 *
	 * @param synonym1
	 *   An [L2Synonym].
	 * @param synonym2
	 *   Another [L2Synonym] representing what has just been shown to be the
	 *   same value.  It may already be equal to the first synonym.
	 * @return
	 *   Whether any change was made to the manifest.
	 */
	private fun privateMergeSynonyms(
		synonym1: L2Synonym,
		synonym2: L2Synonym
	): Boolean
	{
		if (synonym1 == synonym2) return false
		val class1 = classFor(synonym1.pickSemanticValue())
		val class2 = classFor(synonym2.pickSemanticValue())
		if (class1 == class2) return false
		val state1 = states[class1]!!
		val state2 = states[class2]!!
		val semanticValues =
			synonym1.semanticValues() + synonym2.semanticValues()
		// Intersect the stored restrictions, which are boxed, so that nothing the
		// boxed form knows is lost by merging on behalf of an unboxed spelling.
		val restriction = state1.restriction.intersection(state2.restriction)
		// class1 survives and absorbs class2, so anything still holding class2
		// resolves to class1.
		forwardClass(class1, class2)
		// class1's record is re-read, since forwarding class2 can recursively
		// merge derived values and thereby replace it.  Every kind either record
		// was held in is reconciled, not just the one this synonym is spelled in.
		val newState = states[class1]!!.mergedWith(
			state2, semanticValues, restriction)
		assert(
			caresAboutSemanticValues
				|| newState.representations.all {
					it.postponedInstruction === null
				})
		states[class1] = newState
		bind(semanticValues, class1)
		if (state1.isImpossible) impossibleRestrictionCount--
		if (state2.isImpossible) impossibleRestrictionCount--
		if (newState.isImpossible) impossibleRestrictionCount++
		if (restriction.isConstant
			&& semanticValues.none(L2SemanticValue::isConstant))
		{
			// The merged restriction is a constant, but we don't have that
			// semantic constant within the synonym yet.  The two cases are if
			// there's another synonym with that semantic constant and if there
			// isn't.
			val semanticConstant = L2SemanticConstant(
				restriction.constantOrNull!!)
			if (hasSemanticValue(semanticConstant))
			{
				// Another synonym is also constrained to that constant.  Do
				// another synonym merge, technically recursively, although the
				// maximum recursion depth is 2.  Note that we don't care about
				// the boolean return value, since we must answer true from the
				// outer call.
				privateMergeSynonyms(
					states[class1]!!.synonym,
					semanticValueToSynonym(semanticConstant))
			}
			else
			{
				// The semantic constant is not in any synonym yet, but it needs
				// to be added to the new synonym.
				extendSynonym(states[class1]!!.synonym, semanticConstant)
			}
		}
		return true
	}

	/**
	 * Given two semantic values, check if there is an equivalent semantic value
	 * for each in this manifest, and if so, merge their synonyms.  Otherwise do
	 * nothing.
	 *
	 * Only merge them if they're both populated.
	 */
	fun mergeSemanticValueEquivalentsIfPresent(
		value1: L2SemanticValue,
		value2: L2SemanticValue)
	{
		RegisterKind.all.forEach { kind ->
			equivalentPopulatedSemanticValue(value1, kind.cast())
				?.let { eqv1 ->
					equivalentPopulatedSemanticValue(value2, kind.cast())
						?.let { eqv2 ->
							mergeExistingSemanticValues(eqv1, eqv2)
						}
				}
		}
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [kind].  Only consider registers whose
	 * definitions *all* include that semantic value.  This should work well in
	 * SSA or non-SSA, but not after register coloring. If no such register is
	 * found, return `null`.
	 *
	 * @param K
	 *   The [RegisterKind] of the desired register.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   The requested [L2Register] or `null`.
	 */
	fun <K: RegisterKind<K>> getDefinitionOrNull(
		semanticValue: L2SemanticValue,
		kind: K
	): L2Register<K>?
	{
		if (!hasSemanticValue(semanticValue))
		{
			// Postponed instructions don't have registers assigned.
			return null
		}
		val constraint = constraint(semanticValue, kind)
		var definition = constraint.definitions.find { reg ->
			reg.definitions().all { write ->
				semanticValue in write.semanticValues()
			}
		}
		if (definition == null)
		{
			// Fall back to any register of the requested kind, even if it
			// doesn't have the specified semanticValue in all of its
			// definitions.
			definition = constraint.definitions.firstOrNull()
		}
		return definition
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [RegisterKind].  Only consider
	 * registers whose definitions *all* include that semantic value.  This
	 * should work well in SSA or non-SSA, but not after register coloring.
	 *
	 * @param K
	 *   The [RegisterKind] of the desired register.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   The requested [L2Register].
	 */
	fun <K: RegisterKind<K>> getDefinition(
		semanticValue: L2SemanticValue,
		kind: K
	): L2Register<K> = getDefinitionOrNull(semanticValue, kind)!!

	/**
	 * Retrieve all [L2Register]s known to contain the given
	 * [L2SemanticValue], for the specified [kind]. If the mode is still
	 * [BySemanticValue], narrow it to just those registers whose definitions
	 * *all* include that semantic value.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   A [List] of the requested [L2Register]s.
	 */
	fun <K: RegisterKind<K>> getDefinitions(
		semanticValue: L2SemanticValue,
		kind: K
	): List<L2Register<K>> = when (mode)
	{
		BySemanticValue ->
			constraint(semanticValue, kind).definitions.filter { reg ->
				reg.definitions().all { write ->
					semanticValue in write.semanticValues()
				}
			}
		else -> constraint(semanticValue, kind).definitions
	}
	/**
	 * Retrieve all [L2Register]s known to contain the given [L2SemanticValue],
	 * regardless of whether [getDefinitions] would filter some out.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   A [List] of the requested [L2Register]s.
	 */
	fun <K: RegisterKind<K>> getAllDefinitions(
		semanticValue: L2SemanticValue,
		kind: K
	): List<L2Register<K>> = constraint(semanticValue, kind).definitions

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest.  This also
	 * restricts any synonymous semantic values, and may even cause propagation
	 * of narrowing to related synonyms, which may also merge if they become
	 * constrained to constants that are already present in other synonyms.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param newRestriction
	 *   The [TypeRestriction] to bound the synonym.
	 */
	fun setRestriction(
		semanticValue: L2SemanticValue,
		newRestriction: TypeRestriction)
	{
		updateRestriction(semanticValue) { newRestriction }
	}

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest, with the
	 * intersection of its current restriction and the restriction implied by
	 * the given [A_Type].  Note that this also restricts any synonymous
	 * semantic values, and can also propagate narrowing to related semantic
	 * values, including merging synonyms if a restriction narrows to a constant
	 * and that constant is already present in another synonym.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param type
	 *   The [A_Type] to intersect with the existing restriction.
	 */
	fun intersectType(semanticValue: L2SemanticValue, type: A_Type)
	{
		updateRestriction(semanticValue) { intersectionWithType(type) }
	}

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest, with the
	 * difference between its current restriction and the given restriction.
	 * Note that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param type
	 *   The [A_Type] to exclude from the synonym's restriction.
	 */
	fun subtractType(semanticValue: L2SemanticValue, type: A_Type)
	{
		updateRestriction(semanticValue) { minusType(type) }
	}

	fun restrictionFor(read: L2ReadOperand<*>): TypeRestriction = when
	{
		// Simplify things for the caller.  The operand carries its own
		// restriction, which is the whole answer once the graph is held together
		// by registers rather than by semantic values.
		!caresAboutSemanticValues -> read.restriction()
		else -> restrictionFor(read.semanticValue())
			.intersection(read.restriction())
	}

	fun restrictionFor(write: L2WriteOperand<*>): TypeRestriction = when
	{
		// Simplify things for the caller, as for the read overload above.
		!caresAboutSemanticValues -> write.restriction()
		!hasSemanticValue(write.pickSemanticValue()) -> write.restriction()
		else -> restrictionFor(write.pickSemanticValue())
			.intersection(write.restriction())
	}

	/**
	 * Look up the [TypeRestriction] that currently bounds this
	 * [L2SemanticValue].  Fail if there is none.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @return
	 *   The [TypeRestriction] that bounds the synonym.
	 */
	fun restrictionFor (
		semanticValue: L2SemanticValue
	): TypeRestriction
	{
		if (semanticValue is L2SemanticDummy)
		{
			// When the control flow graph is held together by registers instead
			// of semantic values, the restrictions can be ignored.  The
			// L2ReadOperands carry their own restrictions.
			return topRestriction
		}
		if (semanticValue.isConstant)
		{
			// Only auto-introduce constants in early phases
			if (caresAboutSemanticValues &&
				!hasSemanticValue(semanticValue))
			{
				introduceSynonym(
					setOf(semanticValue),
					semanticValue.constantRestrictionOrNull!!)
			}
			return semanticValue.constantRestrictionOrNull!!
		}
		val equivalent = equivalentSemanticValue(semanticValue)!!
		return stateOrNull(equivalent)!!.restriction
	}

	/**
	 * Answer the [Constraint] having the given [kind] and associated with the
	 * [L2Synonym] containing the given [L2SemanticValue], or null if it doesn't
	 * exist.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @param kind
	 *   The [RegisterKind] for the [Constraint].
	 * @return
	 *   The [Constraint] associated with the synonym, or `null`.
	 */
	private fun <K: RegisterKind<K>> constraintOrNull(
		semanticValue: L2SemanticValue,
		kind: K
	): Constraint<K>?
	{
		val equivalent = equivalentSemanticValue(semanticValue)!!
		return stateOrNull(equivalent)?.viewFor(kind)
	}

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue] for the given [kind].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym.
	 */
	private fun <K: RegisterKind<K>> constraint(
		semanticValue: L2SemanticValue,
		kind: K
	): Constraint<K> = constraintOrNull(semanticValue, kind)!!

	/**
	 * Answer an arbitrarily ordered array of the [L2Synonym]s in this manifest,
	 * one per value per [RegisterKind] it is held in, since a synonym names its
	 * members in one kind's spelling.
	 *
	 * @return
	 *   An array of [L2Synonym]s.
	 */
	fun synonymsArray(): Array<L2Synonym> =
		states.values
			.flatMap(ValueState::views)
			.map(Constraint<*>::synonym)
			.toTypedArray()

	/**
	 * Expose the current collection of [ValueState]s as a list.
	 */
	fun valueStates(): List<ValueState> = states.values.toList()

	/**
	 * Answer a [Set] of all [L2SemanticValue]s in this manifest.  This is only
	 * exposed to make sanity checking easier.
	 */
	val allSemanticValuesForChecking: Set<L2SemanticValue> get() =
		synonymsArray().flatMapTo(mutableSetOf()) { it.semanticValues() }

	/**
	 * Anwser a [Set] of all [L2Register]s present in this manifets.  This is
	 * only exposed to make sanity checking easier.
	 */
	val allRegistersForChecking: Set<L2Register<*>> get() =
		// Aggregate: every register in the manifest, of every kind.
		states.values.flatMapTo(mutableSetOf(), ValueState::allDefinitions)


	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	fun clear()
	{
		classOf.clear()
		forward.clear()
		tagOf.clear()
		variantIdOf.clear()
		derivedFrom.clear()
		states.clear()
		impossibleRestrictionCount = 0
		clearPostponedInstructions()
		// Back to the state of a newly created manifest, [ValueClass] numbering
		// included.  A generator reuses one manifest object for every block, so
		// anything left behind here is attributed to the *next* block's values -
		// and since ids are numbered per manifest, a leftover derivation edge
		// would not merely be stale, it would relate two unrelated values that
		// happen to have been numbered alike.
		nextValueClassId = 1
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].
	 *
	 * [L2Instruction]s that move values between semantic values should
	 * customize their [L2Instruction.instructionWasAdded] method to use
	 * [recordDefinitionForMove].
	 *
	 * @param writer
	 *   The operand that received the value.
	 */
	fun recordDefinition(writer: L2WriteOperand<*>)
	{
		recordDefinitionNoCheck(writer)
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].
	 *
	 * [L2Instruction]s that move values between semantic values should
	 * customize their [L2Instruction.instructionWasAdded] method to use
	 * [recordDefinitionForMove].
	 *
	 * This form does not check consistency of the L2ValueManifest, to allow phi
	 * functions to be replaced by moves.
	 *
	 * @param writer
	 *   The operand that received the value.
	 */
	fun <K: RegisterKind<K>> recordDefinitionNoCheck(writer: L2WriteOperand<K>)
	{
		assert(writer.instructionHasBeenEmitted)
		var semanticValues = writer.semanticValues()
		val constant = writer.restriction().constantOrNull
		if (constant !== null)
		{
			// Automatically add the semantic constant if not already present in
			// the writer.
			semanticValues += L2SemanticConstant(constant)
		}
		val pickSemanticValue = semanticValues.firstOrNull(::hasSemanticValue)
		if (pickSemanticValue !== null)
		{
			// This is a new RegisterKind for an existing semantic value.
			semanticValues
				.filterNot(::hasSemanticValue)
				.forEach {
					extendSynonym(semanticValueToSynonym(pickSemanticValue), it)
				}
			updateRestriction(pickSemanticValue) {
				// Replace the restriction entirely.  This is also useful after
				// the registers have been colored, to ensure previous uses of
				// the same register won't have an effect when it's reused for
				// another purpose.
				writer.restriction()
			}
		}
		else
		{
			// This is a write to a synonym that does not yet exist.
			assert(semanticValues.none(::hasSemanticValue))
			introduceSynonym(semanticValues, writer.restriction())
		}
		updateDefinitions(semanticValues.first(), writer.kind) {
			// After register coloring, a regeneration might need to add the
			// same register to the manifest multiple times.
			if (writer.register() !in this) append(writer.register())
			else this
		}
		removePostponedInstructionFor(semanticValues.first(), writer.kind)
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  The source and destination should end up in
	 * the same synonym.
	 *
	 * @param writer
	 *   The operand that received the value.
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that already holds the value.
	 */
	fun <K: RegisterKind<K>> recordDefinitionForMove(
		writer: L2WriteOperand<K>,
		sourceSemanticValue: L2SemanticValue)
	{
		assert(writer.instructionHasBeenEmitted)
		// After constant register substitution, we need to build entries in the
		// manifest for those constants here, (because they're the source of a
		// move).
		if (!caresAboutSemanticValues
			&& sourceSemanticValue.isConstant
			&& !hasSemanticValue(sourceSemanticValue))
		{
			introduceSynonym(
				setOf(sourceSemanticValue),
				sourceSemanticValue.constantRestrictionOrNull!!)
		}

		// Always do synonym updates for moves, even in late phases
		// (needed for branch manifest manipulation)
		for (semanticValue in writer.semanticValues())
		{
			if (semanticValue == sourceSemanticValue) continue
			if (hasSemanticValue(semanticValue))
			{
				if (semanticValueToSynonym(semanticValue)
					!= semanticValueToSynonym(sourceSemanticValue))
				{
					mergeExistingSemanticValues(
						semanticValue, sourceSemanticValue)
				}
			}
			else
			{
				extendSynonym(
					semanticValueToSynonym(sourceSemanticValue), semanticValue)
			}
		}
		val register = writer.register()
		updateDefinitions(sourceSemanticValue, writer.kind) {
			when
			{
				contains(register) -> this
				else -> append(register)
			}
		}
		removePostponedInstructionFor(writer.pickSemanticValue(), writer.kind)
	}

	/**
	 * Given an [L2Register], find which [L2Synonym]s, if any, are
	 * mapped to it in this manifest.  The CFG does not have to be in SSA form.
	 *
	 * @param register
	 *   The [L2Register] to find in this manifest.
	 * @return
	 *   A [Set] of [L2Synonym]s that are mapped to the given register within
	 *   this manifest.
	 */
	fun <K: RegisterKind<K>> synonymsForRegister(
		register: L2Register<K>
	): Set<L2Synonym> = synonymsForRegisters(setOf(register))

	/**
	 * Given a [Set] of [L2Register]s, find which [L2Synonym]s, if any, are
	 * mapped to it in this manifest.  The CFG does not have to be in SSA form.
	 *
	 * @param registers
	 *   The [L2Register]s to find in this manifest.
	 * @return
	 *   A [Set] of [L2Synonym]s that are mapped to any of the given registers
	 *   within this manifest.
	 */
	private fun <K: RegisterKind<K>> synonymsForRegisters(
		registers: Set<L2Register<K>>
	): Set<L2Synonym> = when
	{
		registers.isEmpty() -> emptySet()
		else -> registers.first().kind.let { kind ->
			states.values
				.filter { state ->
					state.allDefinitions.any { def -> def in registers }
				}
				.mapToSet { it.viewFor(kind).synonym }
		}
	}

	/**
	 * Create an [L2ReadOperand] for the [L2SemanticValue] of the earliest known
	 * write for any semantic values in the same [L2Synonym] as the given
	 * [semanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to read.
	 * @return
	 *   An [L2ReadOperand] that reads the value.
	 */
	fun <K: RegisterKind<K>> read(
		semanticValue: L2SemanticValue,
		kind: K
	): L2ReadOperand<K> =
		kind.readOperand(
			semanticValue,
			restrictionFor(semanticValue))

	/**
	 * Populate the empty receiver with bindings from the manifests on edges
	 * leading to the generator's current block. Only keep the bindings for
	 * [L2SemanticValue]s that occur in all incoming manifests.  Generate phi
	 * functions as needed on the provided [generator].  The phi functions'
	 * source registers correspond positionally with the incoming edges.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] on which to write any necessary [L2_PHI]
	 *   functions.  This can be an [L2Regenerator] if there are any postponed
	 *   instructions.
	 * @param forcePhis
	 *   Whether to force creation of every possible phi instruction at this
	 *   point, even if the values always come from the same source. This is
	 *   needed for loop heads, where the back-edges only show up after that
	 *   basic block has already produced instructions.  Must only be true if
	 *   the [generator]'s [L2GeneratorInterface.mode] is [BySemanticValue].
	 */
	fun populateForMerge(
		generator: L2GeneratorInterface,
		forcePhis: Boolean)
	{
		assert(classOf.isEmpty())
		assert(states.isEmpty())
		// Here's a good place to reduce postponed instructions into simpler
		// equivalents, since we're right at the merge that would consume them,
		// and each edge will be visited once.
		val block = generator.currentBlock()
		var edges = block.predecessorEdges()
		var manifests = edges.map(L2PcOperand::manifest)
		manifests.forEach(L2ValueManifest::rewriteAllPostponed)
		when (edges.size)
		{
			// Unreachable, or an entry point where no registers are set yet.
			0 -> return
			1 if !forcePhis ->
			{
				val soleManifest = block.predecessorEdges().single().manifest()
				adoptEverythingFrom(soleManifest)
				return
			}
		}
		if (generator.mode == BySemanticValue)
		{
			// 1. Compute live semantic values (intersection across all edges).
			var liveSemanticValues = manifests
				.map(L2ValueManifest::liveOrPostponedSemanticValues)
				.reduce(Set<L2SemanticValue>::intersect)

			// 2. Force any postponed instructions in the predecessors, if they
			// don't have an equivalent in each edge.  If equivalent postponed
			// instructions are found in each edge, just propagate it to the
			// new manifest.
			if (manifests.any { it.allPostponedInstructions().isNotEmpty() })
			{
				mergeIncomingPostponedInstructions(generator)
				// Recompute liveSemanticValues after forcing postponed
				// instructions, since the manifests may have changed.  Even
				// some incoming edges may have been removed due to discovery of
				// impossible constraints.
				edges = block.predecessorEdges()
				manifests = edges.map(L2PcOperand::manifest)
				liveSemanticValues = manifests
					.map(L2ValueManifest::liveOrPostponedSemanticValues)
					.reduce(Set<L2SemanticValue>::intersect)
			}

			// 3. Build phiMap - chop synonyms into maximal consistent groups.
			// The map is from a list of synonyms, one per manifest, to the
			// semantic values that were present in all the synonyms.
			val phiMap = mutableMapOf<
				List<L2Synonym>,
				MutableList<L2SemanticValue>>()
			liveSemanticValues.forEach { sv ->
				if (manifests.all { it.hasSemanticValue(sv) })
				{
					val key = manifests.map { it.semanticValueToSynonym(sv) }
					phiMap.getOrPut(key, ::mutableListOf).add(sv)
				}
			}

			// 4. Create and populate each output synonym group.
			phiMap.values.forEach { relatedSemanticValues ->
				val pick = relatedSemanticValues[0]
				RegisterKind.all.forEach { kind ->
					if (manifests.all {
						m -> m.hasLiveSemanticValue(pick, kind.cast())
					})
					{
						populateOneSynonym(
							kind.cast(),
							relatedSemanticValues,
							manifests,
							generator,
							forcePhis)
					}
				}
			}
			if (manifests.size > 1)
			{
				generator.run {
					+L2_NOP("finished merge here")
				}
			}
		}
		else
		{
			assert(manifests.all { it.allPostponedInstructions().isEmpty() })
			val registerMaps = manifests.map { manifest ->
				val registerMap = mutableMapOf<
					L2Register<*>,
					Pair<
						MutableSet<L2SemanticValue>,
						Mutable<TypeRestriction>
					>
				>()
				// One view per (value, kind), since a register belongs to
				// exactly one kind's representation.
				val views = manifest.states.values.flatMap(ValueState::views)
				views.forEach { constraint ->
					constraint.definitions.forEach { register ->
						when (val pair = registerMap[register])
						{
							null ->
							{
								registerMap[register] = Pair(
									constraint.members.toMutableSet(),
									Mutable(constraint.restriction))
							}
							else ->
							{
								// The same register *can* occur multiple times,
								// but only after register coloring.
								assert(generator.mode is WithFixedRegisterMap)
								val (values, restriction) = pair
								values.addAll(constraint.members)
								restriction.update {
									// Use the intersection, since it's the same
									// register constrained twice.
									intersection(constraint.restriction)
								}
							}
						}
					}
				}
				registerMap
			}.toMutableList()
			// Note that the first map will be modified.
			val liveRegisters = registerMaps.removeFirst()
			registerMaps.forEach { nextMap ->
				liveRegisters.keys.retainAll(nextMap.keys)
				liveRegisters.forEach { (register, pair) ->
					val (values, restriction) = pair
					val (nextValues, nextRestriction) = nextMap[register]!!
					values.retainAll(nextValues)
					if (values.isNotEmpty())
					{
						restriction.update { union(nextRestriction.value) }
					}
				}
			}
			liveRegisters.values.removeIf { (semanticValues, _) ->
				semanticValues.isEmpty()
			}
			// We now have just the registers that are live in all incoming
			// edges.  The semantic values associated with the registers are
			// those that were associated with the same register(s) in *each* of
			// the incoming manifests, so at this point we can create maximal
			// synonyms based on overlapping membership, since they must all be
			// equivalent here anyhow.
			liveRegisters.forEach { register, (semanticValues, restriction) ->
				updateForPhiRegister(
					register,
					semanticValues.cast(),
					restriction.value)
			}
			// Also preserve semantic values that were in the same synonyms
			// across all predecessors, even if they don't have common
			// registers.
			val allLiveValues = manifests
				.map(L2ValueManifest::liveOrPostponedSemanticValues)
				.reduce(Set<L2SemanticValue>::intersect)
			val registerlessGroups = mutableMapOf<
				List<L2Synonym>,
				MutableList<L2SemanticValue>>()
			allLiveValues.forEach { sv ->
				if (!hasSemanticValue(sv) &&
					manifests.all { it.hasSemanticValue(sv) })
				{
					val key = manifests.map { it.semanticValueToSynonym(sv) }
					registerlessGroups.getOrPut(key, ::mutableListOf).add(sv)
				}
			}
			// Merge these registerless values into existing synonyms or create
			// new ones.
			registerlessGroups.values.forEach { values ->
				// Try to find any value already in the manifest that's
				// equivalent.  Reaching the scan means every probe is
				// absent from the manifest, so each probe that isn't a
				// constant can only match a candidate of its own class.
				val equivalentAnchor = values
					.firstOrNull(::hasSemanticValue)
					?: run {
						val onlyClasses = values
							.map(::classRestrictingSearchFor)
							.run {
								when
								{
									any { it === null } -> null
									else -> toSet()
								}
							}
						classOf.keys.firstOrNull { existing ->
							(onlyClasses === null
									|| existing.javaClass in onlyClasses)
								&& values.any { sv ->
									isEquivalentSemanticValue(sv, existing)
								}
						}
					}
				if (equivalentAnchor != null)
				{
					// Merge into existing synonym
					values.forEach { sv ->
						if (sv != equivalentAnchor && !hasSemanticValue(sv))
						{
							extendSynonym(
								semanticValueToSynonym(equivalentAnchor),
								sv)
						}
					}
				}
				else
				{
					// None are in the manifest, and no equivalent was
					// found.  Create a new synonym.
					val restriction = manifests
						.map { it.restrictionFor(values.first()) }
						.reduce(TypeRestriction::union)
					introduceSynonym(values, restriction)
				}
			}
		}
		// Merge synonyms that contain equivalent semantic values (e.g.,
		// constants with the same value, equivalent outer references).
		mergeAllEquivalentSynonyms()
		check()
	}

	/**
	 * Create an [L2Synonym] in this [L2ValueManifest].  It should include the
	 * [relatedSemanticValues] as members.  It should have a [TypeRestriction]
	 * that's the union of the restriction of the [relatedSemanticValues] in
	 * each of the [manifests].  If the restriction isn't a constant, ensure at
	 * least one register definition is present for the synonym, synthesizing an
	 * [L2_PHI] via the [generator] if needed.
	 *
	 * @param kind
	 *   The kind of register to use for the synonym.  This also anchors
	 *   Kotlin's type checker.
	 * @param relatedSemanticValues
	 *   The semantic values that should be members of the synonym.
	 * @param manifests
	 *   The manifests that should be used to determine the restriction of the
	 *   synonyms.  They also provide information about register definitions
	 *   along the incoming edges.
	 * @param generator
	 *   The generator to use to synthesize an [L2_PHI] if needed.
	 * @param forcePhis
	 *   Whether to force the creation of phi instructions, even if not needed.
	 *   Loops must force phi creation, even though their back-edges won't have
	 *   been created and attached yet.
	 */
	private fun <K: RegisterKind<K>> populateOneSynonym(
		kind: K,
		relatedSemanticValues: List<L2SemanticValue>,
		manifests: List<L2ValueManifest>,
		generator: L2GeneratorInterface,
		forcePhis: Boolean
	): Unit = generator.run {
		val firstSemanticValue = relatedSemanticValues[0]

		// Compute the union of the incoming restrictions.
		val restriction = manifests
			.map { it.restrictionFor(firstSemanticValue) }
			.reduce(TypeRestriction::union)
		// If the restriction is now a constant but there isn't a corresponding
		// constant in the synonym, add it.
		val constant = restriction.constantOrNull
		val semanticValuesToInclude = when
		{
			constant == null -> relatedSemanticValues
			relatedSemanticValues.any {
				it.isConstant && it.constant!! == constant
			} -> relatedSemanticValues
			else -> relatedSemanticValues + constant(constant)
		}

		// Ensure the related semantic values are in the same synonym, and
		// suitably restricted.
		agglomerateSynonym(semanticValuesToInclude, restriction)
		postponedInstructionFor(firstSemanticValue, kind)?.let {
			// There's already a postponed instruction in the manifest, which
			// will automatically populate the mhole synonym.
			return
		}

		// Find any registers that are defined for the same semantic value in
		// all incoming manifests.
		val commonRegisters = relatedSemanticValues
			.map { value ->
				manifests
					.map { m -> m.getDefinitions(value, kind).toSet() }
					.reduce(Set<L2Register<K>>::intersect)
			}
			.flatten()
			.toSet()
		// Make those common registers available at the merge.
		if (commonRegisters.isNotEmpty() && !forcePhis)
		{
			updateDefinitions(firstSemanticValue, kind) {
				plus(commonRegisters - this)
			}
		}
		else
		{
			// There were no common registers, but we might be able to construct
			// a phi that merges different registers (without emitting any
			// postponed instructions in the predecessors).
			var liveValues = relatedSemanticValues
				.filter { sv ->
					manifests.all { it.hasLiveSemanticValue(sv, kind) }
				}
			if (liveValues.isEmpty())
			{
				// There are no semantic values that are live in all edges,
				// (even in different registers).  Force firstSemanticValue to
				// be populated in each predecessor.
				val mergeBlock = currentBlock()
				mergeBlock.predecessorEdges().forEach { edge ->
					splitEdge(edge)
					forcePostponedTranslationsBeforeEdge(
						edge, listOf(firstSemanticValue to kind))
					assert(currentBlock() == mergeBlock)
				}
				assert(manifests.all {
					it.hasLiveSemanticValue(firstSemanticValue, kind)
				})
				liveValues = listOf(firstSemanticValue)
			}
			assert(liveValues.isNotEmpty())

			// Now merge the disparate registers for one of the liveValues with
			// a phi.
			val firstLive = liveValues.first()
			val sources = manifests.map { m ->
				kind.readOperand(
					firstLive,
					m.restrictionFor(firstLive),
					m.getDefinition(firstLive, kind))
			}
			addInstruction(
				kind.createPhi(
					kind.createVector(sources),
					kind.createWrite(setOf(firstLive), restriction)))
		}

		// Postpone a move into the notDefined values, if needed.
		val (defined, notDefined) =
			relatedSemanticValues.partition { hasLiveSemanticValue(it, kind) }
		assert(defined.isNotEmpty())
		if (defined.isNotEmpty() && notDefined.isNotEmpty())
		{
			move(defined.first(), notDefined)
		}
	}

	/**
	 * We're at a merge point, and there's at least one postponed instruction in
	 * a predecessor.  Move into the receiver (the manifest at the start of the
	 * merged block) any postponed instructions in common (up to equivalency) in
	 * all predecessors, but taking care not to move instructions that write
	 * values consumed by other postponed instructions that vary between
	 * predecessors.
	 *
	 * Find all instructions that don't have equivalent instructions in all
	 * incoming edges, and emit those unmatched instructions in the predecessor
	 * blocks, which may force other instructions to be emitted as well,
	 * possibly including some instructions that *do* have equivalents in all
	 * edges.
	 *
	 * Repeat until the only instructions remaining are common to all edges.
	 * Finally, move those remaining instructions into the postponed map of the
	 * receiver, the merged manifest.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] on which the transformed graph is being
	 *   written.
	 */
	private fun mergeIncomingPostponedInstructions(
		generator: L2GeneratorInterface)
	{
		// Group the postponed instructions along each incoming manifest by the
		// set of semantic values that it writes.  Then we look for instructions
		// that are in common (up to equivalency) along all edges.  For any
		// other instructions that we encounter, we force them to be
		// *recursively* generated in their predecessor blocks.  If any
		// instructions were generated on an iteration, we do another iteration
		// until no differences remain.  At that point, the remaining
		// instructions must all be equivalent along all incoming paths – even
		// having same structure of dependencies.  Move those instructions past
		// this merge point, keeping them postponed.
		val predecessorEdges = generator.currentBlock().predecessorEdges()
		val manifests = predecessorEdges.map(L2PcOperand::manifest)
		do
		{
			val instructionEquivalencesByManifest = manifests.map { manifest ->
				manifest.allPostponedInstructions()
			}
			val commonInstructions = instructionEquivalencesByManifest
				.reduce(Set<InstructionEquivalence>::intersect)

			// For each instruction that isn't in commonInstructions, force its
			// emission in its predecessor block.
			var changed = false
			// It's safe to capture the *incoming* edges, since they won't
			// change if we have to do edge-splitting.
			predecessorEdges.forEachIndexed { i, edge ->
				// NOTE: The forced emission in a predecessor can cause the
				// predecessorEdges to be modified (e.g., by splitting edges),
				// but the predecessor edge at index i will still be the same
				// effective edge, and have the effective manifest.
				val toEmit =
					instructionEquivalencesByManifest[i] - commonInstructions
				if (toEmit.isEmpty()) return@forEachIndexed
				changed = true
				generator.splitEdge(edge)
				// The same edge and manifest should be present as a
				// predecessor, even though that edge is now an operand of
				// an [L2_JUMP] (if the split was required).
				assert(predecessorEdges[i] === edge)
				assert(manifests[i] === edge.manifest())
				val pairsToForce = toEmit
					.flatMapTo(mutableSetOf()) { equivalence ->
						val kind = equivalence.kind
						equivalence.synonym.semanticValues()
							.map { sv -> sv to kind }
					}
				generator.forcePostponedTranslationsBeforeEdge(
					edge, pairsToForce)
				assert(pairsToForce.all { (sv, kind) ->
					edge.manifest().hasLiveSemanticValue(sv, kind.cast())
				})
			}
		} while (changed)
		// The same (up to equivalence) instructions are postponed in each
		// predecessor edge.  Produce new instructions by combining information
		// from corresponding originals, and add the new instructions to the
		// postponed map of the receiver.
		val selfMaps = manifests.map { manifest ->
			manifest.allPostponedInstructions().associateWith { it.instruction }
		}
		selfMaps[0].forEach { equivalence, instruction ->
			val kind = equivalence.kind
			val oldInstructions = selfMaps.map { it[equivalence]!! }
			val newInstruction =
				instruction.mergeInstructions(oldInstructions)
			agglomerateSynonym(
				equivalence.synonym.semanticValues(),
				newInstruction.writeOperands.single().restriction())
			val pick = equivalence.synonym.pickSemanticValue()
			val commonDefinitios = manifests
				.map { it.getAllDefinitions(pick, kind.cast()).toSet() }
				.reduce(Set<L2Register<*>>::intersect)
			updateDefinitions(pick, kind.cast()) {
				plus(commonDefinitios).cast()
			}
			if (newInstruction !is L2_MOVE<*>)
			{
				recordPostponedInstruction(pick, newInstruction)
			}
		}
	}

	/**
	 * Given a map from synonyms to restrictions, add this information to this
	 * manifest.  That may entail merging synonyms and narrowing restrictions.
	 *
	 * @param map
	 *   The [L2Synonym]s and associated [TypeRestriction]s to apply.
	 */
	fun applyPostPhiMap(map: Map<L2Synonym, TypeRestriction>)
	{
		map.forEach { synonym, restriction ->
			agglomerateSynonym(synonym.semanticValues(), restriction)
		}
	}

	/**
	 * Recurse through the postponed instructions, starting with the writer of
	 * [valueToCheck], looking for a dependency chain for [stopInstruction],
	 * which is also postponed.  If such a dependency path is found, answer
	 * true, otherwise answer false.  Avoid recursing into the same path twice
	 * by adding to the [ignore] set.
	 *
	 * @param valueToCheck
	 *   The [L2SemanticValue] at which to search ancestor postponed
	 *   instructions.
	 * @param stopInstruction
	 *   The [L2Instruction] which, if reached recursively, causes the
	 *   original call to return true, indicating it was found.
	 * @param ignore
	 *   The [MutableSet] of [L2Instruction]s that have been reached so far,
	 *   which prevents investigating the same dependencies more than once.
	 * @return
	 *   Whether the [stopInstruction] was reachable as an ancestor of the
	 *   [valueToCheck].
	 */
	fun checkDependency(
		valueToCheck: L2SemanticValue,
		stopInstruction: L2Instruction,
		ignore: MutableSet<L2Instruction>
	): Boolean
	{
		val instruction =
			postponedInstructionFor(valueToCheck, BOXED_KIND) ?: return false
		if (instruction == stopInstruction) return true
		if (instruction in ignore) return false
		ignore += instruction
		var any = instruction.readOperands.any { read ->
			checkDependency(read.semanticValue(), stopInstruction, ignore)
		}
		ignore -= instruction
		return any
	}

	/**
	 * A helper that allows the [RegisterKind] to be correlated among values,
	 * which Kotlin can't do for lambdas or subexpressions.  Note that the
	 * Kotlin type deduction algorithm is *very* sensitive to the order of the
	 * arguments at the call site, and is likely to break as Kotlin's type
	 * deduction algorithm changes.
	 */
	private fun <K: RegisterKind<K>> updateForPhiRegister(
		register: L2Register<K>,
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction)
	{
		val newSemanticValues = mutableSetOf<L2SemanticValue>()
		val existingSynonyms = mutableSetOf<L2Synonym>()
		semanticValues.forEach { sv ->
			if (hasSemanticValue(sv))
				existingSynonyms.add(semanticValueToSynonym(sv))
			else newSemanticValues.add(sv)
		}
		if (newSemanticValues.isNotEmpty())
		{
			introduceSynonym(newSemanticValues, restriction)
			existingSynonyms.add(L2Synonym(newSemanticValues))
		}
		val synonymsIterator = existingSynonyms.iterator()
		val sampleSynonym = synonymsIterator.next()
		val sampleSemanticValue = sampleSynonym.pickSemanticValue()
		synonymsIterator.forEachRemaining { nextSynonym ->
			mergeExistingSemanticValues(
				sampleSemanticValue,
				nextSynonym.pickSemanticValue())
		}
		// All the relevant synonyms and semantic values are merged.
		updateDefinitions(sampleSemanticValue, register.kind) { plus(register) }
	}

	/**
	 * Look for equivalent [L2SemanticValue]s in separate [L2Synonym]s, and
	 * merge them.
	 *
	 * This also recomputes the impossible restriction count, since merging
	 * groups will intersect their restrictions, potentially introducing
	 * impossible restrictions.  Merging two already impossible restrictions
	 * will also *decrease* the count.
	 */
	fun mergeAllEquivalentSynonyms()
	{
		// First we determine the equivalence set of synonyms, based on whether
		// two synonyms share equivalent members.
		val initialSynonyms = synonymsArray()
		val count = initialSynonyms.size
		val groups = Array(count, ::mutableListOf)
		for (i in 0..<count)
		{
			inner@for (j in 0..<i)
			{
				val group1 = groups[i]
				val group2 = groups[j]
				if (group1 == group2) continue
				val syn1 = initialSynonyms[i].semanticValues()
				val syn2 = initialSynonyms[j].semanticValues()
//				if (constraintOrNull(syn1.first())!!.definitions.isEmpty() !=
//					constraintOrNull(syn2.first())!!.definitions.isEmpty())
//				{
//					// DO NOT mix synonyms where one has no definitions and the
//					// other does.  A synonym with no definition indicates it's
//					// holding information (restriction, synonymy) for a
//					// postponed instruction.
//					continue
//				}
				if (syn1.any { value1 ->
					syn2.any { value2 ->
						isEquivalentSemanticValue(value1, value2)
					}})
				{
					// Merge the equivalence sets.
					val (big, little) = when
					{
						group1.size > group2.size -> group1 to group2
						else -> group2 to group1
					}
					big.addAll(little)
					for (eachLittle in little)
					{
						groups[eachLittle] = big
					}
				}
			}
		}
		// We now know which sets of synonyms should be merged.  Merge them.
		for (group in groups.toSet())
		{
			for (i in 1..<group.size)
			{
				mergeExistingSemanticValues(
					initialSynonyms[group[0]].pickSemanticValue(),
					initialSynonyms[group[i]].pickSemanticValue())
			}
		}
		// Recompute the impossible restriction count.
		impossibleRestrictionCount =
			states.values.count(ValueState::isImpossible)
	}

	/**
	 * Transform this manifest by mapping its [L2SemanticValue]s.
	 *
	 * @param semanticValueTransformer
	 *   The transformation for [L2SemanticValue]s.
	 * @return
	 *   The transformed manifest.
	 */
	fun transform(
		semanticValueTransformer: (L2SemanticValue) -> L2SemanticValue
	): L2ValueManifest
	{
		assert(mode == BySemanticValue)
		val newManifest = L2ValueManifest(mode)
		for (oldSynonym in synonymsArray())
		{
			newManifest.introduceSynonym(
				oldSynonym.semanticValues().map(semanticValueTransformer),
				restrictionFor(oldSynonym.pickSemanticValue()))
		}
		newManifest.check()
		return newManifest
	}

	/**
	 * Retain as definitions only those [L2Register]s that are in the given
	 * [Set], removing the rest.
	 *
	 * @param registersToRetain
	 *   The [L2Register]s that can be retained by the list of definitions as
	 *   all others are removed.
	 * @return
	 *   Whether the manifest was modified.
	 */
	fun retainRegisters(registersToRetain: Set<L2Register<*>>): Boolean
	{
		// Iterate over a copy of the map, so we can remove from it.  Registers of
		// every kind are subject to retention, so every kind's synonym is visited.
		var changed = false
		synonymsArray().forEach { synonym ->
			RegisterKind.all.forEach { kind ->
				changed = changed ||
					retainRegistersHelper(
						registersToRetain, synonym, kind.cast())
			}
		}
		// Removing constraints can only transition from impossible to
		// possible.
		impossibleRestrictionCount = states.values.count { it.isImpossible }
		check()
		return changed
	}

	/**
	 * A helper to allow the [RegisterKind] to be fixed within this scope.
	 * Answer whether any changes were made.
	 */
	private fun <K: RegisterKind<K>> retainRegistersHelper(
		registersToRetain: Set<L2Register<*>>,
		synonym: L2Synonym,
		kind: K
	): Boolean = updateConstraint(synonym, kind) {
		val definitionList = definitions.toMutableList()
		val changed = definitionList.retainAll(registersToRetain)
		if (changed)
		{
			definitions = definitionList
			if (definitionList.isEmpty())
			{
				// Remove this synonym and any semantic values within it.
				states.remove(classFor(synonym.pickSemanticValue()))
				classOf.keys.removeAll(
					synonym.semanticValues())
			}
		}
		changed
	}

	/**
	 * Remove all occurrences of the specified registers from this manifest.
	 */
	fun removeRegisters(registersToBeOverwritten: Set<L2Register<*>>)
	{
		if (registersToBeOverwritten.isEmpty()) return
		synonymsArray().forEach { synonym ->
			RegisterKind.all.forEach { kind ->
				updateDefinitions(synonym.pickSemanticValue(), kind.cast()) {
					filterNot(registersToBeOverwritten::contains)
				}
			}
		}
	}

	/**
	 * Retain information only about the [L2SemanticValue]s that are present in
	 * the given [Set], removing the rest.
	 *
	 * @param semanticValuesToRetain
	 *   The [L2SemanticValue]s that can be retained in the manifest.
	 */
	fun retainSemanticValues(
		semanticValuesToRetain: Set<L2SemanticValue>)
	{
		check()
		for (syn in synonymsArray())
		{
			retainSemanticValuesInSynonym(syn, semanticValuesToRetain)
		}
		check()
	}

	/**
	 * Within the given [L2Synonym], retain information only about the
	 * [semanticValuesToRetain].
	 */
	fun retainSemanticValuesInSynonym(
		synonym: L2Synonym,
		semanticValuesToRetain: Set<L2SemanticValue>)
	{
		val originalSemanticValues = synonym.semanticValues()
		val intersection =
			originalSemanticValues.intersect(semanticValuesToRetain)
		val valueClass = classFor(synonym.pickSemanticValue())
		val state = states[valueClass]!!
		val constant = state.restriction.constantOrNull
		val newSemanticValues: Set<L2SemanticValue> = when
		{
			// DO NOT add a constant to the new synonym if it's empty.
			intersection.isEmpty() -> intersection
			// Not a constant.
			constant == null -> intersection
			// Combine non-empty survivor set with a constant.
			else -> intersection + constant(constant)
		}
		// Exit quickly if no change to the synonym.
		if (newSemanticValues == originalSemanticValues) return
		// Unbind every original member; the survivors are rebound below.
		classOf.keys.removeAll(originalSemanticValues)
		// Exit quickly if no semantic values survive.
		if (newSemanticValues.isEmpty())
		{
			states.remove(valueClass)
			// See if we just eliminated an impossible constraint.
			if (state.isImpossible) impossibleRestrictionCount--
			return
		}
		// The class survives with a reduced membership; only its state is
		// replaced, so anything still holding the class stays valid.
		states[valueClass] = state.withMembers(newSemanticValues)
		bind(newSemanticValues, valueClass)
	}

	/**
	 * If the given [L2SemanticValue] is derived from some other value – an
	 * [L2SemanticExtractedTag] or an [L2SemanticObjectVariantId].  Answer the
	 * value it was derived from, otherwise answer `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to examine.
	 * @return
	 *   The value it was derived from, or `null` if it is not a derived value.
	 */
	private fun derivationBaseOrNull(
		semanticValue: L2SemanticValue
	): L2SemanticValue? = when (semanticValue)
	{
		is L2SemanticExtractedTag -> semanticValue.base
		is L2SemanticObjectVariantId -> semanticValue.base
		else -> null
	}

	/**
	 * Check that no synonym mentions a derived [L2SemanticValue] whose base is
	 * absent from this manifest.
	 *
	 * A derived value such as `Tag(x)` names a fact *about* `x`, but it lives
	 * in a synonym of its own that is related to `x`'s synonym only by the
	 * spelling of the semantic value.  Nothing structural prevents `x` from
	 * being dropped while `Tag(x)` survives, and when that happens the manifest
	 * still claims to know the tag of a value it no longer knows at all.  Later
	 * passes that reasonably assume the base is present – code splitting in
	 * particular, which consults the tag to decide what to duplicate – then
	 * misbehave a long way from the damage.
	 *
	 * TODO Remove this check once derived values become `tag` and `variantId`
	 *  edges from the base's `ValueState`.  At that point a derived class
	 *  cannot outlive its base, because it has nowhere to hang, and this whole
	 *  category of inconsistency stops being representable.
	 */
	private fun checkDerivedValuesHaveTheirBases()
	{
		states.values.forEach { state ->
			// The canonical, boxed members suffice: `Tag(x)` and `Int(Tag(x))`
			// are derived from the same `x`.
			state.members.forEach { member ->
				val base = derivationBaseOrNull(member) ?: return@forEach
				assert(hasSemanticValue(base))
				{
					buildString {
						append("Manifest holds a derived semantic value ")
						append("whose base it does not know.")
						append("\n  Derived: $member")
						append("\n  Missing base: $base")
						append("\n  Its synonym: ${state.members}")
					}
				}
			}
		}
	}

	fun checkUniqueConstantSynonyms()
	{
		val constants = mutableMapOf<AvailObject, MutableSet<L2Synonym>>()
		states.values.forEach { state ->
			state.restriction.constantOrNull?.let { constant ->
				val synonym = state.synonym
				constants.computeIfAbsent(constant) {
					mutableSetOf()
				}.add(synonym)
			}
		}
		val nonunique = constants.entries.filter { (_, syns) ->
			syns.size > 1
				|| syns.single().semanticValues().none { it.isConstant }
		}
		assert(nonunique.isEmpty())
		{
			buildString {
				append(
					"Multiple synonyms are constrained to the same constant, " +
						"or are missing a semantic constant:")
				nonunique.forEach { (con, syns) ->
					append("\n\t$syns = $con")
				}
			}
		}
	}

	companion object
	{
		/** Perform deep, slow checks every time a manifest changes. */
		var deepManifestDebugCheck = true //  DEBUG: false
	}
}
