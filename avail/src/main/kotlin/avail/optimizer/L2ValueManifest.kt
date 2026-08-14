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
package avail.optimizer

import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectLayoutVariant.Companion.variantFromId
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instanceTag
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i31
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.descriptor.types.TypeTag
import avail.descriptor.types.TypeTag.Companion.restrictionForTagRestriction
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Instruction.InstructionEquivalence
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.bottomRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.boxedRestrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.topRestriction
import avail.interpreter.levelTwo.operation.L2_IMPOSSIBLE_CODE_CONTINUING_FOR_NOW
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_NOP
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2Optimizer.GenerationMode
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2Optimizer.GenerationMode.WithFixedRegisterMap
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedFloat
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticDummy
import avail.optimizer.values.L2SemanticExtractedTag
import avail.optimizer.values.L2SemanticObjectVariantId
import avail.optimizer.values.L2SemanticPrimitiveInvocation
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedFloat.Companion.boxed
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.optimizer.values.L2SemanticValue
import avail.utility.Mutable
import avail.utility.PrefixSharingList.Companion.append
import avail.utility.cast
import avail.utility.isNullOr
import avail.utility.mapToSet
import avail.utility.notNullAnd

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
	 * This is the slot that lets one value be described in several
	 * representations at once.  Today a [Constraint] holds exactly one, matching
	 * the single-kind classes the manifest still has.  When int and float
	 * representations can hang off the same value, the boxed/int/float
	 * distinction moves entirely in here, and an [L2ReadIntOperand] can name a
	 * *boxed* [L2SemanticValue] and find its definition in the int
	 * representation.  That is what allows [L2SemanticUnboxedInt] and
	 * [L2SemanticUnboxedFloat] to be deleted: the operand's static kind selects
	 * the representation, so the semantic value no longer has to carry it.
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
		 * Answer a copy of the receiver with the given definitions.
		 *
		 * @param newDefinitions
		 *   The replacement definitions.
		 * @return
		 *   The new [Representation], or the receiver if nothing changed.
		 */
		fun withDefinitions(newDefinitions: List<L2Register<K>>) = when
		{
			newDefinitions == definitions -> this
			else -> Representation(kind, newDefinitions, postponedInstruction)
		}

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
		 * an int register with no boxed register yet – the result of an
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
		fun definedMembers(): Set<L2SemanticValue<K>> =
			definitions
				.flatMap(L2Register<K>::definitions)
				.flatMap(L2WriteOperand<K>::semanticValues)
				.toSet()

		/**
		 * Answer the owning [Constraint]'s members that do **not** yet have a
		 * defining write in this representation's [kind], and so would be
		 * populated by its [postponedInstruction] – or by an implicit move, when
		 * there is no explicit instruction.
		 *
		 * @param owner
		 *   The [Constraint] this representation belongs to.
		 * @return
		 *   The owner's members lacking a definition of this kind.
		 */
		fun notDefinedMembers(
			owner: Constraint<K>
		): Set<L2SemanticValue<K>> = owner.members - definedMembers()
	}

	/**
	 * A utility type describing one value: the [L2SemanticValue]s that name it,
	 * the [TypeRestriction] bounding it, and the [Representation] holding its
	 * register-level facts.
	 *
	 * @property definitions
	 *   An immutable [List] of [L2Register]s that hold the same value.  They
	 *   all have the same [RegisterKind] as the associated [L2Synonym]; an
	 *   earlier incarnation mixed kinds within one constraint, which caused
	 *   problems, and is not something to reintroduce.  The list may be
	 *   replaced, but not internally modified.  Also, the caller must not
	 *   modify the list after passing it to this constructor.
	 * @property restriction
	 *   The [TypeRestriction] that describes the types, constant values,
	 *   excluded types, excluded values, and [RegisterKind]s that constrain the
	 *   registers of some [L2Synonym].
	 * @property postponedInstruction
	 *   The optional [L2Instruction] that is responsible for populating
	 *   semantic values of this constraint that do not yet have definitions.
	 *   It's an instruction that has *not* yet been emitted, and might not ever
	 *   be, if the semantic values it populates are never read.
	 * @property members
	 *   The [L2SemanticValue]s that this constraint describes.  This is the
	 *   same set as the associated [L2Synonym]'s, but held here rather than
	 *   being read back out of the map key, so that the key is free to become
	 *   a pure identity.
	 */
	class Constraint<K: RegisterKind<K>>(
		val members: Set<L2SemanticValue<K>>,
		val restriction: TypeRestriction,
		val representation: Representation<K>,
		private val otherRepresentations: List<Representation<*>> = emptyList())
	{
		/**
		 * Every [Representation] describing this value, in whatever
		 * [RegisterKind]s it currently has.
		 *
		 * While a class is single-kinded this is just [representation].  The
		 * "primary plus others" asymmetry is scaffolding: it keeps this
		 * additive, and it disappears when the `<K>` parameter does and all
		 * representations become peers.
		 */
		val allRepresentations: List<Representation<*>> get() = when
		{
			otherRepresentations.isEmpty() -> listOf(representation)
			else -> listOf(representation) + otherRepresentations
		}

		/**
		 * Answer the [Representation] describing this value in the given
		 * [RegisterKind], or `null` if it has none.
		 *
		 * @param kind
		 *   The [RegisterKind] of interest.
		 * @return
		 *   That kind's [Representation], or `null`.
		 */
		fun representationFor(
			kind: RegisterKind<*>
		): Representation<*>? =
			allRepresentations.firstOrNull { it.kind == kind }

		/**
		 * Every [L2Register] holding this value, across all of its
		 * [Representation]s.
		 *
		 * Callers wanting *a particular kind's* registers must ask
		 * [representationFor] instead.  This is for the aggregate questions –
		 * "which registers does this manifest mention at all" – where taking
		 * only one kind's would silently under-report once a value has more than
		 * one representation.
		 */
		val allDefinitions: List<L2Register<*>> get() =
			allRepresentations.flatMap(Representation<*>::definitions)

		/**
		 * Answer a copy of the receiver whose [Representation] for the given
		 * one's [RegisterKind] is replaced, or added if the value did not
		 * previously have that kind.
		 *
		 * The primary [representation] is only replaced when the kinds match, so
		 * a value that gains an int representation keeps its boxed one as
		 * primary.  That asymmetry is scaffolding – see [allRepresentations].
		 *
		 * @param newRepresentation
		 *   The [Representation] to install.
		 * @return
		 *   The new [Constraint], or the receiver if nothing changed.
		 */
		fun withRepresentation(
			newRepresentation: Representation<*>
		): Constraint<K> = when
		{
			newRepresentation === representation -> this
			newRepresentation.kind == representation.kind -> Constraint(
				members,
				restriction,
				newRepresentation.cast(),
				otherRepresentations)
			else -> Constraint(
				members,
				restriction,
				representation,
				otherRepresentations.filter {
					it.kind != newRepresentation.kind
				} + newRepresentation)
		}
		/**
		 * Build a constraint with a single [Representation], inferring its
		 * [RegisterKind] from the members.  This is the shape the manifest still
		 * uses everywhere, since a class currently has one kind.
		 *
		 * @param members
		 *   The [L2SemanticValue]s naming this value.
		 * @param definitions
		 *   The [L2Register]s holding it.
		 * @param restriction
		 *   The [TypeRestriction] bounding it.
		 * @param postponedInstruction
		 *   The postponed [L2Instruction] that would populate it, or `null`.
		 */
		constructor(
			members: Set<L2SemanticValue<K>>,
			definitions: List<L2Register<K>>,
			restriction: TypeRestriction,
			postponedInstruction: L2Instruction?
		): this(
			members,
			restriction,
			Representation(
				members.first().kind,
				definitions,
				postponedInstruction))

		/** The [L2Register]s holding this value, in its sole representation. */
		val definitions: List<L2Register<K>> get() = representation.definitions

		/**
		 * The postponed [L2Instruction] that would populate this value, in its
		 * sole representation.
		 */
		val postponedInstruction: L2Instruction?
			get() = representation.postponedInstruction

		/**
		 * A lazily materialized [L2Synonym] view of [members].  Most constraints
		 * are never asked for one.
		 *
		 * Mutating this field is safe even though a [Constraint] is shared
		 * between manifests: it is a pure memoization of immutable data, so
		 * every computation of it produces an equal result.  If [members] ever
		 * becomes replaceable, null this slot at the same time.
		 */
		private var cachedSynonym: L2Synonym<K>? = null

		/** An [L2Synonym] view of this constraint's [members]. */
		val synonym: L2Synonym<K>
			get() = cachedSynonym ?: L2Synonym(members).also {
				cachedSynonym = it
			}

		init
		{
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

		/**
		 * Answer the set of semantic values that have been defined, meaning they
		 * appear in registers with at least one write operand.
		 *
		 * While a class has a single [Representation] this is that
		 * representation's answer.  Once there are several, callers that mean a
		 * particular kind must ask that representation directly; the aggregate
		 * over all of them is a different question, and conflating the two would
		 * treat "available boxed" and "available as an int" as the same thing.
		 *
		 * @return
		 *   The set of [L2SemanticValue]s that have visible definitions.
		 */
		fun definedSemanticValues(): Set<L2SemanticValue<K>> =
			representation.definedMembers()

		fun postponedInstructionIncludingImplicitMove(
			synonym: L2Synonym<K>,
			manifest: L2ValueManifest
		): L2Instruction?
		{
			if (postponedInstruction != null)
				return postponedInstruction
			val defined = definedSemanticValues()
			val undefined = synonym.semanticValues() - defined
			return when
			{
				// No need to create a synthetic move.
				undefined.isEmpty() -> null
				// No definition source, so it must constant-valued.
				defined.isEmpty() ->
				{
					assert(restriction.isConstant)
					undefined.first().kind.moveConstant(
						restriction.constantOrNull!!, undefined)
				}
				// Move from a defined value to the undefined ones.
				else -> undefined.first().kind.dynamicMove(
					defined.first(), undefined, manifest, restriction)
			}
		}

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
		val isImpossible get() = restriction.isImpossible
	}

	/**
	 * A mutable variation of [Constraint], suitable for use in circumstances
	 * where a constraint needs to be updated by a lambda, without breaking the
	 * sharing of constraints between manifests.
	 *
	 * @param constraint
	 *   The original [Constraint] on which to model the mutable builder.
	 * @param synonym
	 *   The [L2Synonym] for which this constraint is being built.  This can be
	 *   quite convenient during constraint updates.
	 */
	class ConstraintBuilder<K: RegisterKind<K>>(
		constraint: Constraint<K>,
		val synonym: L2Synonym<K>)
	{
		/** Capture the original [Constraint]. */
		private val originalConstraint = constraint

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
		 * Either reuse the original [Constraint] if the receiver hasn't been
		 * [modified], or synthesize a new one.
		 */
		fun toConstraint() = when
		{
			!modified -> originalConstraint
			else -> Constraint(
				synonym.semanticValues(),
				definitions,
				restriction,
				postponedInstruction)
		}
	}


	/**
	 * The [ValueClass] that each [L2SemanticValue] currently belongs to.  The
	 * class recorded here may have been merged away since, so every read must
	 * go through [resolve]; use [classOrNull] or [classFor] rather than
	 * indexing this map directly.
	 */
	private val classOf: MutableMap<L2SemanticValue<*>, ValueClass>?

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
	private val tagOf: MutableMap<ValueClass, ValueClass>

	/**
	 * The [ValueClass] holding the [ObjectLayoutVariant] id extracted from each
	 * base [ValueClass], where such a value is known.  The variant counterpart
	 * of [tagOf].
	 */
	private val variantIdOf: MutableMap<ValueClass, ValueClass>

	/**
	 * The base [ValueClass] that each derived [ValueClass] describes – the
	 * *backward* edge of [tagOf] and [variantIdOf].  Narrowing a derived value
	 * constrains its base, so the relation has to be navigable in both
	 * directions.
	 */
	private val derivedFrom: MutableMap<ValueClass, ValueClass>

	/**
	 * A map from each [ValueClass] to the [Constraint] describing what this
	 * manifest knows about that value: its membership, its [TypeRestriction],
	 * the [L2Register]s holding it, and any postponed [L2Instruction].
	 *
	 * Keys are always resolved; a class that has been merged away has no entry.
	 */
	private val states: MutableMap<ValueClass, Constraint<*>>

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
	 * Answer whether there are any impossible restrictions in this manifest.
	 */
	val hasImpossibleRestriction: Boolean get() = impossibleRestrictionCount > 0

	/**
	 * Repeatedly reduce the postponed instructions until no more reductions are
	 * available.
	 */
	fun rewriteAllPostponed()
	{
		do
		{
			var changed = false
			states.entries.toList().forEach { (valueClass, constraint) ->
				// A previous rewrite may have disrupted the synonym structure,
				// so we have to check it here.  It can only have had an effect
				// if it answered true, in which case we'll do another pass to
				// make sure we get every rewrite that we can.
				if (valueClass in states)
				{
					val synonym = constraint.synonym
					constraint.postponedInstruction?.let { instruction ->
						val newChange = instruction.run {
							rewritePostponed(synonym)
						}
						changed = newChange or changed
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
		classOf = when (mode)
		{
			BySemanticValue -> mutableMapOf()
			else -> null
		}
		forward = mutableMapOf()
		tagOf = mutableMapOf()
		variantIdOf = mutableMapOf()
		derivedFrom = mutableMapOf()
		states = mutableMapOf()
		postponedReaders = mutableMapOf()
	}

	/**
	 * Copy an existing manifest.  Clone the maps, and also clone the mutable
	 * [Constraint] associated with each synonym.
	 *
	 * @param original
	 *   The original [L2ValueManifest].
	 */
	constructor(original: L2ValueManifest)
	{
		mode = original.mode
		classOf = original.classOf?.toMutableMap()
		forward = original.forward.toMutableMap()
		tagOf = original.tagOf.toMutableMap()
		variantIdOf = original.variantIdOf.toMutableMap()
		derivedFrom = original.derivedFrom.toMutableMap()
		states = original.states.toMutableMap()
		postponedReaders = original.postponedReaders
			.mapValuesTo(mutableMapOf()) { (_, targets) ->
				targets.toMutableSet()
			}
		impossibleRestrictionCount = original.impossibleRestrictionCount
	}

	/**
	 * Record an [L2Instruction] suitable for subsequent emission, if necessary,
	 * to produce its output values.  The sole writeOperand should have no
	 * target semantic values, as these will be supplied by the current synonym
	 * at emission time.
	 *
	 * @param semanticValue
	 *   A semantic value that will be defined by the instruction.  This is used
	 *   to locate the synonym under which to record the [instruction].
	 * @param instruction
	 *   The instruction to record for later emission.  It must have no target
	 *   semantic values in its sole writeOperand.
	 */
	fun recordPostponedInstruction(
		semanticValue: L2SemanticValue<*>,
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
			dynamicAgglomerateSynonym(
				setOf(semanticValue, source),
				originalWrite.restriction())
			// Never replace an existing postponed instruction with a new move.
			// The existing instruction will automatically write to any semantic
			// values that get added to the synonym.
			if (postponedInstructionFor(semanticValue) != null) return
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
		semanticValue: L2SemanticValue<*>,
		instruction: L2Instruction)
	{
		val originalWrite = instruction.writeOperands.single()
		val constant = originalWrite.restriction().constantOrNull
		if (constant != null)
		{
			// Ensure postponable instructions that produce a constant simply
			// augment an existing synonym, or at worst become a constant move.
			dynamicAgglomerateSynonym(
				setOf(originalWrite.kind.createSemanticConstant(constant)),
				originalWrite.restriction())
			return
		}
		updateConstraint(semanticValueToSynonym(semanticValue)) {
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
	private fun renarrowPostponedConsumersOf(narrowed: L2SemanticValue<*>)
	{
		if (!caresAboutSemanticValues) return
		// Bound the mutual recursion with narrowing.  Chains of postponed
		// instructions are short; anything deeper simply waits until the value
		// is actually needed, when the emit-time refresh handles it.
		if (renarrowDepth >= maxRenarrowDepth) return
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
				val postponed = state.postponedInstruction ?: return@forEach
				if (postponed.writeOperands.size != 1) return@forEach
				val narrowedClone = postponed.narrowedForManifest(this)
				if (narrowedClone === null) return@forEach
				val implied = narrowedClone.impliedWriteRestriction(
					narrowedClone.readOperands.map { it.restriction() })
				narrowedClone.writeOperands.single().restrict { implied }
				val target = state.synonym.pickSemanticValue()
				updateConstraint(state.synonym) {
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
						updateConstraint(semanticValueToSynonym(target)) {
							postponedInstruction = null
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
	 * Answer a map where the values are each postponed [L2Instruction] in this
	 * entire manifest, and whose corresponding keys are synonyms that will be
	 * populated by them.
	 *
	 * @return
	 *   A [Map] from [L2Synonym] to postponed [L2Instruction].
	 */
	fun allPostponedInstructions(): Map<L2Synonym<*>, L2Instruction> =
		states.values
			.filter { it.postponedInstruction != null }
			.associate { it.synonym to it.postponedInstruction!! }

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
	fun postponedInstructionFor(
		semanticValue: L2SemanticValue<*>
	): L2Instruction? = constraint(semanticValue).postponedInstruction

	/**
	 * If there's a postponed instruction for the synonym containing the given
	 * semantic value, answer that instruction, removing it from the manifest.
	 * Otherwise answer `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   The postponed [L2Instruction] for the given semantic value, or `null`
	 *   if none existed.
	 */
	fun <K: RegisterKind<K>> removePostponedInstructionFor(
		semanticValue: L2SemanticValue<K>
	): L2Instruction?
	{
		if (!caresAboutSemanticValues) return null
		if (!hasSemanticValue(semanticValue)) return null
		val instruction = postponedInstructionFor(semanticValue)
		if (instruction == null)
		{
			// Synthesize a postponed instruction to return.
			val values = semanticValueToSynonym(semanticValue).semanticValues()
			val defined = getDefinitions(semanticValue)
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
					semanticValue.kind.dynamicMove(
						defined.first(), emptySet(), this, restriction)
				restriction.isConstant ->
					semanticValue.kind.moveConstant(
						restriction.constantOrNull!!,
						emptySet())
				else -> null
			}
		}
		updateConstraint(semanticValueToSynonym(semanticValue)) {
			postponedInstruction = null
		}
		return instruction
	}

	/** Remove all postponed instructions. */
	fun clearPostponedInstructions()
	{
		postponedReaders.clear()
		states.entries.forEach { entry ->
			val constraint = entry.value
			if (constraint.postponedInstruction != null)
			{
				entry.setValue(
					Constraint(
						constraint.members,
						constraint.definitions,
						constraint.restriction,
						null))
			}
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
		synonym: L2Synonym<K>,
		body: ConstraintBuilder<K>.() -> Result
	): Result
	{
		val valueClass = classOrNull(synonym.pickSemanticValue())
			?: ValueClass.newValueClass().also { fresh ->
				bind(synonym.semanticValues(), fresh)
			}
		var constraint = states[valueClass]
		if (constraint == null)
		{
			constraint = Constraint(
				synonym.semanticValues(),
				emptyList(),
				bottomRestriction,
				null)
			impossibleRestrictionCount++
			bind(synonym.semanticValues(), valueClass)
		}
		val builder = ConstraintBuilder(constraint.cast(), synonym)
		val oldRestriction = constraint.restriction
		val result = builder.body()
		assert(caresAboutSemanticValues || builder.postponedInstruction == null)
		states[valueClass] = builder.toConstraint()
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
	 * Update the [TypeRestriction] in the [Constraint] associated with the
	 * [L2Synonym] containing the given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @param body
	 *   How to transform the restriction, taking the old one as the receiver
	 *   and answering the new one.
	 */
	fun <K: RegisterKind<K>> updateRestriction(
		semanticValue: L2SemanticValue<K>,
		body: TypeRestriction.() -> TypeRestriction
	): Unit
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
		updateConstraint(
			semanticValueToSynonym(semanticValue)
		) {
			restriction = restriction.intersection(restriction.body())
		}
		val newRestriction = restrictionFor(semanticValue)
		newRestriction.constantOrNull?.let { constant ->
			val semanticConstant =
				semanticValue.kind.createSemanticConstant(constant)
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
	private fun <K: RegisterKind<K>> propagateForRestrictionChange(
		semanticValue: L2SemanticValue<K>)
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
				val semanticConstant: L2SemanticValue<K> =
					semanticValue.kind.createSemanticConstant(constant)
				val synonym = semanticValueToSynonym(semanticValue)
				val constSynonym: L2Synonym<K>? =
					semanticValueToSynonymOrNull(semanticConstant)
				when (constSynonym) {
					null -> extendSynonym(synonym, semanticConstant)
					else -> mergeExistingSemanticValues(
						semanticValue, semanticConstant)
				}
			}
		}

		when (semanticValue)
		{
			is L2SemanticBoxedValue ->
			{
				if (restriction.containedByType(i32))
				{
					// The boxed form was restricted, so similarly restrict the
					// int form.
					intFormOf(semanticValue)?.let { unboxedInt ->
						updateRestriction(unboxedInt) {
							restriction.forUnboxedInt()
						}
					}
				}
				if (restriction.containedByType(DOUBLE()))
				{
					// The boxed form was restricted, so similarly restrict the
					// double form.  Floats/doubles don't have range types yet,
					// but we support instance types.
					floatFormOf(semanticValue)?.let { unboxedFloat ->
						updateRestriction(unboxedFloat) {
							restriction.forUnboxedFloat()
						}
					}
				}
				tagFormOf(semanticValue)?.let { intTagValue ->
					// The boxed form was restricted, so see if we can prove a
					// stronger bound for the [TypeTag].
					updateRestriction(intTagValue) {
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
			is L2SemanticUnboxedInt ->
			{
				// The int form was restricted, so similarly restrict the boxed
				// form.
				boxedFormOfInt(semanticValue)?.let { base ->
					updateRestriction(base) {
						restriction.forBoxed()
					}
				}
				when (val baseOfInt = semanticValue.boxed)
				{
					is L2SemanticExtractedTag ->
					{
						// Propagate the tighter tag restriction to a tighter
						// restriction on the source object.
						val boxedSource = baseOfInt.base
						val restrictionFromTag =
							restrictionForTagRestriction(restriction)
						equivalentSemanticValue(boxedSource)?.let {
							updateRestriction(it) {
								restrictionFromTag
							}
						}
						intFormOf(boxedSource)?.let {
							updateRestriction(it) {
								restrictionFromTag.forUnboxedInt()
							}
						}
						floatFormOf(boxedSource)?.let {
							updateRestriction(it) {
								restrictionFromTag.forUnboxedFloat()
							}
						}
					}
					is L2SemanticObjectVariantId ->
					{
						// The semantic value was populated from a variant id
						// (from an object or object type), but we can't go
						// backward to the variant itself to narrow the
						// source value whose variant was extracted.  We simply
						// don't keep that backward map from id to variant.  But
						// we can eliminate any explicitly mentioned variants
						// from the base value based on the strengthened variant
						// id.
						equivalentSemanticValue(baseOfInt.base)?.let {
								objectValue ->
							// The objectValue holds either an object or an
							// object type.
							val variantIdRange =
								restrictionFor(semanticValue).type
							assert(variantIdRange.isSubtypeOf(i31))
							// Multi-way variant dispatching is pretty much
							// always by exact match on the id, so look for an
							// exact match to constrain the corresponding
							// object's type.
							val id = variantIdRange.lowerBound
							val variant: ObjectLayoutVariant? = when
							{
								id.equals(variantIdRange.upperBound) ->
									variantFromId(id.extractInt)
								else -> null
							}
							var baseType = restrictionFor(objectValue).type
							when
							{
								variant == null -> { }
								baseType.isSubtypeOf(mostGeneralObjectType) ->
								{
									updateRestriction(objectValue) {
										boxedRestrictionForType(
											variant.mostGeneralObjectType
										).intersectionWithObjectVariant(variant)
									}
								}
								baseType.isSubtypeOf(mostGeneralObjectMeta) ->
								{
									updateRestriction(objectValue) {
										boxedRestrictionForType(
											variant.mostGeneralObjectMeta
										).intersectionWithObjectTypeVariant(
											variant)
									}
								}
							}
						}
					}
					is L2SemanticPrimitiveInvocation ->
					{
						// The semantic value represents the unboxedInt form of
						// some stable primitive invocation.  Dispatch to the
						// primitive, so it can specialize how to handle further
						// propagation.
						val boxedRestriction = restriction.forBoxed()
						baseOfInt.primitive.propagateManifestRestrictions(
							baseOfInt.argumentSemanticValues,
							this,
							boxedRestriction)
					}
				}
			}
			is L2SemanticUnboxedFloat ->
			{
				boxedFormOfFloat(semanticValue)?.let { base ->
					// The float form was just narrowed, so narrow the boxed
					// form correspondingly.
					updateRestriction(base) {
						restriction.forBoxed()
					}
				}
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
		semanticValue: L2SemanticValue<K>,
		body: List<L2Register<K>>.() -> List<L2Register<K>>
	): Unit = updateConstraint(semanticValueToSynonym(semanticValue)) {
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
				classOf!!.values.mapTo(mutableSetOf(), ::resolve) ==
					states.keys)
			checkDerivedValuesHaveTheirBases()

			// Check each constraint for consistency with its synonym. Postponed
			// instructions are now source-only (no explicit targets), with
			// targets derived contextually from the synonym's not-defined
			// semantic values. There is no special case for constant
			// restrictions - they require an explicit postponed constant move
			// instruction.
			for (constraint in states.values)
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
		val registers = states.values.flatMap(Constraint<*>::allDefinitions)
		if (mode !is WithFixedRegisterMap)
		{
			assert(registers.size == registers.toSet().size)
		}
		for (constraint in states.values)
		{
			assert(constraint.members
				.distinctBy(L2SemanticValue<*>::kind)
				.size == 1)
			{
				"Semantic values in synonym are of mixed kind"
			}
		}
		val count = states.values.count(Constraint<*>::isImpossible)
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
	fun liveOrPostponedSemanticValues(): Set<L2SemanticValue<*>> =
		states.values
			.filter { constraint ->
				constraint.postponedInstruction != null
					|| constraint.restriction.isConstant
					|| constraint.definitions.any { reg ->
						reg.definitions().isNotEmpty()
					}
					|| constraint.members
						.any(L2SemanticValue<*>::isConstant)
			}
			.flatMapTo(mutableSetOf(), Constraint<*>::members)
			.toSet()

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym] that's
	 * bound to it.  Answer `null` if it's not found.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @return
	 *   The [L2Synonym] bound to that semantic value, or `null`.
	 */
	fun <K: RegisterKind<K>> semanticValueToSynonymOrNull(
		semanticValue: L2SemanticValue<K>
	): L2Synonym<K>? = stateOrNull(semanticValue)?.synonym?.cast()

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
		while (link !== target)
		{
			val next = forward[link]!!
			forward[link] = target
			link = next
		}
		return target
	}

	/**
	 * Answer the live [ValueClass] of the given [L2SemanticValue], or `null` if
	 * this manifest doesn't know the value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [ValueClass], or `null`.
	 */
	private fun classOrNull(
		semanticValue: L2SemanticValue<*>
	): ValueClass? = classOf!![semanticValue]?.let(::resolve)

	/**
	 * Answer the live [ValueClass] of the given [L2SemanticValue].  Fail if the
	 * value is unknown to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [ValueClass].
	 */
	private fun classFor(
		semanticValue: L2SemanticValue<*>
	): ValueClass = classOrNull(semanticValue)!!

	/**
	 * Answer the [Constraint] describing the given [L2SemanticValue], or `null`
	 * if this manifest doesn't know the value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to look up.
	 * @return
	 *   Its [Constraint], or `null`.
	 */
	private fun stateOrNull(
		semanticValue: L2SemanticValue<*>
	): Constraint<*>? = classOrNull(semanticValue)?.let(states::get)

	/**
	 * Bind the given [L2SemanticValue]s to the given [ValueClass], replacing
	 * any prior binding.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to bind.
	 * @param valueClass
	 *   The [ValueClass] to bind them to.
	 */
	private fun bind(
		semanticValues: Iterable<L2SemanticValue<*>>,
		valueClass: ValueClass)
	{
		semanticValues.forEach { semanticValue ->
			classOf!![semanticValue] = valueClass
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
		semanticValue: L2SemanticValue<*>,
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
		base: L2SemanticValue<*>,
		derivedClass: ValueClass
	) = recordDerivation(tagOf, base, derivedClass)

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
		base: L2SemanticValue<*>,
		derivedClass: ValueClass
	) = recordDerivation(variantIdOf, base, derivedClass)

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
	 * The base is introduced with its default restriction and no definition –
	 * an anchor, not a value anyone will read.  Narrowing propagates into it
	 * from the derived value in the usual way.
	 *
	 * @param edges
	 *   Either [tagOf] or [variantIdOf].
	 * @param base
	 *   The [L2SemanticValue] the derived class describes.
	 * @param derivedClass
	 *   The derived [ValueClass].
	 */
	private fun recordDerivation(
		edges: MutableMap<ValueClass, ValueClass>,
		base: L2SemanticValue<*>,
		derivedClass: ValueClass)
	{
		if (!caresAboutSemanticValues) return
		val baseClass = classOrNull(base)
			?: run {
				introduceSynonym<BOXED_KIND>(
					setOf(base), base.defaultRestriction)
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
		if (winner === loser) return
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
		if (winner === loser) return
		val winnerState = states[winner] ?: return
		val loserState = states[loser] ?: return
		// This recurses back through forwardClass if the merged classes have
		// derived values of their own, which terminates because every merge
		// strictly reduces the number of classes.
		dynamicAgglomerateSynonym(
			winnerState.members + loserState.members,
			winnerState.restriction.intersection(loserState.restriction))
	}

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym] that's
	 * bound to it.  Fail if it's not found.
	 *
	 * @param semanticValue
	 *   The semantic value to look up.
	 * @return
	 *   The [L2Synonym] bound to that semantic value.
	 */
	fun <K: RegisterKind<K>> semanticValueToSynonym(
		semanticValue: L2SemanticValue<K>
	): L2Synonym<K> = semanticValueToSynonymOrNull(semanticValue)!!

	/**
	 * Look up the given [L2SemanticValue], answering the [L2Synonym] that's
	 * bound to it.  If not found, evaluate the lambda to produce an
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
		semanticValue: L2SemanticValue<K>,
		elseSupplier: ()->L2Synonym<K>
	): L2Synonym<K> =
		semanticValueToSynonymOrNull(semanticValue) ?: elseSupplier()

	/**
	 * Capture information about a new [L2Synonym] and its [TypeRestriction].
	 * It's an error if any of the provided [L2SemanticValue]s are already bound
	 * to other synonyms in this manifest.
	 *
	 * @param semanticValues
	 *   The new [L2SemanticValue]s to place in the new synonym.
	 * @param restriction
	 *   The [TypeRestriction] to constrain the new synonym.
	 */
	fun <K: RegisterKind<K>> introduceSynonym(
		semanticValues: Iterable<L2SemanticValue<*>>,
		restriction: TypeRestriction)
	{
		assert(semanticValues.none(::hasSemanticValue))

		val pick = semanticValues.first()
		val freshSynonym = L2Synonym(
			semanticValues.toSet().cast<Iterable<*>, Set<L2SemanticValue<K>>>())
		val freshClass = ValueClass.newValueClass()
		bind(semanticValues, freshClass)
		states[freshClass] =
			Constraint(
				freshSynonym.semanticValues(),
				emptyList(),
				pick.defaultRestriction,
				null)
		updateRestriction(pick) { restriction }
	}

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether this semantic value is known to this manifest, due to a
	 *   previous instruction that wrote it.
	 */
	fun hasSemanticValue(semanticValue: L2SemanticValue<*>): Boolean =
		classOf!!.containsKey(semanticValue)

	/**
	 * Answer whether the [L2SemanticValue] is known to this manifest AND the
	 * instructions that compute it have been emitted.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue].
	 * @return
	 *   Whether there is a register known to be holding this value, whether
	 *   it's already written by a previous instruction or it would be written
	 *   by a postponed instruction.
	 */
	fun hasLiveSemanticValue(
		semanticValue: L2SemanticValue<*>
	): Boolean = stateOrNull(semanticValue).notNullAnd {
		definitions.any { reg ->
			reg.definitions().any { semanticValue in it.semanticValues() }
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
		semanticValue: L2SemanticValue<K>
	): L2SemanticValue<K>?
	{
		if (hasSemanticValue(semanticValue))
		{
			// It already exists in exactly the form given, which is the vast
			// majority of cases.
			return semanticValue
		}
		// Try a slower, far less frequent search.
		val onlyClass = classRestrictingSearchFor(semanticValue)
		return classOf!!.keys.firstOrNull { other ->
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
		semanticValue: L2SemanticValue<*>
	): Class<out L2SemanticValue<*>>? = when
	{
		semanticValue is L2SemanticConstant -> null
		hasSemanticValue(semanticValue) -> null
		else -> semanticValue.javaClass
	}

	/**
	 * If the value denoted by the given boxed [L2SemanticValue] is also
	 * available in unboxed int form, answer the [L2SemanticValue] that names
	 * that form, otherwise answer `null`.
	 *
	 * Note that an [L2SemanticUnboxedInt] is constructed from one arbitrary
	 * representative of the boxed [L2Synonym], so the int form of a value is
	 * *not* generally findable by a direct map lookup – it has to be searched
	 * for.  Callers should prefer this operation over constructing an
	 * [L2SemanticUnboxedInt] and searching for it themselves, both because the
	 * search is subtle and because this is the operation that a later redesign
	 * will turn into a simple field access.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose int form is sought.
	 * @return
	 *   The equivalent unboxed int [L2SemanticValue], or `null`.
	 */
	fun intFormOf(
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<INTEGER_KIND>? =
		equivalentSemanticValue(boxed.unboxedInt)

	/**
	 * As [intFormOf], but only answer an int form that is already bound to a
	 * register by a visible defining write.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose int form is sought.
	 * @return
	 *   The equivalent populated unboxed int [L2SemanticValue], or `null`.
	 */
	fun populatedIntFormOf(
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<INTEGER_KIND>? =
		equivalentPopulatedSemanticValue(boxed.unboxedInt)

	/**
	 * If the value denoted by the given boxed [L2SemanticValue] is also
	 * available in unboxed float form, answer the [L2SemanticValue] that names
	 * that form, otherwise answer `null`.  See [intFormOf] for why this is a
	 * search rather than a lookup.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose float form is sought.
	 * @return
	 *   The equivalent unboxed float [L2SemanticValue], or `null`.
	 */
	fun floatFormOf(
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<FLOAT_KIND>? =
		equivalentSemanticValue(boxed.unboxedFloat)

	/**
	 * If the value denoted by the given unboxed int [L2SemanticValue] is also
	 * available in boxed form, answer the [L2SemanticValue] that names that
	 * form, otherwise answer `null`.  See [intFormOf] for why this is a search
	 * rather than a lookup.
	 *
	 * @param unboxedInt
	 *   The unboxed int [L2SemanticValue] whose boxed form is sought.
	 * @return
	 *   The equivalent boxed [L2SemanticValue], or `null`.
	 */
	fun boxedFormOfInt(
		unboxedInt: L2SemanticValue<INTEGER_KIND>
	): L2SemanticValue<BOXED_KIND>? =
		equivalentSemanticValue(unboxedInt.boxed)

	/**
	 * If the value denoted by the given unboxed float [L2SemanticValue] is also
	 * available in boxed form, answer the [L2SemanticValue] that names that
	 * form, otherwise answer `null`.  See [intFormOf] for why this is a search
	 * rather than a lookup.
	 *
	 * @param unboxedFloat
	 *   The unboxed float [L2SemanticValue] whose boxed form is sought.
	 * @return
	 *   The equivalent boxed [L2SemanticValue], or `null`.
	 */
	fun boxedFormOfFloat(
		unboxedFloat: L2SemanticValue<FLOAT_KIND>
	): L2SemanticValue<BOXED_KIND>? =
		equivalentSemanticValue(unboxedFloat.boxed)

	/**
	 * Answer the [L2SemanticValue] naming the [TypeTag] extracted from the
	 * given boxed [L2SemanticValue], if that tag is available in this manifest
	 * as an unboxed int, otherwise answer `null`.  See [intFormOf] for why this
	 * is a search rather than a lookup.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose extracted tag is sought.
	 * @return
	 *   The equivalent unboxed int [L2SemanticValue] holding the tag, or
	 *   `null`.
	 */
	fun tagFormOf(
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<INTEGER_KIND>? =
		derivedFormOf(tagOf, boxed)
			?: equivalentSemanticValue(L2SemanticExtractedTag(boxed).unboxedInt)

	/**
	 * Answer a member of the [ValueClass] reached from the given base by the
	 * given derivation edges, or `null` if there is no such edge or the class it
	 * points at has been forgotten.
	 *
	 * This replaces a search with an edge traversal.  The search it replaces is
	 * subtly weak: it looks for whichever spelling of the derived value happens
	 * to be present, so it misses when the base's synonym has since been merged
	 * and the derived value is spelled in terms of a different member.
	 *
	 * @param edges
	 *   Either [tagOf] or [variantIdOf].
	 * @param boxed
	 *   The base [L2SemanticValue].
	 * @return
	 *   A member of the derived [ValueClass], or `null`.
	 */
	private fun derivedFormOf(
		edges: Map<ValueClass, ValueClass>,
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<INTEGER_KIND>?
	{
		val baseClass = classOrNull(boxed) ?: return null
		val derivedClass = edges[baseClass]?.let(::resolve) ?: return null
		val state = states[derivedClass] ?: return null
		return state.synonym.pickSemanticValue().cast()
	}

	/**
	 * Answer the [L2SemanticValue] naming the [ObjectLayoutVariant] id
	 * extracted from the given boxed [L2SemanticValue], if that id is available
	 * in this manifest as an unboxed int, otherwise answer `null`.  See
	 * [intFormOf] for why this is a search rather than a lookup.
	 *
	 * @param boxed
	 *   The boxed [L2SemanticValue] whose extracted variant id is sought.
	 * @return
	 *   The equivalent unboxed int [L2SemanticValue] holding the variant id, or
	 *   `null`.
	 */
	fun variantIdFormOf(
		boxed: L2SemanticValue<BOXED_KIND>
	): L2SemanticValue<INTEGER_KIND>? =
		derivedFormOf(variantIdOf, boxed)
			?: equivalentSemanticValue(
				L2SemanticObjectVariantId(boxed).unboxedInt)

	/**
	 * Given an [L2SemanticValue], see if there's already an equivalent one in
	 * this manifest, but appearing in a definition (i.e., already bound to a
	 * register).  If an [L2SemanticPrimitiveInvocation] is supplied, look
	 * for a recursively synonymous one (that's bound to a register).
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
	 *   given one, and appearing in a defining write, or `null` if no such
	 *   value is in the manifest.
	 */
	fun <K: RegisterKind<K>> equivalentPopulatedSemanticValue(
		semanticValue: L2SemanticValue<K>
	): L2SemanticValue<K>?
	{
		if (isPopulated(semanticValue)) return semanticValue
		// Try a slower, far less frequent search.  Note that the probe may be
		// present in the manifest but unpopulated, in which case the search
		// cannot be narrowed by class, since the shared-synonym test can then
		// match a candidate of some other class.
		val onlyClass = classRestrictingSearchFor(semanticValue)
		return classOf!!.keys.firstOrNull { other ->
			(onlyClass === null || other.javaClass === onlyClass)
				&& isEquivalentSemanticValue(semanticValue, other)
				&& isPopulated(other)
		}.cast()
	}

	/**
	 * Answer whether the given [L2SemanticValue] is populated by having a
	 * visible defining write that wrote to that exact semantic value.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to test.
	 * @return
	 *   Whether that semantic value has a visible write to it.
	 */
	fun isPopulated(
		semanticValue: L2SemanticValue<*>
	): Boolean = (hasSemanticValue(semanticValue)
		&& getDefinitions(semanticValue).any { register ->
			register.definitions().any { write ->
				semanticValue in write.semanticValues()
			}
		})

	/**
	 * Given two [L2SemanticValue]s, see if they represent the same value in
	 * this manifest.  Include checking covariant homomorphisms between
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
		semanticValue: L2SemanticValue<*>,
		otherSemanticValue: L2SemanticValue<*>
	): Boolean
	{
		if (semanticValue == otherSemanticValue) return true
		if (semanticValue.kind != otherSemanticValue.kind) return false
		val ownClass = classOrNull(semanticValue)
		if (ownClass !== null && ownClass === classOrNull(otherSemanticValue))
		{
			// They're already synonyms of each other.
			return true
		}
		when
		{
			semanticValue is L2SemanticUnboxedInt &&
				otherSemanticValue is L2SemanticUnboxedInt ->
			{
				return isEquivalentSemanticValue(
					semanticValue.boxed, otherSemanticValue.boxed)
			}
			semanticValue is L2SemanticUnboxedFloat &&
				otherSemanticValue is L2SemanticUnboxedFloat ->
			{
				return isEquivalentSemanticValue(
					semanticValue.boxed, otherSemanticValue.boxed)
			}
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
	 * Ensure all the given [L2SemanticValue]s are placed in the same synonym if
	 * they're not already.  Merge any existing synonyms that include any of the
	 * mentioned semantic values.
	 *
	 * Additionally, ensure the restriction for the new synonym is built from
	 * the intersection of the existing restrictions, if any, otherwise using
	 * the [topRestriction].
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to ensure are in the same synonym.
	 * @param baseRestriction
	 *   The [TypeRestriction] to use for the new synonym, if it needs to be
	 *   created.  It may be further strengthened by the restrictions present
	 *   for existing synonyms.
	 */
	fun <K: RegisterKind<K>> agglomerateSynonym(
		semanticValues: Iterable<L2SemanticValue<K>>,
		baseRestriction: TypeRestriction)
	{
		val constant = baseRestriction.constantOrNull
		if (constant != null && semanticValues.none { it.isConstant })
		{
			// Recurse, but with the semantic constant present.
			agglomerateSynonym(
				semanticValues +
					semanticValues.first().kind
						.createSemanticConstant(constant),
				baseRestriction)
			return
		}
		val existingClasses = mutableSetOf<ValueClass>()
		val strandedValues = mutableSetOf<L2SemanticValue<K>>()
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
			strandedValues.firstOrNull(L2SemanticValue<K>::isConstant)
		val newRestriction = existingSemanticConstant?.constantRestrictionOrNull
			?: when
			{
				existingClasses.isEmpty() -> baseRestriction
				else -> existingClasses
					.map { states[it]!!.restriction }
					.reduce(TypeRestriction::intersection)
			}

		val definitions = mutableListOf<L2Register<K>>()
		val postponedInstructions = mutableListOf<L2Instruction>()
		val allSemanticValues: Set<L2SemanticValue<K>> = existingClasses
			.flatMapTo(mutableSetOf()) {
				states[it]!!.members.cast<Set<*>, Set<L2SemanticValue<K>>>()
			}
			.plus(strandedValues)
		existingClasses.forEach { existingClass ->
			val constraint = states[existingClass]!!
			definitions.addAll(constraint.definitions.cast())
			constraint.postponedInstruction?.let(postponedInstructions::add)
		}

		// Reuse any of tho existing postponed instructions, since they all will
		// populate the entire synonym.
		val defined = definitions
			.flatMap(L2Register<K>::definitions)
			.flatMap(L2WriteOperand<K>::semanticValues)
			.intersect(allSemanticValues)
		val notDefined = allSemanticValues - defined
		val kind = allSemanticValues.first().kind
		val postponedInstruction = when
		{
			// Don't generate postponed instructions when the graph is held
			// together by registers instead of semantic values.
			!caresAboutSemanticValues -> null
			// If the value is defined for all, no instruction is needed.
			notDefined.isEmpty() -> null
			// If the new restriction is impossible, output an impossibleCode
			// instruction, which should hopefully cause this path to become
			// unreachable from the nearest branch.
			newRestriction.isImpossible -> null
			// If the value is defined for some and notDefined for others,
			// produce a move.
			defined.isNotEmpty() -> kind.dynamicMove(
				defined.first(), emptySet(), this, newRestriction)
			// The value is defined for none.  Check for a constant restriction.
			newRestriction.isConstant -> kind.moveConstant(
				newRestriction.constantOrNull!!, emptySet())
			// Defined for none, and not constant.  Keep (any) one of the
			// (definitely non-move, non-constant-move) postponed instructions
			// found in the prior synonyms.  Allow there to have been no
			// postponed instruction *just* to simplify intermediate states,
			// where the synonym is built before the defining instruction is
			// added.
			else -> postponedInstructions.firstOrNull()
		}
		// Wire it in.  One of the existing classes survives and absorbs the
		// others, so that anything still referring to a merged-away class
		// resolves to the survivor.
		assert(caresAboutSemanticValues || postponedInstruction == null)
		val winner = existingClasses.firstOrNull() ?: ValueClass.newValueClass()
		existingClasses.forEach { loser -> forwardClass(winner, loser) }
		states[winner] =
			Constraint(
				allSemanticValues,
				definitions,
				newRestriction,
				postponedInstruction)
		bind(allSemanticValues, winner)
	}

	/**
	 * Ensure all the given [L2SemanticValue]s are placed in the same synonym if
	 * they're not already.  Merge any existing synonyms that include any of the
	 * mentioned semantic values.  This is the version that accepts semantic
	 * values whose [RegisterKind] is not known statically.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to ensure are in the same synonym.
	 * @param baseRestriction
	 *   The [TypeRestriction] to use for the new synonym, if it needs to be
	 *   created.  It may be further strengthened by the restrictions present
	 *   for existing synonyms.
	 */
	fun dynamicAgglomerateSynonym(
		semanticValues: Iterable<L2SemanticValue<*>>,
		baseRestriction: TypeRestriction)
	{
		// Note that we *have* to have an argument dependent on K *before* any
		// value that we need to cast().  There are a number of Kotlin type
		// shortcomings that are bypassed here, most notably the inability of
		// the calling code to introduce fresh genericity somewhere other than
		// at a method boundary.
		fun <K: RegisterKind<K>> innerFun(
			@Suppress("unused") kind: K,
			semanticValues: Iterable<L2SemanticValue<K>>,
			baseRestriction: TypeRestriction)
		{
			agglomerateSynonym(semanticValues, baseRestriction)
		}
		// Take special note that we can't put the kind into a temp and use it
		// that way without also casting it, because Kotlin's internal captured
		// type representation on a variable is insufficient... even though it
		// works for typing subexpressions.
		innerFun(
			semanticValues.first().kind,
			semanticValues.cast(),
			baseRestriction)
	}

	/**
	* Merge a new [L2SemanticValue] into an existing [L2Synonym]. Update the
	* manifest to reflect the merge. This bypasses a shortcoming in Kotlin's
	* type erasure algorithm at some call sites, instead relying on a dynamic
	* check of their [RegisterKind].
	*
	* Note that because the [L2SemanticValue] is new, we don't have to check
	* for existing [L2SemanticPrimitiveInvocation]s becoming synonyms of each
	* other, which is much faster than the general case in
	* [mergeExistingSemanticValues].
	*
	* @param existingSynonym
	*   An [L2Synonym].
	* @param semanticValue
	*   Another [L2SemanticValue] representing the same value.
	*/
	fun <K: RegisterKind<K>> dynamicExtendSynonym(
		existingSynonym: L2Synonym<K>,
		semanticValue: L2SemanticValue<*>)
	{
		assert(existingSynonym.kind == semanticValue.kind)
		extendSynonym(existingSynonym, semanticValue.cast())
	}

	/**
	 * Merge a new [L2SemanticValue] into an existing [L2Synonym]. Update the
	 * manifest to reflect the merge.
	 *
	 * Note that because the [L2SemanticValue] is new, we don't have to check
	 * for existing [L2SemanticPrimitiveInvocation]s becoming synonyms of each
	 * other, which is much faster than the general case in
	 * [mergeExistingSemanticValues].
	 *
	 * @param existingSynonym
	 *   An [L2Synonym].
	 * @param semanticValue
	 *   Another [L2SemanticValue] representing the same value.
	 */
	fun <K: RegisterKind<K>> extendSynonym(
		existingSynonym: L2Synonym<K>,
		semanticValue: L2SemanticValue<K>)
	{
		assert(!hasSemanticValue(semanticValue))
		val semanticValues = existingSynonym.semanticValues().toMutableSet()
		semanticValues.add(semanticValue)
		// The class keeps its identity; only its state is replaced, since the
		// constraint records its own membership.
		val valueClass = classFor(existingSynonym.pickSemanticValue())
		val existingConstraint = states[valueClass]!!
		states[valueClass] = Constraint(
			semanticValues,
			existingConstraint.definitions.cast(),
			existingConstraint.restriction,
			existingConstraint.postponedInstruction)
		bind(semanticValues, valueClass)
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 * This bypasses a shortcoming in Kotlin's type erasure algorithm at some
	 * call sites, instead relying on a dynamic check of their [RegisterKind].
	 *
	 * @param semanticValue1
	 *   An [L2SemanticValue].
	 * @param semanticValue2
	 *   Another [L2SemanticValue] representing what has just been shown to be
	 *   the same value.  It may already be a synonym of the first semantic
	 *   value.
	 */
	fun <K: RegisterKind<K>> dynamicMergeExistingSemanticValues(
		semanticValue1: L2SemanticValue<K>,
		semanticValue2: L2SemanticValue<*>)
	{
		assert(semanticValue1.kind == semanticValue2.kind)
		mergeExistingSemanticValues(semanticValue1, semanticValue2.cast())
	}

	/**
	 * Given two [L2SemanticValue]s, merge their [L2Synonym]s together, if
	 * they're not already.  Update the manifest to reflect the merged synonyms.
	 *
	 * @param semanticValue1
	 *   An [L2SemanticValue].
	 * @param semanticValue2
	 *   Another [L2SemanticValue] representing what has just been shown to be
	 *   the same value.  It may already be a synonym of the first semantic
	 *   value.
	 */
	fun <K: RegisterKind<K>> mergeExistingSemanticValues(
		semanticValue1: L2SemanticValue<K>,
		semanticValue2: L2SemanticValue<K>)
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
		val allSemanticPrimitives = classOf!!.keys
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
					L2SemanticValue<BOXED_KIND>,
					L2SemanticValue<BOXED_KIND>>>()
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
					List<L2Synonym<BOXED_KIND>?>,
					MutableSet<L2Synonym<BOXED_KIND>>>()
				for (invocation in invocations)
				{
					// Note that sometimes an L2SemanticPrimitiveInvocation will
					// be in the manifest, even though some of its argument
					// semantic values are no longer accessible.  Create a
					// singleton synonym for such a semantic value, but don't
					// register it in the manifest.
					val argumentSynonyms: List<L2Synonym<BOXED_KIND>?> =
						invocation.argumentSemanticValues
							.map {
								semanticValueToSynonymOrElse(it) {
									L2Synonym(setOf(it))
								}
							}
					val primitiveSynonyms =
						map.computeIfAbsent(argumentSynonyms) { mutableSetOf() }
					val invocationSynonym: L2Synonym<BOXED_KIND> =
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
	private fun <K: RegisterKind<K>> privateMergeSynonyms(
		synonym1: L2Synonym<K>,
		synonym2: L2Synonym<K>
	): Boolean
	{
		if (synonym1 == synonym2) return false
		val class1 = classFor(synonym1.pickSemanticValue())
		val class2 = classFor(synonym2.pickSemanticValue())
		if (class1 === class2) return false
		val constraint1: Constraint<K> = states[class1].cast()
		val constraint2: Constraint<K> = states[class2].cast()
		val semanticValues =
			synonym1.semanticValues() + synonym2.semanticValues()
		val restriction =
			constraint1.restriction.intersection(constraint2.restriction)
		// class1 survives and absorbs class2, so anything still holding class2
		// resolves to class1.
		forwardClass(class1, class2)
		val definitions = constraint1.definitions + constraint2.definitions
		val postponed1 = constraint1.postponedInstruction
		val postponed2 = constraint2.postponedInstruction
		// In theory, if both postponed instructions are present we could decide
		// which to keep and augment with the other synonym, but for now we can
		// just choose arbitrarily, since they yield equivalent values.
		val newPostponed = (postponed1 ?: postponed2)?.let { instruction ->
			when {
				// There's already a definition, so drop the instruction.
				definitions.isNotEmpty() -> null
				// It's a constant, so drop the instruction.
				semanticValues.any(L2SemanticValue<*>::isConstant) -> null
				else -> instruction
			}
		}
		// Just concatenate the input synonyms' lists, as this essentially
		// preserves earliest definition order.
		assert(caresAboutSemanticValues || newPostponed == null)
		val newConstraint = Constraint(
			semanticValues,
			definitions,
			restriction,
			newPostponed)
		states[class1] = newConstraint
		bind(semanticValues, class1)
		if (constraint1.isImpossible) impossibleRestrictionCount--
		if (constraint2.isImpossible) impossibleRestrictionCount--
		if (newConstraint.isImpossible) impossibleRestrictionCount++
		if (restriction.isConstant
			&& semanticValues.none(L2SemanticValue<*>::isConstant))
		{
			// The merged restriction is a constant, but we don't have that
			// semantic constant within the synonym yet.  The two cases are if
			// there's another synonym with that semantic constant and if there
			// isn't.
			val semanticConstant = synonym1.kind
				.createSemanticConstant(restriction.constantOrNull!!)
			if (hasSemanticValue(semanticConstant))
			{
				// Another synonym is also constrained to that constant.  Do
				// another synonym merge, technically recursively, although the
				// maximum recursion depth is 2.  Note that we don't care about
				// the boolean return value, since we must answer true from the
				// outer call.
				privateMergeSynonyms(
					states[class1]!!.synonym.cast(),
					semanticValueToSynonym(semanticConstant))
			}
			else
			{
				// The semantic constant is not in any synonym yet, but it needs
				// to be added to the new synonym.
				extendSynonym(states[class1]!!.synonym.cast(), semanticConstant)
			}
		}
		return true
	}

	/**
	 * Given two semantic values of the same [RegisterKind], check if there is
	 * an equivalent semantic value for each in this manifest, and if so, merge
	 * their synonyms.  Otherwise do nothing.
	 *
	 * Only merge them if they're both populated.
	 */
	fun <K: RegisterKind<K>> mergeSemanticValueEquivalentsIfPresent(
		value1: L2SemanticValue<K>,
		value2: L2SemanticValue<K>)
	{
		equivalentPopulatedSemanticValue(value1)?.let { equivalent1 ->
			equivalentPopulatedSemanticValue(value2)?.let { equivalent2 ->
				mergeExistingSemanticValues(equivalent1, equivalent2)
			}
		}
	}

	/**
	 * Retrieve the oldest definition of the given [L2SemanticValue] or an
	 * equivalent, but having the given [RegisterKind].  Only consider registers
	 * whose definitions *all* include that semantic value.  This should work
	 * well in SSA or non-SSA, but not after register coloring. If no such
	 * register is found, return `null`.
	 *
	 * @param K
	 *   The [RegisterKind] of the desired register.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   The requested [L2Register] or `null`.
	 */
	fun <K: RegisterKind<K>> getDefinitionOrNull(
		semanticValue: L2SemanticValue<K>
	): L2Register<K>?
	{
		if (!hasLiveSemanticValue(semanticValue))
		{
			// Postponed instructions don't have registers assigned.
			return null
		}
		val constraint = constraint(semanticValue)
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
		semanticValue: L2SemanticValue<K>
	): L2Register<K> = getDefinitionOrNull(semanticValue)!!

	/**
	 * Retrieve all [L2Register]s known to contain the given [L2SemanticValue].
	 * If the mode is still [BySemanticValue], narrow it to just those registers
	 * whose definitions *all* include that semantic value.
	 *
	 * @param <R>
	 *   The kind of [L2Register] to return.
	 * @param semanticValue
	 *   The [L2SemanticValue] being examined.
	 * @return
	 *   A [List] of the requested [L2Register]s.
	 */
	fun <K: RegisterKind<K>> getDefinitions(
		semanticValue: L2SemanticValue<K>
	): List<L2Register<K>> = when (mode)
	{
		BySemanticValue ->
			constraint(semanticValue).definitions.filter { reg ->
				reg.definitions().all { write ->
					semanticValue in write.semanticValues()
				}
			}
		else -> constraint(semanticValue).definitions
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
		semanticValue: L2SemanticValue<K>
	): List<L2Register<K>> = constraint(semanticValue).definitions


	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest.  Note that this
	 * also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param newRestriction
	 *   The [TypeRestriction] to bound the synonym.
	 */
	fun setRestriction(
		semanticValue: L2SemanticValue<*>,
		newRestriction: TypeRestriction)
	{
		updateRestriction(semanticValue) { newRestriction }
	}

	/**
	 * Replace the [TypeRestriction] associated with the given
	 * [L2SemanticValue], which must be known by this manifest, with the
	 * intersection of its current restriction and the given restriction. Note
	 * that this also restricts any synonymous semantic values.
	 *
	 * @param semanticValue
	 *   The given [L2SemanticValue].
	 * @param type
	 *   The [A_Type] to intersect with the existing restriction.
	 */
	fun intersectType(semanticValue: L2SemanticValue<*>, type: A_Type)
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
	fun subtractType(semanticValue: L2SemanticValue<*>, type: A_Type)
	{
		updateRestriction(semanticValue) { minusType(type) }
	}

	fun restrictionFor(read: L2ReadOperand<*>): TypeRestriction = when
	{
		// Simplify things for the caller.
		classOf == null -> read.restriction()
		!caresAboutSemanticValues -> read.restriction()
		else -> restrictionFor(read.semanticValue())
			.intersection(read.restriction())
	}

	fun restrictionFor(write: L2WriteOperand<*>): TypeRestriction = when
	{
		// Simplify things for the caller.
		classOf == null -> write.restriction()
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
	fun <K: RegisterKind<K>> restrictionFor (
		semanticValue: L2SemanticValue<K>
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
		stateOrNull(equivalent)?.let { return it.restriction }
		postponedInstructionFor(equivalent)?.let { instruction ->
			return instruction
				.writeOperands
				.first { semanticValue in it.semanticValues() }
				.restriction()
		}
		// The semantic value wasn't found directly in a synonym.  If it's
		// unboxed, see if we can synthesize an answer from the boxed form.
		return when (equivalent)
		{
			is L2SemanticUnboxedInt ->
				restrictionFor(equivalent.boxed).forUnboxedInt()
			is L2SemanticUnboxedFloat ->
				restrictionFor(equivalent.boxed).forUnboxedFloat()
			else -> throw AssertionError(
				"Cannot find restriction for: $semanticValue")
		}
	}

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue], or null if it doesn't exist.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym, or `null`.
	 */
	private fun <K: RegisterKind<K>> constraintOrNull(
		semanticValue: L2SemanticValue<K>
	): Constraint<K>?
	{
		val equivalent = equivalentSemanticValue(semanticValue)!!
		return stateOrNull(equivalent)?.cast()
	}

	/**
	 * Answer the [Constraint] associated with the [L2Synonym] containing the
	 * given [L2SemanticValue].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] whose synonym is to be looked up.
	 * @return
	 *   The [Constraint] associated with the synonym.
	 */
	private fun <K: RegisterKind<K>> constraint(
		semanticValue: L2SemanticValue<K>
	): Constraint<K> = constraintOrNull(semanticValue)!!

	/**
	 * Answer an arbitrarily ordered array of the [L2Synonym]s in this manifest.
	 *
	 * @return
	 *   An array of [L2Synonym]s.
	 */
	fun synonymsArray(): Array<L2Synonym<*>> =
		states.values.map(Constraint<*>::synonym).toTypedArray()

	/**
	 * Answer a [Set] of all [L2SemanticValue]s in this manifest.  This is only
	 * exposed to make sanity checking easier.
	 */
	val allSemanticValuesForChecking: Set<L2SemanticValue<*>> get() =
		synonymsArray().flatMapTo(mutableSetOf()) { it.semanticValues() }

	/**
	 * Anwser a [Set] of all [L2Register]s present in this manifets.  This is
	 * only exposed to make sanity checking easier.
	 */
	val allRegistersForChecking: Set<L2Register<*>> get() =
		// Aggregate: every register in the manifest, of every kind.
		states.values.flatMapTo(mutableSetOf(), Constraint<*>::allDefinitions)


	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	fun clear()
	{
		classOf?.clear()
		forward.clear()
		states.clear()
		impossibleRestrictionCount = 0
		clearPostponedInstructions()
	}

	/**
	 * Record the fact that an [L2Instruction] has been emitted, which writes to
	 * the given [L2WriteOperand].  Since this is the introduction of a new
	 * [L2SemanticValue], it must not yet be in this manifest.
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
	 * the given [L2WriteOperand].  Since this is the introduction of a new
	 * [L2SemanticValue], it must not yet be in this manifest.
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
			semanticValues += writer.kind.createSemanticConstant(constant)
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
		updateDefinitions(semanticValues.first()) {
			// After register coloring, a regeneration might need to add the
			// same register to the manifest multiple times.
			if (writer.register() !in this) append(writer.register())
			else this
		}
		removePostponedInstructionFor(semanticValues.first())
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
		sourceSemanticValue: L2SemanticValue<K>)
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
		updateDefinitions(sourceSemanticValue) {
			when
			{
				contains(register) -> this
				else -> append(register)
			}
		}
		removePostponedInstructionFor(writer.pickSemanticValue())
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
	): Set<L2Synonym<K>> = synonymsForRegisters(setOf(register))

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
	): Set<L2Synonym<K>> =
		states.values
			.filter { it.definitions.any { def -> def in registers } }
			.mapToSet { it.synonym }
			.cast()  // strengthen from * to K

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
		semanticValue: L2SemanticValue<K>
	): L2ReadOperand<K> = semanticValue.kind
		.readOperand(semanticValue, restrictionFor(semanticValue))

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
		assert(classOf!!.isEmpty())
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
				classOf.putAll(soleManifest.classOf!!)
				forward.putAll(soleManifest.forward)
				states.putAll(soleManifest.states)
				impossibleRestrictionCount =
					soleManifest.impossibleRestrictionCount
				return
			}
		}
		if (generator.mode == BySemanticValue)
		{
			// 1. Compute live semantic values (intersection across all edges).
			var liveSemanticValues = manifests
				.map(L2ValueManifest::liveOrPostponedSemanticValues)
				.reduce(Set<L2SemanticValue<*>>::intersect)

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
					.reduce(Set<L2SemanticValue<*>>::intersect)
			}

			// 3. Build phiMap - chop synonyms into maximal consistent groups.
			// The map is from a list of synonyms, one per manifest, to the
			// semantic values that were present in all the synonyms.
			val phiMap = mutableMapOf<
				List<L2Synonym<*>>,
				MutableList<L2SemanticValue<*>>>()
			liveSemanticValues.forEach { sv ->
				if (manifests.all { it.hasSemanticValue(sv) })
				{
					val key = manifests.map { it.semanticValueToSynonym(sv) }
					phiMap.getOrPut(key, ::mutableListOf).add(sv)
				}
			}

			// 4. Create and populate each output synonym group.
			phiMap.values.forEach { relatedSemanticValues ->
				relatedSemanticValues[0].kind.populateOneSynonym(
					relatedSemanticValues.cast(),
					manifests,
					generator,
					forcePhis)
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
						MutableSet<L2SemanticValue<*>>,
						Mutable<TypeRestriction>
					>
				>()
				manifest.states.values.forEach { constraint ->
					constraint.definitions.forEach { register ->
						val pair = registerMap[register]
						when (pair)
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
								pair.first.addAll(constraint.members)
								pair.second.update {
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
				.reduce(Set<L2SemanticValue<*>>::intersect)
			val registerlessGroups = mutableMapOf<
				List<L2Synonym<*>>,
				MutableList<L2SemanticValue<*>>>()
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
				val firstValue = values.first()
				firstValue.kind.run {
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
								dynamicExtendSynonym(
									semanticValueToSynonym(equivalentAnchor),
									sv.cast())
							}
						}
					}
					else
					{
						// None are in the manifest, and no equivalent was
						// found.  Create a new synonym.
						val restriction = manifests
							.map { it.restrictionFor(firstValue) }
							.reduce(TypeRestriction::union)
						introduceSynonym(values, restriction)
					}
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
	 * @receiver
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
	private fun <K: RegisterKind<K>> K.populateOneSynonym(
		relatedSemanticValues: List<L2SemanticValue<K>>,
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
			else -> relatedSemanticValues +
				firstSemanticValue.kind.createSemanticConstant(constant)
		}

		// Ensure the related semantic values are in the same synonym, and
		// suitably restricted.
		agglomerateSynonym(semanticValuesToInclude, restriction)
		postponedInstructionFor(firstSemanticValue)?.let {
			// There's already a postponed instruction in the manifest, which
			// will automatically populate the mhole synonym.
			return
		}

		// Find any registers that are defined for the same semantic value in
		// all incoming manifests.
		val commonRegisters = relatedSemanticValues
			.map { value ->
				manifests
					.map { m -> m.getDefinitions(value).toSet() }
					.reduce(Set<L2Register<K>>::intersect)
			}
			.flatten()
			.toSet()
		// Make those common registers available at the merge.
		if (commonRegisters.isNotEmpty() && !forcePhis)
		{
			updateDefinitions(firstSemanticValue) {
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
					manifests.all { it.hasLiveSemanticValue(sv) }
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
						edge, listOf(firstSemanticValue))
					assert(currentBlock() == mergeBlock)
				}
				assert(manifests.all {
					it.hasLiveSemanticValue(firstSemanticValue)
				})
				liveValues = listOf(firstSemanticValue)
			}
			assert(liveValues.isNotEmpty())

			// Now merge the disparate registers for one of the liveValues with
			// a phi.
			val firstLive = liveValues.first()
			val sources = manifests.map { m ->
				readOperand(
					firstLive,
					m.restrictionFor(firstLive),
					m.getDefinition(firstLive))
			}
			addInstruction(
				createPhi(
					createVector(sources),
					createWrite(setOf(firstLive), restriction)))
		}

		// Postpone a move into the notDefined values, if needed.
		val (defined, notDefined) =
			relatedSemanticValues.partition(::hasLiveSemanticValue)
		assert(defined.isNotEmpty())
		if (defined.isNotEmpty())
		{
			if (notDefined.isNotEmpty())
			{
				// Record a postponed move.
				recordPostponedInstruction(
					firstSemanticValue,
					dynamicMove(
						defined.first(),
						emptySet(),
						this@L2ValueManifest,
						restriction))
			}
			else
			{
				assert(postponedInstructionFor(firstSemanticValue) == null)
			}
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
					.entries
					.mapToSet { (synonym, instruction) ->
						InstructionEquivalence(instruction, synonym)
					}
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
				// an [L2_JUMP] (if the split was nequired).
				assert(predecessorEdges[i] === edge)
				assert(manifests[i] === edge.manifest())
				val valuesToForce = toEmit
					.mapToSet(transform = InstructionEquivalence::synonym)
					.flatMap(L2Synonym<*>::semanticValues)
				generator.forcePostponedTranslationsBeforeEdge(
					edge, valuesToForce)
				assert(valuesToForce.all(edge.manifest()::hasLiveSemanticValue))
			}
		} while (changed)
		// The same (up to equivalence) instructions are postponed in each
		// predecessor edge.  Produce new instructions by combining information
		// from corresponding originals, and add the new instructions to the
		// postponed map of the receiver.
		val selfMaps = manifests.map { manifest ->
			manifest.allPostponedInstructions()
				.entries
				.associateBy { (synonym, instruction) ->
					InstructionEquivalence(instruction, synonym)
				}
		}

		selfMaps[0].forEach { equivalence, instruction ->
			val oldInstructions = selfMaps.map { it[equivalence]!! }
			val newInstruction = instruction.value.mergeInstructions(
				oldInstructions.map(Map.Entry<*, L2Instruction>::value))
			agglomerateSynonym(
				equivalence.synonym.semanticValues(),
				newInstruction.writeOperands.single().restriction())
			val pick = equivalence.synonym.pickSemanticValue()
			val commonDefinitios = manifests
				.map { it.getAllDefinitions(pick).toSet() }
				.reduce(Set<L2Register<*>>::intersect)
			updateDefinitions(pick) { plus(commonDefinitios).cast() }
			if (newInstruction !is L2_MOVE<*>)
			{
				recordPostponedInstruction(
					oldInstructions[0].key.pickSemanticValue(),
					newInstruction)
			}
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
		valueToCheck: L2SemanticValue<*>,
		stopInstruction: L2Instruction,
		ignore: MutableSet<L2Instruction>
	): Boolean
	{
		val instruction = postponedInstructionFor(valueToCheck) ?: return false
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
		semanticValues: Set<L2SemanticValue<K>>,
		restriction: TypeRestriction)
	{
		val newSemanticValues = mutableSetOf<L2SemanticValue<K>>()
		val existingSynonyms = mutableSetOf<L2Synonym<K>>()
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
		updateDefinitions(sampleSemanticValue) { plus(register) }
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
				dynamicMergeExistingSemanticValues(
					initialSynonyms[group[0]].pickSemanticValue(),
					initialSynonyms[group[i]].pickSemanticValue())
			}
		}
		// Recompute the impossible restriction count.
		impossibleRestrictionCount =
			states.values.count(Constraint<*>::isImpossible)
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
		semanticValueTransformer: (L2SemanticValue<*>) -> L2SemanticValue<*>
	): L2ValueManifest
	{
		assert(mode == BySemanticValue)
		val newManifest = L2ValueManifest(mode)
		// Generic erasure is problematic here, so place an extra generic
		// function in the mix for the type checker to pin things to.
		fun <K: RegisterKind<K>> transformOne(
			semanticValue: L2SemanticValue<K>
		): L2SemanticValue<K> = semanticValueTransformer(semanticValue).cast()
		for (oldSynonym in synonymsArray())
		{
			val restriction = restrictionFor(oldSynonym.pickSemanticValue())
			oldSynonym.kind.run {
				newManifest.introduceSynonym(
					oldSynonym.semanticValues().map { transformOne(it) },
					restriction)
			}
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
		// Iterate over a copy of the map, so we can remove from it.
		var changed = false
		states.values.map(Constraint<*>::synonym).forEach { synonym ->
			changed = changed ||
				retainRegistersHelper(registersToRetain, synonym)
		}
		// Removing constraints can only transition from impossible to
		// possible.
		impossibleRestrictionCount =
			states.values.count { it.isImpossible }
		check()
		return changed
	}

	/**
	 * A helper to allow the [RegisterKind] to be fixed within this scope.
	 * Answer whether any changes were made.
	 */
	private fun <K: RegisterKind<K>> retainRegistersHelper(
		registersToRetain: Set<L2Register<*>>,
		synonym: L2Synonym<K>
	): Boolean = updateConstraint(synonym) {
		val definitionList = definitions.toMutableList()
		val changed = definitionList.retainAll(registersToRetain)
		if (changed)
		{
			definitions = definitionList
			if (definitionList.isEmpty())
			{
				// Remove this synonym and any semantic values within it.
				states.remove(classFor(synonym.pickSemanticValue()))
				classOf!!.keys.removeAll(
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
			updateDefinitions(synonym.pickSemanticValue()) {
				filterNot(registersToBeOverwritten::contains).cast()
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
		semanticValuesToRetain: Set<L2SemanticValue<*>>)
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
	fun <K: RegisterKind<K>> retainSemanticValuesInSynonym(
		synonym: L2Synonym<K>,
		semanticValuesToRetain: Set<L2SemanticValue<*>>)
	{
		val originalSemanticValues = synonym.semanticValues()
		val intersection =
			originalSemanticValues.intersect(semanticValuesToRetain)
		val valueClass = classFor(synonym.pickSemanticValue())
		val constraint = states[valueClass]!!
		val constant = constraint.restriction.constantOrNull
		val newSemanticValues: Set<L2SemanticValue<K>> = when
		{
			// DO NOT add a constant to the new synonym if it's empty.
			intersection.isEmpty() -> intersection
			// Not a constant.
			constant == null -> intersection
			// Combine non-empty survivor set with a constant.
			else -> intersection + synonym.kind.createSemanticConstant(constant)
		}.cast()
		// Exit quickly if no change to the synonym.
		if (newSemanticValues == originalSemanticValues) return
		// Unbind every original member; the survivors are rebound below.
		classOf!!.keys.removeAll(originalSemanticValues)
		// Exit quickly if no semantic values survive.
		if (newSemanticValues.isEmpty())
		{
			states.remove(valueClass)
			// See if we just eliminated an impossible constraint.
			if (constraint.isImpossible) impossibleRestrictionCount--
			return
		}
		// The class survives with a reduced membership; only its state is
		// replaced, so anything still holding the class stays valid.
		states[valueClass] = Constraint(
			newSemanticValues,
			constraint.definitions.cast(),
			constraint.restriction,
			constraint.postponedInstruction)
		bind(newSemanticValues, valueClass)
	}

	/**
	 * If the given [L2SemanticValue] is derived from some other value – an
	 * [L2SemanticExtractedTag] or an [L2SemanticObjectVariantId], possibly
	 * wrapped in an [L2SemanticUnboxedInt] – answer the value it was derived
	 * from, otherwise answer `null`.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to examine.
	 * @return
	 *   The value it was derived from, or `null` if it is not a derived value.
	 */
	private fun derivationBaseOrNull(
		semanticValue: L2SemanticValue<*>
	): L2SemanticValue<*>? = when (semanticValue)
	{
		is L2SemanticUnboxedInt -> derivationBaseOrNull(semanticValue.boxed)
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
		states.values.forEach { constraint ->
			constraint.members.forEach { member ->
				val base = derivationBaseOrNull(member) ?: return@forEach
				assert(hasSemanticValue(base))
				{
					buildString {
						append("Manifest holds a derived semantic value ")
						append("whose base it does not know.")
						append("\n  Derived: $member")
						append("\n  Missing base: $base")
						append("\n  Its synonym: ${constraint.members}")
					}
				}
			}
		}
	}

	fun checkUniqueConstantSynonyms()
	{
		val constants = mutableMapOf<
			Pair<RegisterKind<*>, AvailObject>,
			MutableSet<L2Synonym<*>>>()
		states.values.forEach { constraint ->
			constraint.restriction.constantOrNull?.let { constant ->
				val synonym = constraint.synonym
				constants.computeIfAbsent(synonym.kind to constant) {
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

		/**
		 * How deeply [renarrowPostponedConsumersOf] may recurse through chains
		 * of postponed instructions that feed one another.  Narrowing is
		 * monotone, so this is a comfort bound rather than a termination
		 * condition; anything deeper is simply left for the refresh that
		 * happens when the value is actually emitted.
		 */
		const val maxRenarrowDepth = 5
	}
}
