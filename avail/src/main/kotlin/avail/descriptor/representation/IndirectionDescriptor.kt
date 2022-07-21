/*
 * IndirectionDescriptor.kt
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
 * POSSIBILITY OF SUCH DAMAGE.
 */
package avail.descriptor.representation
import avail.AvailDebuggerModel
import avail.annotations.HideFieldInDebugger
import avail.compiler.AvailCodeGenerator
import avail.compiler.CompilationContext
import avail.compiler.ModuleHeader
import avail.compiler.ModuleManifestEntry
import avail.compiler.scanning.LexingState
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.atoms.A_Atom.Companion.setAtomBundle
import avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.addDefinitionParsingPlan
import avail.descriptor.bundles.A_Bundle.Companion.addGrammaticalRestriction
import avail.descriptor.bundles.A_Bundle.Companion.bundleAddMacro
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import avail.descriptor.bundles.A_Bundle.Companion.grammaticalRestrictions
import avail.descriptor.bundles.A_Bundle.Companion.hasGrammaticalRestrictions
import avail.descriptor.bundles.A_Bundle.Companion.lookupMacroByPhraseTuple
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.A_Bundle.Companion.messagePart
import avail.descriptor.bundles.A_Bundle.Companion.messageParts
import avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import avail.descriptor.bundles.A_Bundle.Companion.numArgs
import avail.descriptor.bundles.A_Bundle.Companion.removeGrammaticalRestriction
import avail.descriptor.bundles.A_Bundle.Companion.removeMacro
import avail.descriptor.bundles.A_Bundle.Companion.removePlanForSendable
import avail.descriptor.bundles.A_BundleTree
import avail.descriptor.bundles.A_BundleTree.Companion.addPlanInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.allParsingPlansInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.expand
import avail.descriptor.bundles.A_BundleTree.Companion.hasBackwardJump
import avail.descriptor.bundles.A_BundleTree.Companion.isSourceOfCycle
import avail.descriptor.bundles.A_BundleTree.Companion.latestBackwardJump
import avail.descriptor.bundles.A_BundleTree.Companion.lazyActions
import avail.descriptor.bundles.A_BundleTree.Companion.lazyComplete
import avail.descriptor.bundles.A_BundleTree.Companion.lazyIncomplete
import avail.descriptor.bundles.A_BundleTree.Companion.lazyIncompleteCaseInsensitive
import avail.descriptor.bundles.A_BundleTree.Companion.lazyPrefilterMap
import avail.descriptor.bundles.A_BundleTree.Companion.lazyTypeFilterTreePojo
import avail.descriptor.bundles.A_BundleTree.Companion.removePlanInProgress
import avail.descriptor.bundles.A_BundleTree.Companion.updateForNewGrammaticalRestriction
import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.character.A_Character.Companion.equalsCharacterWithCodePoint
import avail.descriptor.character.A_Character.Companion.isCharacter
import avail.descriptor.fiber.A_Fiber.Companion.availLoader
import avail.descriptor.fiber.A_Fiber.Companion.captureInDebugger
import avail.descriptor.fiber.A_Fiber.Companion.clearGeneralFlag
import avail.descriptor.fiber.A_Fiber.Companion.clearTraceFlag
import avail.descriptor.fiber.A_Fiber.Companion.continuation
import avail.descriptor.fiber.A_Fiber.Companion.debugLog
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.failureContinuation
import avail.descriptor.fiber.A_Fiber.Companion.fiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.fiberName
import avail.descriptor.fiber.A_Fiber.Companion.fiberNameSupplier
import avail.descriptor.fiber.A_Fiber.Companion.fiberResult
import avail.descriptor.fiber.A_Fiber.Companion.fiberResultType
import avail.descriptor.fiber.A_Fiber.Companion.generalFlag
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearInterruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.getAndClearReificationWaiters
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.interruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.joiningFibers
import avail.descriptor.fiber.A_Fiber.Companion.priority
import avail.descriptor.fiber.A_Fiber.Companion.recordVariableAccess
import avail.descriptor.fiber.A_Fiber.Companion.releaseFromDebugger
import avail.descriptor.fiber.A_Fiber.Companion.resultContinuation
import avail.descriptor.fiber.A_Fiber.Companion.setGeneralFlag
import avail.descriptor.fiber.A_Fiber.Companion.setInterruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.A_Fiber.Companion.setTraceFlag
import avail.descriptor.fiber.A_Fiber.Companion.suspendingFunction
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.A_Fiber.Companion.traceFlag
import avail.descriptor.fiber.A_Fiber.Companion.uniqueId
import avail.descriptor.fiber.A_Fiber.Companion.variablesReadBeforeWritten
import avail.descriptor.fiber.A_Fiber.Companion.variablesWritten
import avail.descriptor.fiber.A_Fiber.Companion.wakeupTask
import avail.descriptor.fiber.A_Fiber.Companion.whenContinuationIsAvailableDo
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import avail.descriptor.fiber.FiberDescriptor.TraceFlag
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.adjustPcAndStackp
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.currentLineNumber
import avail.descriptor.functions.A_Continuation.Companion.deoptimizedForDebugger
import avail.descriptor.functions.A_Continuation.Companion.ensureMutable
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.frameAtPut
import avail.descriptor.functions.A_Continuation.Companion.highlightPc
import avail.descriptor.functions.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.functions.A_Continuation.Companion.levelTwoOffset
import avail.descriptor.functions.A_Continuation.Companion.numSlots
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.registerDump
import avail.descriptor.functions.A_Continuation.Companion.replacingCaller
import avail.descriptor.functions.A_Continuation.Companion.stackAt
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_Function.Companion.numOuterVars
import avail.descriptor.functions.A_Function.Companion.optionallyNilOuterVar
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.constantTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.countdownToReoptimize
import avail.descriptor.functions.A_RawFunction.Companion.declarationNames
import avail.descriptor.functions.A_RawFunction.Companion.decreaseCountdownToReoptimizeFromPoll
import avail.descriptor.functions.A_RawFunction.Companion.decrementCountdownToReoptimize
import avail.descriptor.functions.A_RawFunction.Companion.lineNumberEncodedDeltas
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.maxStackDepth
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.A_RawFunction.Companion.numConstants
import avail.descriptor.functions.A_RawFunction.Companion.numLiterals
import avail.descriptor.functions.A_RawFunction.Companion.numLocals
import avail.descriptor.functions.A_RawFunction.Companion.numNybbles
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.A_RawFunction.Companion.nybbles
import avail.descriptor.functions.A_RawFunction.Companion.originatingPhrase
import avail.descriptor.functions.A_RawFunction.Companion.originatingPhraseIndex
import avail.descriptor.functions.A_RawFunction.Companion.outerTypeAt
import avail.descriptor.functions.A_RawFunction.Companion.packedDeclarationNames
import avail.descriptor.functions.A_RawFunction.Companion.returnTypeIfPrimitiveFails
import avail.descriptor.functions.A_RawFunction.Companion.returneeCheckStat
import avail.descriptor.functions.A_RawFunction.Companion.returnerCheckStat
import avail.descriptor.functions.A_RawFunction.Companion.setStartingChunkAndReoptimizationCountdown
import avail.descriptor.functions.A_RawFunction.Companion.startingChunk
import avail.descriptor.functions.A_RawFunction.Companion.tallyInvocation
import avail.descriptor.functions.A_RawFunction.Companion.totalInvocations
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.keysAsSet
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapAtReplacingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapIterable
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.maps.A_MapBin
import avail.descriptor.maps.A_MapBin.Companion.forEachInMapBin
import avail.descriptor.maps.A_MapBin.Companion.isHashedMapBin
import avail.descriptor.maps.A_MapBin.Companion.mapBinAtHash
import avail.descriptor.maps.A_MapBin.Companion.mapBinAtHashPutLevelCanDestroy
import avail.descriptor.maps.A_MapBin.Companion.mapBinAtHashReplacingLevelCanDestroy
import avail.descriptor.maps.A_MapBin.Companion.mapBinIterator
import avail.descriptor.maps.A_MapBin.Companion.mapBinKeyUnionKind
import avail.descriptor.maps.A_MapBin.Companion.mapBinKeysHash
import avail.descriptor.maps.A_MapBin.Companion.mapBinRemoveKeyHashCanDestroy
import avail.descriptor.maps.A_MapBin.Companion.mapBinSize
import avail.descriptor.maps.A_MapBin.Companion.mapBinValueUnionKind
import avail.descriptor.maps.A_MapBin.Companion.mapBinValuesHash
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.maps.MapDescriptor.MapIterator
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.addSealedArgumentsType
import avail.descriptor.methods.A_Method.Companion.addSemanticRestriction
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.chooseBundle
import avail.descriptor.methods.A_Method.Companion.definitionsAtOrBelow
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.filterByTypes
import avail.descriptor.methods.A_Method.Companion.includesDefinition
import avail.descriptor.methods.A_Method.Companion.isMethodEmpty
import avail.descriptor.methods.A_Method.Companion.lexer
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.lookupByValuesFromList
import avail.descriptor.methods.A_Method.Companion.membershipChanged
import avail.descriptor.methods.A_Method.Companion.methodAddBundle
import avail.descriptor.methods.A_Method.Companion.methodAddDefinition
import avail.descriptor.methods.A_Method.Companion.methodRemoveBundle
import avail.descriptor.methods.A_Method.Companion.methodStylers
import avail.descriptor.methods.A_Method.Companion.removeDefinition
import avail.descriptor.methods.A_Method.Companion.removeSealedArgumentsType
import avail.descriptor.methods.A_Method.Companion.removeSemanticRestriction
import avail.descriptor.methods.A_Method.Companion.sealedArgumentsTypesTuple
import avail.descriptor.methods.A_Method.Companion.semanticRestrictions
import avail.descriptor.methods.A_Method.Companion.testingTree
import avail.descriptor.methods.A_Method.Companion.updateStylers
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.A_Sendable.Companion.isAbstractDefinition
import avail.descriptor.methods.A_Sendable.Companion.isForwardDefinition
import avail.descriptor.methods.A_Sendable.Companion.isMethodDefinition
import avail.descriptor.methods.A_Sendable.Companion.parsingSignature
import avail.descriptor.methods.A_Styler
import avail.descriptor.methods.A_Styler.Companion.stylerMethod
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.addBundle
import avail.descriptor.module.A_Module.Companion.addConstantBinding
import avail.descriptor.module.A_Module.Companion.addImportedName
import avail.descriptor.module.A_Module.Companion.addImportedNames
import avail.descriptor.module.A_Module.Companion.addLexer
import avail.descriptor.module.A_Module.Companion.addPrivateName
import avail.descriptor.module.A_Module.Companion.addPrivateNames
import avail.descriptor.module.A_Module.Companion.addSeal
import avail.descriptor.module.A_Module.Companion.addUnloadFunction
import avail.descriptor.module.A_Module.Companion.addVariableBinding
import avail.descriptor.module.A_Module.Companion.allAncestors
import avail.descriptor.module.A_Module.Companion.applyModuleHeader
import avail.descriptor.module.A_Module.Companion.buildFilteredBundleTree
import avail.descriptor.module.A_Module.Companion.constantBindings
import avail.descriptor.module.A_Module.Companion.createLexicalScanner
import avail.descriptor.module.A_Module.Companion.entryPoints
import avail.descriptor.module.A_Module.Companion.exportedNames
import avail.descriptor.module.A_Module.Companion.getAndSetTupleOfBlockPhrases
import avail.descriptor.module.A_Module.Companion.hasAncestor
import avail.descriptor.module.A_Module.Companion.importedNames
import avail.descriptor.module.A_Module.Companion.introduceNewName
import avail.descriptor.module.A_Module.Companion.manifestEntries
import avail.descriptor.module.A_Module.Companion.methodDefinitions
import avail.descriptor.module.A_Module.Companion.moduleAddDefinition
import avail.descriptor.module.A_Module.Companion.moduleAddGrammaticalRestriction
import avail.descriptor.module.A_Module.Companion.moduleAddMacro
import avail.descriptor.module.A_Module.Companion.moduleAddSemanticRestriction
import avail.descriptor.module.A_Module.Companion.moduleAddStyler
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.module.A_Module.Companion.moduleState
import avail.descriptor.module.A_Module.Companion.newNames
import avail.descriptor.module.A_Module.Companion.originatingPhraseAtIndex
import avail.descriptor.module.A_Module.Companion.privateNames
import avail.descriptor.module.A_Module.Companion.recordBlockPhrase
import avail.descriptor.module.A_Module.Companion.removeFrom
import avail.descriptor.module.A_Module.Companion.resolveForward
import avail.descriptor.module.A_Module.Companion.serializedObjects
import avail.descriptor.module.A_Module.Companion.setManifestEntriesIndex
import avail.descriptor.module.A_Module.Companion.setStylingRecordIndex
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.A_Module.Companion.stylers
import avail.descriptor.module.A_Module.Companion.stylingRecord
import avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import avail.descriptor.module.A_Module.Companion.variableBindings
import avail.descriptor.module.A_Module.Companion.versions
import avail.descriptor.module.A_Module.Companion.visibleNames
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.addToDoubleCanDestroy
import avail.descriptor.numbers.A_Number.Companion.addToFloatCanDestroy
import avail.descriptor.numbers.A_Number.Companion.addToInfinityCanDestroy
import avail.descriptor.numbers.A_Number.Companion.addToIntegerCanDestroy
import avail.descriptor.numbers.A_Number.Companion.asBigInteger
import avail.descriptor.numbers.A_Number.Companion.bitSet
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.bitShiftLeftTruncatingToBits
import avail.descriptor.numbers.A_Number.Companion.bitTest
import avail.descriptor.numbers.A_Number.Companion.bitwiseAnd
import avail.descriptor.numbers.A_Number.Companion.bitwiseOr
import avail.descriptor.numbers.A_Number.Companion.bitwiseXor
import avail.descriptor.numbers.A_Number.Companion.divideCanDestroy
import avail.descriptor.numbers.A_Number.Companion.divideIntoDoubleCanDestroy
import avail.descriptor.numbers.A_Number.Companion.divideIntoFloatCanDestroy
import avail.descriptor.numbers.A_Number.Companion.divideIntoInfinityCanDestroy
import avail.descriptor.numbers.A_Number.Companion.divideIntoIntegerCanDestroy
import avail.descriptor.numbers.A_Number.Companion.equalsDouble
import avail.descriptor.numbers.A_Number.Companion.equalsFloat
import avail.descriptor.numbers.A_Number.Companion.equalsInfinity
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.equalsInteger
import avail.descriptor.numbers.A_Number.Companion.extractDouble
import avail.descriptor.numbers.A_Number.Companion.extractFloat
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.extractNybble
import avail.descriptor.numbers.A_Number.Companion.extractSignedByte
import avail.descriptor.numbers.A_Number.Companion.extractSignedShort
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedShort
import avail.descriptor.numbers.A_Number.Companion.isDouble
import avail.descriptor.numbers.A_Number.Companion.isFloat
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.A_Number.Companion.isNumericallyIntegral
import avail.descriptor.numbers.A_Number.Companion.isPositive
import avail.descriptor.numbers.A_Number.Companion.isSignedByte
import avail.descriptor.numbers.A_Number.Companion.isSignedShort
import avail.descriptor.numbers.A_Number.Companion.isUnsignedShort
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.multiplyByDoubleCanDestroy
import avail.descriptor.numbers.A_Number.Companion.multiplyByFloatCanDestroy
import avail.descriptor.numbers.A_Number.Companion.multiplyByInfinityCanDestroy
import avail.descriptor.numbers.A_Number.Companion.multiplyByIntegerCanDestroy
import avail.descriptor.numbers.A_Number.Companion.numericCompare
import avail.descriptor.numbers.A_Number.Companion.numericCompareToDouble
import avail.descriptor.numbers.A_Number.Companion.numericCompareToInfinity
import avail.descriptor.numbers.A_Number.Companion.numericCompareToInteger
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.rawSignedIntegerAt
import avail.descriptor.numbers.A_Number.Companion.rawSignedIntegerAtPut
import avail.descriptor.numbers.A_Number.Companion.rawUnsignedIntegerAt
import avail.descriptor.numbers.A_Number.Companion.rawUnsignedIntegerAtPut
import avail.descriptor.numbers.A_Number.Companion.subtractFromDoubleCanDestroy
import avail.descriptor.numbers.A_Number.Companion.subtractFromFloatCanDestroy
import avail.descriptor.numbers.A_Number.Companion.subtractFromInfinityCanDestroy
import avail.descriptor.numbers.A_Number.Companion.subtractFromIntegerCanDestroy
import avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import avail.descriptor.numbers.A_Number.Companion.trimExcessInts
import avail.descriptor.numbers.AbstractNumberDescriptor
import avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.parsing.A_Lexer.Companion.lexerApplicability
import avail.descriptor.parsing.A_Lexer.Companion.lexerBodyFunction
import avail.descriptor.parsing.A_Lexer.Companion.lexerFilterFunction
import avail.descriptor.parsing.A_Lexer.Companion.lexerMethod
import avail.descriptor.parsing.A_Lexer.Companion.setLexerApplicability
import avail.descriptor.parsing.A_ParsingPlanInProgress
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.isBackwardJump
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.nameHighlightingPc
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPc
import avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPlan
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.A_Phrase.Companion.apparentSendName
import avail.descriptor.phrases.A_Phrase.Companion.applyStylesThen
import avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
import avail.descriptor.phrases.A_Phrase.Companion.bundle
import avail.descriptor.phrases.A_Phrase.Companion.childrenDo
import avail.descriptor.phrases.A_Phrase.Companion.childrenMap
import avail.descriptor.phrases.A_Phrase.Companion.copyConcatenating
import avail.descriptor.phrases.A_Phrase.Companion.copyMutablePhrase
import avail.descriptor.phrases.A_Phrase.Companion.copyWith
import avail.descriptor.phrases.A_Phrase.Companion.declaration
import avail.descriptor.phrases.A_Phrase.Companion.declaredExceptions
import avail.descriptor.phrases.A_Phrase.Companion.declaredType
import avail.descriptor.phrases.A_Phrase.Companion.emitAllValuesOn
import avail.descriptor.phrases.A_Phrase.Companion.emitEffectOn
import avail.descriptor.phrases.A_Phrase.Companion.emitValueOn
import avail.descriptor.phrases.A_Phrase.Companion.expression
import avail.descriptor.phrases.A_Phrase.Companion.expressionAt
import avail.descriptor.phrases.A_Phrase.Companion.expressionsSize
import avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import avail.descriptor.phrases.A_Phrase.Companion.flattenStatementsInto
import avail.descriptor.phrases.A_Phrase.Companion.generateInModule
import avail.descriptor.phrases.A_Phrase.Companion.hasSuperCast
import avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import avail.descriptor.phrases.A_Phrase.Companion.isLastUse
import avail.descriptor.phrases.A_Phrase.Companion.isMacroSubstitutionNode
import avail.descriptor.phrases.A_Phrase.Companion.lastExpression
import avail.descriptor.phrases.A_Phrase.Companion.list
import avail.descriptor.phrases.A_Phrase.Companion.literalObject
import avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import avail.descriptor.phrases.A_Phrase.Companion.markerValue
import avail.descriptor.phrases.A_Phrase.Companion.neededVariables
import avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
import avail.descriptor.phrases.A_Phrase.Companion.permutation
import avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import avail.descriptor.phrases.A_Phrase.Companion.phraseKindIsUnder
import avail.descriptor.phrases.A_Phrase.Companion.sequence
import avail.descriptor.phrases.A_Phrase.Companion.statements
import avail.descriptor.phrases.A_Phrase.Companion.statementsDo
import avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
import avail.descriptor.phrases.A_Phrase.Companion.stripMacro
import avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import avail.descriptor.phrases.A_Phrase.Companion.token
import avail.descriptor.phrases.A_Phrase.Companion.tokens
import avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import avail.descriptor.phrases.A_Phrase.Companion.validateLocally
import avail.descriptor.phrases.A_Phrase.Companion.variable
import avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import avail.descriptor.representation.A_BasicObject.Companion.objectVariant
import avail.descriptor.representation.IndirectionDescriptor.ObjectSlots.INDIRECTION_TARGET
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.asTuple
import avail.descriptor.sets.A_Set.Companion.equalsSet
import avail.descriptor.sets.A_Set.Companion.hasElement
import avail.descriptor.sets.A_Set.Companion.isSet
import avail.descriptor.sets.A_Set.Companion.isSubsetOf
import avail.descriptor.sets.A_Set.Companion.setElementsAreAllInstancesOfKind
import avail.descriptor.sets.A_Set.Companion.setIntersectionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setIntersects
import avail.descriptor.sets.A_Set.Companion.setMinusCanDestroy
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import avail.descriptor.sets.A_SetBin
import avail.descriptor.sets.A_SetBin.Companion.binElementAt
import avail.descriptor.sets.A_SetBin.Companion.binElementsAreAllInstancesOfKind
import avail.descriptor.sets.A_SetBin.Companion.binHasElementWithHash
import avail.descriptor.sets.A_SetBin.Companion.binRemoveElementHashLevelCanDestroy
import avail.descriptor.sets.A_SetBin.Companion.binUnionKind
import avail.descriptor.sets.A_SetBin.Companion.isBinSubsetOf
import avail.descriptor.sets.A_SetBin.Companion.isSetBin
import avail.descriptor.sets.A_SetBin.Companion.setBinAddingElementHashLevelCanDestroy
import avail.descriptor.sets.A_SetBin.Companion.setBinHash
import avail.descriptor.sets.A_SetBin.Companion.setBinIterator
import avail.descriptor.sets.A_SetBin.Companion.setBinSize
import avail.descriptor.sets.SetDescriptor.SetIterator
import avail.descriptor.tokens.A_Token
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.asSet
import avail.descriptor.tuples.A_Tuple.Companion.bitsPerEntry
import avail.descriptor.tuples.A_Tuple.Companion.byteArray
import avail.descriptor.tuples.A_Tuple.Companion.byteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.childAt
import avail.descriptor.tuples.A_Tuple.Companion.childCount
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithAnyTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteArrayTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteBufferTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteStringStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithIntTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithIntegerIntervalTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithNybbleTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithObjectTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithRepeatedElementTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithSmallIntegerIntervalTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithTwoByteStringStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.computeHashFromTo
import avail.descriptor.tuples.A_Tuple.Companion.concatenateTuplesCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableIntTuple
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableLongTuple
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.extractNybbleFromTupleAt
import avail.descriptor.tuples.A_Tuple.Companion.hashFromTo
import avail.descriptor.tuples.A_Tuple.Companion.isBetterRepresentationThan
import avail.descriptor.tuples.A_Tuple.Companion.parallelStream
import avail.descriptor.tuples.A_Tuple.Companion.rawByteForCharacterAt
import avail.descriptor.tuples.A_Tuple.Companion.replaceFirstChild
import avail.descriptor.tuples.A_Tuple.Companion.stream
import avail.descriptor.tuples.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleElementsInRangeAreInstancesOf
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsArgTypesFromFunctionType
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsListOfArgValues
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArguments
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.computeInstanceTag
import avail.descriptor.types.A_Type.Companion.computeSuperkind
import avail.descriptor.types.A_Type.Companion.contentType
import avail.descriptor.types.A_Type.Companion.couldEverBeInvokedWith
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.fieldTypeTuple
import avail.descriptor.types.A_Type.Companion.hasObjectInstance
import avail.descriptor.types.A_Type.Companion.instance
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.instanceTag
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfCompiledCodeType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfContinuationType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfEnumerationType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfFiberType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfFunctionType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfListNodeType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfLiteralTokenType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfMapType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfObjectType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPhraseType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPojoBottomType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPojoType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.isSupertypeOfSetType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfTokenType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfTupleType
import avail.descriptor.types.A_Type.Companion.isSupertypeOfVariableType
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.literalType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.descriptor.types.A_Type.Companion.parent
import avail.descriptor.types.A_Type.Companion.phraseKind
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.subexpressionsTupleType
import avail.descriptor.types.A_Type.Companion.trimType
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfCompiledCodeType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfContinuationType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfFiberType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfFunctionType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfListNodeType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfLiteralTokenType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfMapType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfObjectType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPhraseType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPojoFusedType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPojoType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPojoUnfusedType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfSetType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfTokenType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfTupleType
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfVariableType
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfCompiledCodeType
import avail.descriptor.types.A_Type.Companion.typeUnionOfContinuationType
import avail.descriptor.types.A_Type.Companion.typeUnionOfFiberType
import avail.descriptor.types.A_Type.Companion.typeUnionOfFunctionType
import avail.descriptor.types.A_Type.Companion.typeUnionOfIntegerRangeType
import avail.descriptor.types.A_Type.Companion.typeUnionOfListNodeType
import avail.descriptor.types.A_Type.Companion.typeUnionOfLiteralTokenType
import avail.descriptor.types.A_Type.Companion.typeUnionOfMapType
import avail.descriptor.types.A_Type.Companion.typeUnionOfObjectType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPhraseType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoFusedType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPojoUnfusedType
import avail.descriptor.types.A_Type.Companion.typeUnionOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.typeUnionOfSetType
import avail.descriptor.types.A_Type.Companion.typeUnionOfTokenType
import avail.descriptor.types.A_Type.Companion.typeUnionOfTupleType
import avail.descriptor.types.A_Type.Companion.typeUnionOfVariableType
import avail.descriptor.types.A_Type.Companion.typeVariables
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import avail.dispatch.LookupTree
import avail.exceptions.AvailException
import avail.exceptions.MalformedMessageException
import avail.exceptions.MethodDefinitionException
import avail.exceptions.SignatureException
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.Primitive
import avail.interpreter.execution.AvailLoader
import avail.interpreter.execution.AvailLoader.LexicalScanner
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.io.TextInterface
import avail.performance.Statistic
import avail.persistence.cache.Repository.StylingRecord
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Deque
import java.util.IdentityHashMap
import java.util.Spliterator
import java.util.TimerTask
import java.util.stream.Stream

/**
 * An [AvailObject] with an [IndirectionDescriptor] keeps track of its target,
 * that which it is pretending to be.  Almost all messages are routed to the
 * target, making it an ideal proxy.
 *
 * When some kinds of objects are compared to each other, say
 * [strings][A_String], a check is first made to see if the objects are at the
 * same location in memory -- the same AvailObject, in the current version that
 * uses [AvailObjectRepresentation].  If so, it immediately returns true.  If
 * not, a more detailed, potentially expensive comparison takes place.  If the
 * objects are found to be equal, one of them is mutated into an indirection (by
 * replacing its descriptor with an `IndirectionDescriptor`) to cause subsequent
 * comparisons to be faster.
 *
 * When Avail has had its own garbage collector over the years, it has been
 * possible to strip off indirections during a suitable level of garbage
 * collection.  When combined with the comparison optimization above, this has
 * the effect of collapsing together equal objects.  There was even once a
 * mechanism that collected objects at some garbage collection generation into a
 * set, causing *all* equal objects in that generation to be compared against
 * each other.  So not only does this mechanism save time, it also saves space.
 *
 * Of course, the cost of traversing indirections, and even just of descriptors
 * may be significant.  That's a complexity price that's paid once, with many
 * mechanisms depending on it to effect higher level optimizations.  Our bet is
 * that this will have a net payoff.  Especially since the low level
 * optimizations can be replaced with expression folding, dynamic inlining,
 * object escape analysis, instance-specific optimizations, and a plethora of
 * other just-in-time optimizations.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] that's in use at the target of this indirection.  Note that
 *   this will never change, even if the target is mutable.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class IndirectionDescriptor private constructor(
	mutability: Mutability,
	typeTag: TypeTag
) : AbstractDescriptor(
	mutability,
	typeTag,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The object slots of my [AvailObject] instances.  In particular, an
	 * [indirection][IndirectionDescriptor] has just a [INDIRECTION_TARGET],
	 * which is the object that the current object is equivalent to.  There may
	 * be other slots, depending on our mechanism for conversion to an
	 * indirection object, but they should be ignored.
	 */
	internal enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The target [object][AvailObject] to which my instance is delegating
		 * all behavior.
		 */
		INDIRECTION_TARGET
	}

	/**
	 * The integer slots of my [AvailObject] instances.  Always ignored for an
	 * indirection object.
	 */
	internal enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * Ignore all integer slots.
		 */
		@Suppress("unused")
		@HideFieldInDebugger
		IGNORED_INTEGER_SLOT_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = e === INDIRECTION_TARGET

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = self.traversed().printOnAvoidingIndent(builder, recursionMap, indent)

	/**
	 * Answer the non-indirection pointed to (transitively) by object.  Also
	 * changes the object to point directly at the ultimate target to save hops
	 * next time if possible.
	 */
	override fun o_Traversed(self: AvailObject): AvailObject {
		val next = self.slot(INDIRECTION_TARGET)
		val finalObject = next.traversed()
		if (!finalObject.sameAddressAs(next)) {
			self.setSlot(INDIRECTION_TARGET, finalObject)
		}
		return finalObject
	}

	/**
	 * Answer the non-indirection pointed to (transitively) by object.  Also
	 * changes the object to point directly at the ultimate target to save hops
	 * next time if possible.  While shortening chains of indirections, it
	 * ignores the otherwise forbidden immutable->mutable pointers.
	 */
	override fun o_TraversedWhileMakingImmutable(self: AvailObject): AvailObject
	{
		val next = self.slot(INDIRECTION_TARGET)
		val finalObject = next.traversedWhileMakingImmutable()
		if (!finalObject.sameAddressAs(next)) {
			// Allow immutable -> mutable pointers, since we're doing an
			// iterative makeImmutable() operation, and the graph is allowed to
			// have that form temporarily.
			self.writeBackSlot(INDIRECTION_TARGET, 1, finalObject)
		}
		return finalObject
	}

	/**
	 * Answer the non-indirection pointed to (transitively) by object.  Also
	 * changes the object to point directly at the ultimate target to save hops
	 * next time if possible.  While shortening chains of indirections, it
	 * ignores the otherwise forbidden shared->unshared pointers.
	 */
	override fun o_TraversedWhileMakingShared(self: AvailObject): AvailObject
	{
		val next = self.slot(INDIRECTION_TARGET)
		val finalObject = next.traversedWhileMakingShared()
		if (!finalObject.sameAddressAs(next)) {
			// Allow shared -> unshared pointers, since we're doing an iterative
			// makeShared() operation, and the graph is allowed to have that
			// form temporarily.
			self.writeBackSlot(INDIRECTION_TARGET, 1, finalObject)
		}
		return finalObject
	}

	companion object {
		/** The mutable [IndirectionDescriptor]. */
		val mutables = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.MUTABLE, typeTag)
		}.toTypedArray()

		/** The immutable [IndirectionDescriptor]. */
		val immutables = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.IMMUTABLE, typeTag)
		}.toTypedArray()

		/** The shared [IndirectionDescriptor]. */
		val shareds = TypeTag.values().map { typeTag ->
			IndirectionDescriptor(Mutability.SHARED, typeTag)
		}.toTypedArray()

		/**
		 * Answer a [shared][Mutability.MUTABLE] `IndirectionDescriptor`
		 * suitable for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun mutable(typeTag: TypeTag): IndirectionDescriptor =
			mutables[typeTag.ordinal]

		/**
		 * Answer a [shared][Mutability.IMMUTABLE] `IndirectionDescriptor`
		 * suitable for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun immutable(typeTag: TypeTag): IndirectionDescriptor =
			immutables[typeTag.ordinal]

		/**
		 * Answer a [shared][Mutability.SHARED] `IndirectionDescriptor` suitable
		 * for pointing to an object having the given [TypeTag].
		 *
		 * @param typeTag
		 *   The target's [TypeTag].
		 * @return
		 *   An `IndirectionDescriptor`.
		 */
		fun shared(typeTag: TypeTag): IndirectionDescriptor =
			shareds[typeTag.ordinal]
	}

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun mutable() = mutables[typeTag.ordinal]

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun immutable() =
		immutables[typeTag.ordinal]

	@Deprecated(
		"Not recommended",
		ReplaceWith("IndirectionDescriptor.Companion.mutable(TypeTag)"))
	override fun shared() = shareds[typeTag.ordinal]


	override fun o_ComputeTypeTag(self: AvailObject): TypeTag {
		val tag = self .. { typeTag }
		// Now that we know it, switch to a descriptor that has it cached...
		self.setDescriptor(when {
			mutability === Mutability.MUTABLE -> mutable(tag)
			mutability === Mutability.IMMUTABLE -> immutable(tag)
			else -> shared(tag)
		})
		return tag
	}

	/**
	 * Define the infix ".." operator  to reducing redundancy in the many reflex
	 * methods below.
	 *
	 * @param body
	 *   The action to perform on the traversed receiver.
	 * @return
	 *   The value produced by the action.
	 */
	inline operator fun <R> AvailObject.rangeTo(
		body: AvailObject.() -> R
	): R = o_Traversed(this@rangeTo).body()


	/* ====================================================================
	 * The remainder of these methods are reflex methods that send the same
	 * message to the traversed target.
	 * ====================================================================
	 */

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type
	): Boolean = self .. { acceptsArgTypesFromFunctionType(functionType) }

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>
	): Boolean = self .. { acceptsListOfArgTypes(argTypes) }

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>
	): Boolean = self .. { acceptsListOfArgValues(argValues) }

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple
	): Boolean = self .. { acceptsTupleOfArgTypes(argTypes) }

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple
	): Boolean = self .. { acceptsTupleOfArguments(arguments) }

	override fun o_AddDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) = self .. { addDependentChunk(chunk) }

	@Throws(SignatureException::class)
	override fun o_MethodAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { methodAddDefinition(definition) }

	override fun o_AddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self .. { addGrammaticalRestriction(grammaticalRestriction) }

	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { addToInfinityCanDestroy(sign, canDestroy) }

	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. { addToIntegerCanDestroy(anInteger, canDestroy) }

	override fun o_ModuleAddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self .. { moduleAddGrammaticalRestriction(grammaticalRestriction) }

	override fun o_ModuleAddDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { moduleAddDefinition(definition) }

	override fun o_AddDefinitionParsingPlan(
		self: AvailObject,
		plan: A_DefinitionParsingPlan
	) = self .. { addDefinitionParsingPlan(plan) }

	override fun o_AddImportedName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { addImportedName(trueName) }

	override fun o_AddImportedNames(
		self: AvailObject,
		trueNames: A_Set
	) = self .. { addImportedNames(trueNames) }

	override fun o_IntroduceNewName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { introduceNewName(trueName) }

	override fun o_AddPrivateName(
		self: AvailObject,
		trueName: A_Atom
	) = self .. { addPrivateName(trueName) }

	override fun o_AddPrivateNames(
		self: AvailObject,
		trueNames: A_Set
	) = self .. { addPrivateNames(trueNames) }

	override fun o_SetBinAddingElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_SetBin = self.. {
		setBinAddingElementHashLevelCanDestroy(
			elementObject, elementObjectHash, myLevel, canDestroy)
	}

	override fun o_BinElementAt(
		self: AvailObject,
		index: Int
	): AvailObject = self .. { binElementAt(index) }

	override fun o_BinHasElementWithHash(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean = self .. {
		binHasElementWithHash(elementObject, elementObjectHash)
	}

	override fun o_BinRemoveElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_SetBin = self .. {
		binRemoveElementHashLevelCanDestroy(
			elementObject, elementObjectHash, myLevel, canDestroy)
	}

	override fun o_BuildFilteredBundleTree(
		self: AvailObject
	): A_BundleTree = self .. { buildFilteredBundleTree() }

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithStartingAt(
			startIndex1, endIndex1, anotherObject, startIndex2)
	}

	override fun o_CompareFromToWithAnyTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithAnyTupleStartingAt(
			startIndex1, endIndex1, aTuple, startIndex2)
	}

	override fun o_CompareFromToWithByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteStringStartingAt(
			startIndex1, endIndex1, aByteString, startIndex2)
	}

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteTupleStartingAt(
			startIndex1, endIndex1, aByteTuple, startIndex2)
	}

	override fun o_CompareFromToWithIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithIntegerIntervalTupleStartingAt(
			startIndex1, endIndex1, anIntegerIntervalTuple, startIndex2)
	}

	override fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex1, endIndex1, aSmallIntegerIntervalTuple, startIndex2)
	}

	override fun o_CompareFromToWithRepeatedElementTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aRepeatedElementTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithRepeatedElementTupleStartingAt(
			startIndex1, endIndex1, aRepeatedElementTuple, startIndex2)
	}

	override fun o_CompareFromToWithNybbleTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithNybbleTupleStartingAt(
			startIndex1, endIndex1, aNybbleTuple, startIndex2)
	}

	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithObjectTupleStartingAt(
			startIndex1, endIndex1, anObjectTuple, startIndex2)
	}

	override fun o_CompareFromToWithTwoByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithTwoByteStringStartingAt(
			startIndex1, endIndex1, aTwoByteString, startIndex2)
	}

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int
	): Int = self .. { computeHashFromTo(start, end) }

	override fun o_ConcatenateTuplesCanDestroy(
		self: AvailObject,
		canDestroy: Boolean
	): A_Tuple = self .. { concatenateTuplesCanDestroy(canDestroy) }

	override fun o_SetContinuation(
		self: AvailObject,
		value: A_Continuation
	) = self .. { continuation = value }

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean
	): A_Tuple = self .. {
		copyTupleFromToCanDestroy(start, end, canDestroy)
	}

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>
	): Boolean = self .. { couldEverBeInvokedWith(argRestrictions) }

	override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { divideCanDestroy(aNumber, canDestroy) }

	override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { divideIntoInfinityCanDestroy(sign, canDestroy) }

	override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		divideIntoIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = self .. { equals(another) }

	override fun o_EqualsAnyTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsAnyTuple(aTuple) }

	override fun o_EqualsByteString(
		self: AvailObject,
		aByteString: A_String
	): Boolean = self .. { equalsByteString(aByteString) }

	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple
	): Boolean = self .. { equalsByteTuple(aByteTuple) }

	override fun o_EqualsCharacterWithCodePoint(
		self: AvailObject,
		aCodePoint: Int
	): Boolean = self .. { equalsCharacterWithCodePoint(aCodePoint) }

	override fun o_EqualsFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): Boolean = self .. { equalsFiberType(aFiberType) }

	override fun o_EqualsFunction(
		self: AvailObject,
		aFunction: A_Function
	): Boolean = self .. { equalsFunction(aFunction) }

	override fun o_EqualsFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): Boolean = self .. { equalsFunctionType(aFunctionType) }

	override fun o_EqualsIntegerIntervalTuple(
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple
	): Boolean = self .. { equalsIntegerIntervalTuple(
		anIntegerIntervalTuple) }

	override fun o_EqualsSmallIntegerIntervalTuple(
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple
	): Boolean = self .. {
		equalsSmallIntegerIntervalTuple(aSmallIntegerIntervalTuple)
	}

	override fun o_EqualsRepeatedElementTuple(
		self: AvailObject,
		aRepeatedElementTuple: A_Tuple
	): Boolean = self .. {
		equalsRepeatedElementTuple(aRepeatedElementTuple)
	}

	override fun o_EqualsCompiledCode(
		self: AvailObject,
		aCompiledCode: A_RawFunction
	): Boolean = self .. { equalsCompiledCode(aCompiledCode) }

	override fun o_EqualsVariableType(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { equalsVariableType(aType) }

	override fun o_EqualsContinuation(
		self: AvailObject,
		aContinuation: A_Continuation
	): Boolean = self .. { equalsContinuation(aContinuation) }

	override fun o_EqualsContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): Boolean = self .. { equalsContinuationType(aContinuationType) }

	override fun o_EqualsCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): Boolean = self .. { equalsCompiledCodeType(aCompiledCodeType) }

	override fun o_EqualsDouble(
		self: AvailObject,
		aDouble: Double
	): Boolean = self .. { equalsDouble(aDouble) }

	override fun o_EqualsFloat(
		self: AvailObject,
		aFloat: Float
	): Boolean = self .. { equalsFloat(aFloat) }

	override fun o_EqualsInfinity(
		self: AvailObject,
		sign: Sign
	): Boolean = self .. { equalsInfinity(sign) }

	override fun o_EqualsInteger(
		self: AvailObject,
		anAvailInteger: AvailObject
	): Boolean = self .. { equalsInteger(anAvailInteger) }

	override fun o_EqualsIntegerRangeType(
		self: AvailObject,
		another: A_Type
	): Boolean = self .. { equalsIntegerRangeType(another) }

	override fun o_EqualsMap(
		self: AvailObject,
		aMap: A_Map
	): Boolean = self .. { equalsMap(aMap) }

	override fun o_EqualsMapType(
		self: AvailObject,
		aMapType: A_Type
	): Boolean = self .. { equalsMapType(aMapType) }

	override fun o_EqualsNybbleTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsNybbleTuple(aTuple) }

	override fun o_EqualsObject(
		self: AvailObject,
		anObject: AvailObject
	): Boolean = self .. { equalsObject(anObject) }

	override fun o_EqualsObjectTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsObjectTuple(aTuple) }

	override fun o_EqualsPojo(
		self: AvailObject,
		aPojo: AvailObject
	): Boolean = self .. { equalsPojo(aPojo) }

	override fun o_EqualsPojoType(
		self: AvailObject,
		aPojoType: AvailObject
	): Boolean = self .. { equalsPojoType(aPojoType) }

	override fun o_EqualsPrimitiveType(
		self: AvailObject,
		aPrimitiveType: A_Type
	): Boolean = self .. { equalsPrimitiveType(aPrimitiveType) }

	override fun o_EqualsRawPojoFor(
		self: AvailObject,
		otherRawPojo: AvailObject,
		otherJavaObject: Any?
	): Boolean = self .. { equalsRawPojoFor(otherRawPojo, otherJavaObject) }

	override fun o_EqualsReverseTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean = self .. { equalsReverseTuple(aTuple) }

	override fun o_EqualsSet(
		self: AvailObject,
		aSet: A_Set
	): Boolean = self .. { equalsSet(aSet) }

	override fun o_EqualsSetType(
		self: AvailObject,
		aSetType: A_Type
	): Boolean = self .. { equalsSetType(aSetType) }

	override fun o_EqualsTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): Boolean = self .. { equalsTupleType(aTupleType) }

	override fun o_EqualsTwoByteString(
		self: AvailObject,
		aString: A_String
	): Boolean = self .. { equalsTwoByteString(aString) }

	override fun o_SetExecutionState(
		self: AvailObject,
		value: ExecutionState
	) = self .. { executionState = value }

	override fun o_ExtractNybbleFromTupleAt(
		self: AvailObject,
		index: Int
	): Byte = self .. { extractNybbleFromTupleAt(index) }

	override fun o_FilterByTypes(
		self: AvailObject,
		argTypes: List<A_Type>
	): List<A_Definition> = self .. { filterByTypes(argTypes) }

	override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): AbstractNumberDescriptor.Order =
		self .. { numericCompareToInteger(anInteger) }

	override fun o_NumericCompareToInfinity(
		self: AvailObject,
		sign: Sign
	): AbstractNumberDescriptor.Order =
		self .. { numericCompareToInfinity(sign) }

	override fun o_HasElement(
		self: AvailObject,
		elementObject: A_BasicObject
	): Boolean = self .. { hasElement(elementObject) }

	override fun o_HashFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): Int = self .. { hashFromTo(startIndex, endIndex) }

	override fun o_SetHashOrZero(self: AvailObject, value: Int) =
		self .. { setHashOrZero(value) }

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean = self .. { hasObjectInstance(potentialInstance) }

	override fun o_DefinitionsAtOrBelow(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>
	): List<A_Definition> = self .. { definitionsAtOrBelow(argRestrictions) }

	override fun o_IncludesDefinition(
		self: AvailObject,
		definition: A_Definition
	): Boolean = self .. { includesDefinition(definition) }

	override fun o_SetInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	) = self .. { setInterruptRequestFlag(flag) }

	override fun o_CountdownToReoptimize(self: AvailObject, value: Long) =
		self .. { countdownToReoptimize(value) }

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject
	): Boolean = self .. { isBetterRepresentationThan(anotherObject) }

	override fun o_RepresentationCostOfTupleType(
		self: AvailObject
	): Int = self .. { representationCostOfTupleType() }

	override fun o_IsBinSubsetOf(
		self: AvailObject,
		potentialSuperset: A_Set
	): Boolean = self .. { isBinSubsetOf(potentialSuperset) }

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isInstanceOfKind(aType) }

	override fun o_IsSubsetOf(
		self: AvailObject,
		another: A_Set
	): Boolean = self .. { isSubsetOf(another) }

	override fun o_IsSubtypeOf(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isSubtypeOf(aType) }

	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): Boolean = self .. { isSupertypeOfVariableType(aVariableType) }

	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): Boolean = self .. {
		isSupertypeOfContinuationType(aContinuationType)
	}

	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): Boolean = self .. {
		isSupertypeOfCompiledCodeType(aCompiledCodeType)
	}

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isSupertypeOfFiberType(aType) }

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): Boolean = self .. { isSupertypeOfFunctionType(aFunctionType) }

	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): Boolean {
		return self .. { isSupertypeOfIntegerRangeType(anIntegerRangeType) }
	}

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): Boolean = self .. { isSupertypeOfListNodeType(aListNodeType) }

	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject
	): Boolean = self .. { isSupertypeOfMapType(aMapType) }

	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean = self .. { isSupertypeOfObjectType(anObjectType) }

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): Boolean = self .. { isSupertypeOfPhraseType(aPhraseType) }

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): Boolean = self .. { isSupertypeOfPojoType(aPojoType) }

	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types
	): Boolean = self .. {
		isSupertypeOfPrimitiveTypeEnum(primitiveTypeEnum)
	}

	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): Boolean = self .. { isSupertypeOfSetType(aSetType) }

	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): Boolean = self .. { isSupertypeOfTupleType(aTupleType) }

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_Type
	): Boolean = self .. { isSupertypeOfEnumerationType(anEnumerationType) }

	override fun o_Iterator(self: AvailObject): Iterator<AvailObject> =
		self .. { iterator() }

	override fun o_Spliterator(self: AvailObject): Spliterator<AvailObject> =
		self .. { spliterator() }

	override fun o_Stream(self: AvailObject): Stream<AvailObject> =
		self .. { stream() }

	override fun o_ParallelStream(self: AvailObject): Stream<AvailObject> =
		self .. { parallelStream() }

	override fun o_LiteralAt(self: AvailObject, index: Int): AvailObject =
		self .. { literalAt(index) }

	override fun o_FrameAt(
		self: AvailObject,
		index: Int
	): AvailObject = self .. { frameAt(index) }

	override fun o_FrameAtPut(
		self: AvailObject,
		index: Int,
		value: AvailObject
	): AvailObject = self .. { frameAtPut(index, value) }

	override fun o_LocalTypeAt(
		self: AvailObject,
		index: Int
	): A_Type = self .. { localTypeAt(index) }

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByTypesFromTuple(
		self: AvailObject,
		argumentTypeTuple: A_Tuple
	): A_Definition = self .. { lookupByTypesFromTuple(argumentTypeTuple) }

	@Throws(MethodDefinitionException::class)
	override fun o_LookupByValuesFromList(
		self: AvailObject,
		argumentList: List<A_BasicObject>
	): A_Definition = self .. { lookupByValuesFromList(argumentList) }

	override fun o_MapAtOrNull(
		self: AvailObject,
		keyObject: A_BasicObject
	): AvailObject? = self .. { mapAtOrNull(keyObject) }

	override fun o_MapAtPuttingCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map = self .. {
		mapAtPuttingCanDestroy(keyObject, newValueObject, canDestroy)
	}

	override fun o_MapAtReplacingCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		notFoundValue: A_BasicObject,
		canDestroy: Boolean,
		transformer: (AvailObject, AvailObject) -> A_BasicObject
	): A_Map = self.. {
		mapAtReplacingCanDestroy(key, notFoundValue, canDestroy, transformer)
	}

	override fun o_MapWithoutKeyCanDestroy(
		self: AvailObject,
		keyObject: A_BasicObject,
		canDestroy: Boolean
	): A_Map = self .. { mapWithoutKeyCanDestroy(keyObject, canDestroy) }

	override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { minusCanDestroy(aNumber, canDestroy) }

	override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. { multiplyByInfinityCanDestroy(sign, canDestroy) }

	override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		multiplyByIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_OptionallyNilOuterVar(
		self: AvailObject,
		index: Int
	): Boolean = self .. { optionallyNilOuterVar(index) }

	override fun o_OuterTypeAt(self: AvailObject, index: Int): A_Type =
		self .. { outerTypeAt(index) }

	override fun o_OuterVarAt(self: AvailObject, index: Int): AvailObject =
		self .. { outerVarAt(index) }

	override fun o_OuterVarAtPut(
		self: AvailObject,
		index: Int,
		value: AvailObject
	) = self .. { outerVarAtPut(index, value) }

	override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { plusCanDestroy(aNumber, canDestroy) }

	override fun o_SetPriority(
		self: AvailObject,
		value: Int
	) = self .. { priority = value }

	override fun o_SetFiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self .. { fiberGlobals = globals }

	override fun o_RawByteForCharacterAt(
		self: AvailObject,
		index: Int
	): Short = self .. { rawByteForCharacterAt(index) }

	override fun o_RawSignedIntegerAt(self: AvailObject, index: Int): Int =
		self .. { rawSignedIntegerAt(index) }

	override fun o_RawSignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self .. { rawSignedIntegerAtPut(index, value) }

	override fun o_RawUnsignedIntegerAt(
		self: AvailObject,
		index: Int
	): Long = self .. { rawUnsignedIntegerAt(index) }

	override fun o_RawUnsignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self .. { rawUnsignedIntegerAtPut(index, value) }

	override fun o_RemoveDependentChunk(
		self: AvailObject,
		chunk: L2Chunk
	) = self .. { removeDependentChunk(chunk) }

	override fun o_RemoveFrom(
		self: AvailObject,
		loader: AvailLoader,
		afterRemoval: () -> Unit) =
			self .. { removeFrom(loader, afterRemoval) }

	override fun o_RemoveDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self .. { removeDefinition(definition) }

	override fun o_RemoveGrammaticalRestriction(
		self: AvailObject,
		obsoleteRestriction: A_GrammaticalRestriction
	) = self .. { removeGrammaticalRestriction(obsoleteRestriction) }

	override fun o_ResolveForward(
		self: AvailObject,
		forwardDefinition: A_BasicObject
	) = self .. { resolveForward(forwardDefinition) }

	override fun o_SetIntersectionCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setIntersectionCanDestroy(otherSet, canDestroy) }

	override fun o_SetMinusCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setMinusCanDestroy(otherSet, canDestroy) }

	override fun o_SetUnionCanDestroy(
		self: AvailObject,
		otherSet: A_Set,
		canDestroy: Boolean
	): A_Set = self .. { setUnionCanDestroy(otherSet, canDestroy) }

	@Throws(VariableSetException::class)
	override fun o_SetValue(
		self: AvailObject,
		newValue: A_BasicObject
	) = self .. { setValue(newValue) }

	override fun o_SetValueNoCheck(
		self: AvailObject,
		newValue: A_BasicObject
	) = self .. { setValueNoCheck(newValue) }

	override fun o_SetWithElementCanDestroy(
		self: AvailObject,
		newElementObject: A_BasicObject,
		canDestroy: Boolean
	): A_Set = self .. {
		setWithElementCanDestroy(newElementObject, canDestroy)
	}

	override fun o_SetWithoutElementCanDestroy(
		self: AvailObject,
		elementObjectToExclude: A_BasicObject,
		canDestroy: Boolean
	): A_Set = self .. {
		setWithoutElementCanDestroy(elementObjectToExclude, canDestroy)
	}

	override fun o_StackAt(self: AvailObject, slotIndex: Int): AvailObject =
		self .. { stackAt(slotIndex) }

	override fun o_SetStartingChunkAndReoptimizationCountdown(
		self: AvailObject,
		chunk: L2Chunk,
		countdown: Long
	) = self .. {
		setStartingChunkAndReoptimizationCountdown(chunk, countdown)
	}

	override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = self .. {
		subtractFromInfinityCanDestroy(sign, canDestroy)
	}

	override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number = self .. {
		subtractFromIntegerCanDestroy(anInteger, canDestroy)
	}

	override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { timesCanDestroy(aNumber, canDestroy) }

	override fun o_TrueNamesForStringName(
		self: AvailObject,
		stringName: A_String
	): A_Set = self .. { trueNamesForStringName(stringName) }

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject =
		self .. { tupleAt(index) }

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Tuple = self .. {
		tupleAtPuttingCanDestroy(index, newValueObject, canDestroy)
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		self .. { tupleIntAt(index) }

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long =
		self .. { tupleLongAt(index) }

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type =
		self .. { typeAtIndex(index) }

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type
	): A_Type = self .. { typeIntersection(another) }

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): A_Type = self .. {
		typeIntersectionOfCompiledCodeType(aCompiledCodeType)
	}

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): A_Type = self .. {
		typeIntersectionOfContinuationType(aContinuationType)
	}

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): A_Type = self .. { typeIntersectionOfFiberType(aFiberType) }

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): A_Type = self .. { typeIntersectionOfFunctionType(aFunctionType) }

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): A_Type = self .. {
		typeIntersectionOfIntegerRangeType(anIntegerRangeType)
	}

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): A_Type = self .. { typeIntersectionOfListNodeType(aListNodeType) }

	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type
	): A_Type = self .. { typeIntersectionOfMapType(aMapType) }

	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type = self .. { typeIntersectionOfObjectType(anObjectType) }

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): A_Type = self .. { typeIntersectionOfPhraseType(aPhraseType) }

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): A_Type = self .. { typeIntersectionOfPojoType(aPojoType) }

	override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): A_Type = self .. { typeIntersectionOfSetType(aSetType) }

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): A_Type = self .. { typeIntersectionOfTupleType(aTupleType) }

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): A_Type = self .. { typeIntersectionOfVariableType(aVariableType) }

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type
	): A_Type = self .. { typeUnion(another) }

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type
	): A_Type = self .. { typeUnionOfFiberType(aFiberType) }

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type
	): A_Type = self .. { typeUnionOfFunctionType(aFunctionType) }

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type
	): A_Type = self .. { typeUnionOfVariableType(aVariableType) }

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type
	): A_Type = self .. { typeUnionOfContinuationType(aContinuationType) }

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type
	): A_Type = self .. { typeUnionOfCompiledCodeType(aCompiledCodeType) }

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type
	): A_Type = self .. { typeUnionOfIntegerRangeType(anIntegerRangeType) }

	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type
	): A_Type = self .. { typeUnionOfMapType(aMapType) }

	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): A_Type = self .. { typeUnionOfObjectType(anObjectType) }

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): A_Type = self .. { typeUnionOfPhraseType(aPhraseType) }

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoType(aPojoType) }

	override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type
	): A_Type = self .. { typeUnionOfSetType(aSetType) }

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type
	): A_Type = self .. { typeUnionOfTupleType(aTupleType) }

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Type = self .. { unionOfTypesAtThrough(startIndex, endIndex) }

	override fun o_AsNativeString(self: AvailObject): String =
		self .. { asNativeString() }

	override fun o_AsSet(self: AvailObject): A_Set =
		self .. { asSet }

	override fun o_AsTuple(self: AvailObject): A_Tuple =
		self .. { asTuple }

	override fun o_BitsPerEntry(self: AvailObject): Int =
		self .. { bitsPerEntry }

	override fun o_BodyBlock(self: AvailObject): A_Function =
		self .. { bodyBlock() }

	override fun o_BodySignature(self: AvailObject): A_Type =
		self .. { bodySignature() }

	override fun o_Caller(self: AvailObject): A_Continuation =
		self .. { caller() }

	override fun o_ClearValue(self: AvailObject) =
		self .. { clearValue() }

	override fun o_Function(self: AvailObject): A_Function =
		self .. { function() }

	override fun o_FunctionType(self: AvailObject): A_Type =
		self .. { functionType() }

	override fun o_Code(self: AvailObject): A_RawFunction =
		self .. { code() }

	override fun o_CodePoint(self: AvailObject): Int =
		self .. { codePoint }

	override fun o_LazyComplete(self: AvailObject): A_Set =
		self .. { lazyComplete }

	override fun o_ConstantBindings(self: AvailObject): A_Map =
		self .. { constantBindings }

	override fun o_ContentType(self: AvailObject): A_Type =
		self .. { contentType }

	override fun o_Continuation(self: AvailObject): A_Continuation =
		self .. { continuation }

	override fun o_CopyAsMutableIntTuple(self: AvailObject): A_Tuple =
		self .. { copyAsMutableIntTuple() }

	override fun o_CopyAsMutableLongTuple(self: AvailObject): A_Tuple =
		self .. { copyAsMutableLongTuple() }

	override fun o_CopyAsMutableObjectTuple(self: AvailObject): A_Tuple =
		self .. { copyAsMutableObjectTuple() }

	override fun o_DefaultType(self: AvailObject): A_Type =
		self .. { defaultType }

	override fun o_EnsureMutable(self: AvailObject): A_Continuation =
		self .. { ensureMutable() }

	override fun o_ExecutionState(self: AvailObject): ExecutionState =
		self .. { executionState }

	override fun o_Expand(self: AvailObject, module: A_Module) =
		self .. { expand(module) }

	override fun o_ExtractBoolean(self: AvailObject): Boolean =
		self .. { extractBoolean }

	override fun o_ExtractUnsignedByte(self: AvailObject): Short =
		self .. { extractUnsignedByte }

	override fun o_ExtractDouble(self: AvailObject): Double =
		self .. { extractDouble }

	override fun o_ExtractFloat(self: AvailObject): Float =
		self .. { extractFloat }

	override fun o_ExtractInt(self: AvailObject): Int =
		self .. { extractInt }

	override fun o_ExtractLong(self: AvailObject): Long =
		self .. { extractLong }

	override fun o_ExtractNybble(self: AvailObject): Byte =
		self .. { extractNybble }

	override fun o_FieldMap(self: AvailObject): A_Map =
		self .. { fieldMap() }

	override fun o_FieldTypeMap(self: AvailObject): A_Map =
		self .. { fieldTypeMap }

	@Throws(VariableGetException::class)
	override fun o_GetValue(self: AvailObject): AvailObject =
		self .. { getValue() }

	@Throws(VariableGetException::class)
	override fun o_GetValueClearing(self: AvailObject): AvailObject =
		self .. { getValueClearing() }

	override fun o_Hash(self: AvailObject): Int =
		self .. { hash() }

	override fun o_HashOrZero(self: AvailObject): Int =
		self .. { hashOrZero() }

	override fun o_HasGrammaticalRestrictions(self: AvailObject): Boolean =
		self .. { hasGrammaticalRestrictions }

	override fun o_DefinitionsTuple(self: AvailObject): A_Tuple =
		self .. { definitionsTuple }

	override fun o_LazyIncomplete(self: AvailObject): A_Map =
		self .. { lazyIncomplete }

	override fun o_DecrementCountdownToReoptimize(
		self: AvailObject,
		continuation: (Boolean)->Unit
	) = self .. { decrementCountdownToReoptimize(continuation) }

	override fun o_DecreaseCountdownToReoptimizeFromPoll (
		self: AvailObject,
		delta: Long
	) = self .. { decreaseCountdownToReoptimizeFromPoll(delta) }

	override fun o_IsAbstractDefinition(self: AvailObject): Boolean =
		self .. { isAbstractDefinition() }

	override fun o_IsAbstract(self: AvailObject): Boolean =
		self .. { isAbstract }

	override fun o_IsBoolean(self: AvailObject): Boolean =
		self .. { isBoolean }

	override fun o_IsUnsignedByte(self: AvailObject): Boolean =
		self .. { isUnsignedByte }

	/**
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	override fun o_IsByteTuple(self: AvailObject): Boolean =
		self .. { isByteTuple }

	override fun o_IsCharacter(self: AvailObject): Boolean =
		self .. { isCharacter }

	override fun o_IsFunction(self: AvailObject): Boolean =
		self .. { isFunction }

	override fun o_IsAtom(self: AvailObject): Boolean =
		self .. { isAtom }

	override fun o_IsExtendedInteger(self: AvailObject): Boolean =
		self .. { isExtendedInteger }

	override fun o_IsFinite(self: AvailObject): Boolean =
		self .. { isFinite }

	override fun o_IsForwardDefinition(self: AvailObject): Boolean =
		self .. { isForwardDefinition() }

	override fun o_IsInstanceMeta(self: AvailObject): Boolean =
		self .. { isInstanceMeta }

	override fun o_IsMethodDefinition(self: AvailObject): Boolean =
		self .. { isMethodDefinition() }

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean =
		self .. { isIntegerRangeType }

	override fun o_IsMap(self: AvailObject): Boolean =
		self .. { isMap }

	override fun o_IsMapType(self: AvailObject): Boolean =
		self .. { isMapType }

	override fun o_IsNybble(self: AvailObject): Boolean =
		self .. { isNybble }

	override fun o_IsPositive(self: AvailObject): Boolean =
		self .. { isPositive }

	override fun o_IsSet(self: AvailObject): Boolean =
		self .. { isSet }

	override fun o_IsSetType(self: AvailObject): Boolean =
		self .. { isSetType }

	override fun o_IsString(self: AvailObject): Boolean =
		self .. { isString }

	override fun o_IsTuple(self: AvailObject): Boolean =
		self .. { isTuple }

	override fun o_IsTupleType(self: AvailObject): Boolean =
		self .. { isTupleType }

	override fun o_IsType(self: AvailObject): Boolean =
		self .. { isType }

	override fun o_KeysAsSet(self: AvailObject): A_Set =
		self .. { keysAsSet }

	override fun o_KeyType(self: AvailObject): A_Type =
		self .. { keyType }

	override fun o_LevelTwoChunk(self: AvailObject): L2Chunk =
		self .. { levelTwoChunk() }

	override fun o_LevelTwoOffset(self: AvailObject): Int =
		self .. { levelTwoOffset() }

	override fun o_Literal(self: AvailObject): AvailObject =
		self .. { literal() }

	override fun o_LowerBound(self: AvailObject): A_Number =
		self .. { lowerBound }

	override fun o_LowerInclusive(self: AvailObject): Boolean =
		self .. { lowerInclusive }

	override fun o_MakeSubobjectsImmutable(self: AvailObject): AvailObject =
		self .. { makeSubobjectsImmutable() }

	override fun o_MakeSubobjectsShared(self: AvailObject): AvailObject =
		self .. { makeSubobjectsShared() }

	override fun o_MapSize(self: AvailObject): Int =
		self .. { mapSize }

	override fun o_MaxStackDepth(self: AvailObject): Int =
		self .. { maxStackDepth }

	override fun o_Message(self: AvailObject): A_Atom =
		self .. { message }

	override fun o_MessagePart (self: AvailObject, index: Int): A_String =
		self .. { messagePart(index) }

	override fun o_MessageParts(self: AvailObject): A_Tuple =
		self .. { messageParts }

	override fun o_MethodDefinitions(self: AvailObject): A_Set =
		self .. { methodDefinitions }

	override fun o_ImportedNames(self: AvailObject): A_Map =
		self .. { importedNames }

	override fun o_NewNames(self: AvailObject): A_Map =
		self .. { newNames }

	override fun o_NumArgs(self: AvailObject): Int =
		self .. { numArgs }

	override fun o_NumSlots(self: AvailObject): Int =
		self .. { numSlots() }

	override fun o_NumLiterals(self: AvailObject): Int =
		self .. { numLiterals }

	override fun o_NumLocals(self: AvailObject): Int =
		self .. { numLocals }

	override fun o_NumOuters(self: AvailObject): Int =
		self .. { numOuters }

	override fun o_NumOuterVars(self: AvailObject): Int =
		self .. { numOuterVars }

	override fun o_Nybbles(self: AvailObject): A_Tuple =
		self .. { nybbles }

	override fun o_Parent(self: AvailObject): A_BasicObject =
		self .. { parent }

	override fun o_Pc(self: AvailObject): Int =
		self .. { pc() }

	override fun o_Priority(self: AvailObject): Int =
		self .. { priority }

	override fun o_PrivateNames(self: AvailObject): A_Map =
		self .. { privateNames }

	override fun o_FiberGlobals(self: AvailObject): A_Map =
		self .. { fiberGlobals }

	override fun o_GrammaticalRestrictions(self: AvailObject): A_Set =
		self .. { grammaticalRestrictions }

	override fun o_ReturnType(self: AvailObject): A_Type =
		self .. { returnType }

	override fun o_SetBinHash(self: AvailObject): Int =
		self .. { setBinHash }

	override fun o_SetBinSize(self: AvailObject): Int =
		self .. { setBinSize }

	override fun o_SetSize(self: AvailObject): Int =
		self .. { setSize }

	override fun o_SizeRange(self: AvailObject): A_Type =
		self .. { sizeRange }

	override fun o_LazyActions(self: AvailObject): A_Map =
		self .. { lazyActions }

	override fun o_Stackp(self: AvailObject): Int =
		self .. { stackp() }

	override fun o_Start(self: AvailObject): Int =
		self .. { start() }

	override fun o_StartingChunk(self: AvailObject): L2Chunk =
		self .. { startingChunk }

	override fun o_String(self: AvailObject): A_String =
		self .. { string() }

	override fun o_TokenType(self: AvailObject): TokenDescriptor.TokenType =
		self .. { tokenType() }

	override fun o_TrimExcessInts(self: AvailObject) =
		self .. { trimExcessInts() }

	override fun o_TupleReverse(self: AvailObject): A_Tuple =
		self .. { tupleReverse() }

	override fun o_TupleSize(self: AvailObject): Int =
		self .. { tupleSize }

	override fun o_Kind(self: AvailObject): A_Type =
		self .. { kind() }

	override fun o_TypeTuple(self: AvailObject): A_Tuple =
		self .. { typeTuple }

	override fun o_UpperBound(self: AvailObject): A_Number =
		self .. { upperBound }

	override fun o_UpperInclusive(self: AvailObject): Boolean =
		self .. { upperInclusive }

	override fun o_Value(self: AvailObject): AvailObject =
		self .. { value() }

	override fun o_ValuesAsTuple(self: AvailObject): A_Tuple =
		self .. { valuesAsTuple }

	override fun o_ValueType(self: AvailObject): A_Type =
		self .. { valueType }

	override fun o_VariableBindings(self: AvailObject): A_Map =
		self .. { variableBindings }

	override fun o_VisibleNames(self: AvailObject): A_Set =
		self .. { visibleNames }

	override fun o_ParsingInstructions(self: AvailObject): A_Tuple =
		self .. { parsingInstructions }

	override fun o_Expression(self: AvailObject): A_Phrase =
		self .. { expression }

	override fun o_Sequence (self: AvailObject): A_Phrase =
		self .. { sequence }

	override fun o_Variable(self: AvailObject): A_Phrase =
		self .. { variable }

	override fun o_ArgumentsTuple(self: AvailObject): A_Tuple =
		self .. { argumentsTuple }

	override fun o_StatementsTuple(self: AvailObject): A_Tuple =
		self .. { statementsTuple }

	override fun o_ResultType(self: AvailObject): A_Type =
		self .. { resultType() }

	override fun o_NeededVariables(
		self: AvailObject,
		neededVariables: A_Tuple
	) = self .. { this.neededVariables = neededVariables }

	override fun o_NeededVariables(self: AvailObject): A_Tuple =
		self .. { neededVariables }

	override fun o_Primitive(self: AvailObject): Primitive? =
		self .. { codePrimitive() }

	override fun o_DeclaredType(self: AvailObject): A_Type =
		self .. { declaredType }

	override fun o_DeclarationKind(self: AvailObject): DeclarationKind =
		self .. { declarationKind() }

	override fun o_TypeExpression(self: AvailObject): A_Phrase =
		self .. { typeExpression }

	override fun o_InitializationExpression(self: AvailObject): AvailObject =
		self .. { initializationExpression }

	override fun o_LiteralObject(self: AvailObject): A_BasicObject =
		self .. { literalObject }

	override fun o_Token(self: AvailObject): A_Token =
		self .. { token }

	override fun o_MarkerValue(self: AvailObject): A_BasicObject =
		self .. { markerValue }

	override fun o_ArgumentsListNode(self: AvailObject): A_Phrase =
		self .. { argumentsListNode }

	override fun o_Bundle(self: AvailObject): A_Bundle =
		self .. { bundle }

	override fun o_ExpressionsTuple(self: AvailObject): A_Tuple =
		self .. { expressionsTuple }

	override fun o_Declaration(self: AvailObject): A_Phrase =
		self .. { declaration }

	override fun o_PhraseExpressionType(self: AvailObject): A_Type =
		self .. { phraseExpressionType }

	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type =
		self .. { phraseTypeExpressionType }

	override fun o_EmitEffectOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitEffectOn(codeGenerator) }

	override fun o_EmitValueOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitValueOn(codeGenerator) }

	override fun o_ChildrenMap(
		self: AvailObject,
		transformer: (A_Phrase)->A_Phrase
	) = self .. { childrenMap(transformer) }

	override fun o_ChildrenDo(
		self: AvailObject,
		action: (A_Phrase)->Unit
	) = self .. { childrenDo(action) }

	override fun o_ValidateLocally(
		self: AvailObject,
		parent: A_Phrase?
	) = self .. { validateLocally(parent) }

	override fun o_GenerateInModule(
		self: AvailObject,
		module: A_Module
	): A_RawFunction = self .. { generateInModule(module) }

	override fun o_CopyWith(
		self: AvailObject,
		newPhrase: A_Phrase
	): A_Phrase = self .. { copyWith(newPhrase) }

	override fun o_CopyConcatenating(
		self: AvailObject,
		newListPhrase: A_Phrase
	): A_Phrase = self .. { copyConcatenating(newListPhrase) }

	override fun o_IsLastUse(
		self: AvailObject,
		isLastUse: Boolean
	) = self .. {
		this.isLastUse = isLastUse
	}

	override fun o_IsLastUse(self: AvailObject): Boolean =
		self .. { isLastUse }

	override fun o_CopyMutablePhrase(self: AvailObject): A_Phrase =
		self .. { copyMutablePhrase() }

	override fun o_BinUnionKind(self: AvailObject): A_Type =
		self .. { binUnionKind }

	override fun o_OutputPhrase(self: AvailObject): A_Phrase =
		self .. { outputPhrase }

	override fun o_ApparentSendName(self: AvailObject): A_Atom =
		self .. { apparentSendName }

	override fun o_Statements(self: AvailObject): A_Tuple =
		self .. { statements }

	override fun o_FlattenStatementsInto(
		self: AvailObject,
		accumulatedStatements: MutableList<A_Phrase>
	) = self .. { flattenStatementsInto(accumulatedStatements) }

	override fun o_LineNumber(self: AvailObject): Int =
		self .. { lineNumber() }

	override fun o_AllParsingPlansInProgress(self: AvailObject): A_Map =
		self .. { allParsingPlansInProgress }

	override fun o_IsSetBin(self: AvailObject): Boolean =
		self .. { isSetBin }

	override fun o_MapIterable(
		self: AvailObject
	): Iterable<MapDescriptor.Entry> =
		self .. { mapIterable }

	override fun o_DeclaredExceptions(self: AvailObject): A_Set =
		self .. { declaredExceptions }

	override fun o_IsInt(self: AvailObject): Boolean =
		self .. { isInt }

	override fun o_IsLong(self: AvailObject): Boolean =
		self .. { isLong }

	override fun o_ArgsTupleType(self: AvailObject): A_Type =
		self .. { argsTupleType }

	override fun o_EqualsInstanceTypeFor(
		self: AvailObject,
		anObject: AvailObject
	): Boolean = self .. { equalsInstanceTypeFor(anObject) }

	override fun o_Instances(self: AvailObject): A_Set =
		self .. { instances }

	override fun o_EqualsEnumerationWithSet(
		self: AvailObject,
		aSet: A_Set
	): Boolean = self .. { equalsEnumerationWithSet(aSet) }

	override fun o_IsEnumeration(self: AvailObject): Boolean =
		self .. { isEnumeration }

	override fun o_IsInstanceOf(
		self: AvailObject,
		aType: A_Type
	): Boolean = self .. { isInstanceOf(aType) }

	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject
	): Boolean =
		self .. { enumerationIncludesInstance(potentialInstance) }

	override fun o_ComputeSuperkind(self: AvailObject): A_Type =
		self .. { computeSuperkind() }

	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject
	) = self .. { setAtomProperty(key, value) }

	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom
	): AvailObject = self .. { getAtomProperty(key) }

	override fun o_EqualsEnumerationType(
		self: AvailObject,
		another: A_BasicObject
	): Boolean = self .. { equalsEnumerationType(another) }

	override fun o_ReadType(self: AvailObject): A_Type =
		self .. { readType }

	override fun o_WriteType(self: AvailObject): A_Type =
		self .. { writeType }

	override fun o_Versions(self: AvailObject): A_Set =
		self .. { versions }

	override fun o_EqualsPhraseType(
		self: AvailObject,
		aPhraseType: A_Type
	): Boolean = self .. { equalsPhraseType(aPhraseType) }

	override fun o_PhraseKind(self: AvailObject): PhraseKind =
		self .. { phraseKind }

	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind
	): Boolean = self .. { phraseKindIsUnder(expectedPhraseKind) }

	override fun o_IsRawPojo(self: AvailObject): Boolean =
		self .. { isRawPojo }

	override fun o_AddSemanticRestriction(
		self: AvailObject,
		restriction: A_SemanticRestriction
	) = self .. { addSemanticRestriction(restriction) }

	override fun o_RemoveSemanticRestriction(
		self: AvailObject,
		restriction: A_SemanticRestriction
	) = self .. { removeSemanticRestriction(restriction) }

	override fun o_SemanticRestrictions(
		self: AvailObject
	): A_Set = self .. { semanticRestrictions }

	override fun o_AddSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = self .. { addSealedArgumentsType(typeTuple) }

	override fun o_RemoveSealedArgumentsType(
		self: AvailObject,
		typeTuple: A_Tuple
	) = self .. { removeSealedArgumentsType(typeTuple) }

	override fun o_SealedArgumentsTypesTuple(
		self: AvailObject
	): A_Tuple = self .. { sealedArgumentsTypesTuple }

	override fun o_ModuleAddSemanticRestriction(
		self: AvailObject,
		semanticRestriction: A_SemanticRestriction
	) = self .. { moduleAddSemanticRestriction(semanticRestriction) }

	override fun o_AddConstantBinding(
		self: AvailObject,
		name: A_String,
		constantBinding: A_Variable
	) = self .. { addConstantBinding(name, constantBinding) }

	override fun o_AddVariableBinding(
		self: AvailObject,
		name: A_String,
		variableBinding: A_Variable
	) = self .. { addVariableBinding(name, variableBinding) }

	override fun o_IsMethodEmpty(
		self: AvailObject
	): Boolean = self .. { isMethodEmpty }

	override fun o_IsPojoSelfType(self: AvailObject): Boolean =
		self .. { isPojoSelfType }

	override fun o_PojoSelfType(self: AvailObject): A_Type =
		self .. { pojoSelfType() }

	override fun o_JavaClass(self: AvailObject): AvailObject =
		self .. { javaClass() }

	override fun o_IsUnsignedShort(self: AvailObject): Boolean =
		self .. { isUnsignedShort }

	override fun o_ExtractUnsignedShort(self: AvailObject): Int =
		self .. { extractUnsignedShort }

	override fun o_IsFloat(self: AvailObject): Boolean =
		self .. { isFloat }

	override fun o_IsDouble(self: AvailObject): Boolean =
		self .. { isDouble }

	override fun o_RawPojo(self: AvailObject): AvailObject =
		self .. { rawPojo() }

	override fun o_IsPojo(self: AvailObject): Boolean =
		self .. { isPojo }

	override fun o_IsPojoType(self: AvailObject): Boolean =
		self .. { isPojoType }

	override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number): AbstractNumberDescriptor.Order =
		self .. { numericCompare(another) }

	override fun o_NumericCompareToDouble(
		self: AvailObject,
		aDouble: Double): AbstractNumberDescriptor.Order =
		self .. { numericCompareToDouble(aDouble) }

	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number = self .. {
		addToDoubleCanDestroy(doubleObject, canDestroy)
	}

	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { addToFloatCanDestroy(floatObject, canDestroy) }

	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { subtractFromDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { subtractFromFloatCanDestroy(floatObject, canDestroy) }

	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { multiplyByDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { multiplyByFloatCanDestroy(floatObject, canDestroy) }

	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { divideIntoDoubleCanDestroy(doubleObject, canDestroy) }

	override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number =
		self .. { divideIntoFloatCanDestroy(floatObject, canDestroy) }

	override fun o_LazyPrefilterMap(self: AvailObject): A_Map =
		self .. { lazyPrefilterMap }

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		self .. { serializerOperation() }

	override fun o_MapBinAtHashPutLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin = self.. {
		mapBinAtHashPutLevelCanDestroy(key, keyHash, value, myLevel, canDestroy)
	}

	override fun o_MapBinRemoveKeyHashCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	): A_MapBin =
		self .. { mapBinRemoveKeyHashCanDestroy(key, keyHash, canDestroy) }

	override fun o_MapBinAtHashReplacingLevelCanDestroy(
		self: AvailObject,
		key: AvailObject,
		keyHash: Int,
		notFoundValue: AvailObject,
		myLevel: Int,
		canDestroy: Boolean,
		transformer: (AvailObject, AvailObject) -> A_BasicObject
	): A_MapBin = self.. {
		mapBinAtHashReplacingLevelCanDestroy(
			key, keyHash, notFoundValue, myLevel, canDestroy, transformer)
	}

	override fun o_MapBinSize(self: AvailObject): Int =
		self .. { mapBinSize }

	override fun o_MapBinKeyUnionKind(self: AvailObject): A_Type =
		self .. { mapBinKeyUnionKind }

	override fun o_MapBinValueUnionKind(self: AvailObject): A_Type =
		self .. { mapBinValueUnionKind }

	override fun o_IsHashedMapBin(self: AvailObject): Boolean =
		self .. { isHashedMapBin }

	override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject? = self .. { mapBinAtHash(key, keyHash) }

	override fun o_MapBinKeysHash(
		self: AvailObject
	): Int = self .. { mapBinKeysHash }

	override fun o_MapBinValuesHash(self: AvailObject): Int =
		self .. { mapBinValuesHash }

	override fun o_IssuingModule(
		self: AvailObject
	): A_Module = self .. { issuingModule }

	override fun o_IsPojoFusedType(self: AvailObject): Boolean =
		self .. { isPojoFusedType }

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type
	): Boolean = self .. { isSupertypeOfPojoBottomType(aPojoType) }

	override fun o_EqualsPojoBottomType(self: AvailObject): Boolean =
		self .. { equalsPojoBottomType() }

	override fun o_JavaAncestors(self: AvailObject): AvailObject =
		self .. { javaAncestors() }

	override fun o_TypeIntersectionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = self .. { typeIntersectionOfPojoFusedType(aFusedPojoType) }

	override fun o_TypeIntersectionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type =
		self .. { typeIntersectionOfPojoUnfusedType(anUnfusedPojoType) }

	override fun o_TypeUnionOfPojoFusedType(
		self: AvailObject,
		aFusedPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoFusedType(aFusedPojoType) }

	override fun o_TypeUnionOfPojoUnfusedType(
		self: AvailObject,
		anUnfusedPojoType: A_Type
	): A_Type = self .. { typeUnionOfPojoUnfusedType(anUnfusedPojoType) }

	override fun o_IsPojoArrayType(self: AvailObject): Boolean =
		self .. { isPojoArrayType }

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = self .. { marshalToJava(classHint) }

	override fun o_TypeVariables(self: AvailObject): A_Map =
		self .. { typeVariables }

	override fun o_EqualsPojoField(
		self: AvailObject,
		field: AvailObject,
		receiver: AvailObject
	): Boolean = self .. { equalsPojoField(field, receiver) }

	override fun o_IsSignedByte(self: AvailObject): Boolean =
		self .. { isSignedByte }

	override fun o_IsSignedShort(self: AvailObject): Boolean =
		self .. { isSignedShort }

	override fun o_ExtractSignedByte(self: AvailObject): Byte =
		self .. { extractSignedByte }

	override fun o_ExtractSignedShort(self: AvailObject): Short =
		self .. { extractSignedShort }

	override fun o_EqualsEqualityRawPojo(
		self: AvailObject,
		otherEqualityRawPojo: AvailObject,
		otherJavaObject: Any?
	): Boolean = self .. { equalsEqualityRawPojoFor(self, otherJavaObject) }

	override fun <T : Any> o_JavaObject(self: AvailObject): T? =
		self .. { javaObject() }

	override fun o_AsBigInteger(
		self: AvailObject
	): BigInteger = self .. { asBigInteger() }

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean
	): A_Tuple = self .. { appendCanDestroy(newElement, canDestroy) }

	override fun o_LazyIncompleteCaseInsensitive(
		self: AvailObject
	): A_Map = self .. { lazyIncompleteCaseInsensitive }

	override fun o_LowerCaseString(self: AvailObject): A_String =
		self .. { lowerCaseString() }

	override fun o_InstanceCount(self: AvailObject): A_Number =
		self .. { instanceCount }

	override fun o_TotalInvocations(self: AvailObject): Long =
		self .. { totalInvocations }

	override fun o_TallyInvocation(self: AvailObject) =
		self .. { tallyInvocation() }

	override fun o_FieldTypeTuple(self: AvailObject): A_Tuple =
		self .. { fieldTypeTuple }

	override fun o_FieldTuple(self: AvailObject): A_Tuple =
		self .. { fieldTuple() }

	override fun o_LiteralType(self: AvailObject): A_Type =
		self .. { literalType }

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): A_Type = self .. { typeIntersectionOfTokenType(aTokenType) }

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): A_Type =
		self .. { typeIntersectionOfLiteralTokenType(aLiteralTokenType) }

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): A_Type = self .. { typeUnionOfTokenType(aTokenType) }

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): A_Type = self .. { typeUnionOfLiteralTokenType(aLiteralTokenType) }

	override fun o_IsTokenType(self: AvailObject): Boolean =
		self .. { isTokenType }

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean =
		self .. { isLiteralTokenType }

	override fun o_IsLiteralToken(self: AvailObject): Boolean =
		self .. { isLiteralToken() }

	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): Boolean = self .. { isSupertypeOfTokenType(aTokenType) }

	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): Boolean =
		self .. { isSupertypeOfLiteralTokenType(aLiteralTokenType) }

	override fun o_EqualsTokenType(
		self: AvailObject,
		aTokenType: A_Type
	): Boolean = self .. { equalsTokenType(aTokenType) }

	override fun o_EqualsLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type
	): Boolean = self .. { equalsLiteralTokenType(aLiteralTokenType) }

	override fun o_EqualsObjectType(
		self: AvailObject,
		anObjectType: AvailObject
	): Boolean = self .. { equalsObjectType(anObjectType) }

	override fun o_EqualsToken(
		self: AvailObject,
		aToken: A_Token
	): Boolean = self .. { equalsToken(aToken) }

	override fun o_BitwiseAnd(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseAnd(anInteger, canDestroy) }

	override fun o_BitwiseOr(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseOr(anInteger, canDestroy) }

	override fun o_BitwiseXor(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitwiseXor(anInteger, canDestroy) }

	override fun o_BitTest(
		self: AvailObject,
		bitPosition: Int
	): Boolean = self .. { bitTest(bitPosition) }

	override fun o_BitSet(
		self: AvailObject,
		bitPosition: Int,
		value: Boolean,
		canDestroy: Boolean
	) : A_Number = self .. { bitSet(bitPosition, value, canDestroy) }

	override fun o_AddSeal(
		self: AvailObject,
		methodName: A_Atom,
		argumentTypes: A_Tuple
	) = self .. { addSeal(methodName, argumentTypes) }

	override fun o_Instance(
		self: AvailObject
	): AvailObject = self .. { instance }

	override fun o_SetMethodName(
		self: AvailObject,
		methodName: A_String
	) = self .. { this.methodName = methodName }

	override fun o_StartingLineNumber(
		self: AvailObject
	): Int = self .. { codeStartingLineNumber }

	override fun o_Module(self: AvailObject): A_Module =
		self .. { module }

	override fun o_MethodName(self: AvailObject): A_String =
		self .. { methodName }

	override fun o_NameForDebugger(self: AvailObject): String =
		"IND" + mutability.suffix + "→" + (self .. { nameForDebugger() })

	override fun o_BinElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: A_Type
	): Boolean = self .. { binElementsAreAllInstancesOfKind(kind) }

	override fun o_SetElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: AvailObject
	): Boolean = self .. { setElementsAreAllInstancesOfKind(kind) }

	override fun o_MapBinIterator(
		self: AvailObject
	): MapIterator = self .. { mapBinIterator }

	override fun o_RangeIncludesLong(
		self: AvailObject,
		aLong: Long
	): Boolean = self .. { rangeIncludesLong(aLong) }

	override fun o_BitShiftLeftTruncatingToBits(
		self: AvailObject,
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	): A_Number = self .. {
		bitShiftLeftTruncatingToBits(shiftFactor, truncationBits, canDestroy)
	}

	override fun o_SetBinIterator(
		self: AvailObject
	): SetIterator = self .. { setBinIterator }

	override fun o_BitShift(
		self: AvailObject,
		shiftFactor: A_Number,
		canDestroy: Boolean
	): A_Number = self .. { bitShift(shiftFactor, canDestroy) }

	override fun o_EqualsPhrase(
		self: AvailObject,
		aPhrase: A_Phrase
	): Boolean = self .. { equalsPhrase(aPhrase) }

	override fun o_StripMacro(
		self: AvailObject
	): A_Phrase = self .. { stripMacro }

	override fun o_DefinitionMethod(
		self: AvailObject
	): A_Method = self .. { definitionMethod() }

	override fun o_PrefixFunctions(
		self: AvailObject
	): A_Tuple = self .. { prefixFunctions() }

	override fun o_EqualsByteArrayTuple(
		self: AvailObject,
		aByteArrayTuple: A_Tuple
	): Boolean = self .. { equalsByteArrayTuple(aByteArrayTuple) }

	override fun o_CompareFromToWithByteArrayTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteArrayTupleStartingAt(
			startIndex1, endIndex1, aByteArrayTuple, startIndex2)
	}

	override fun o_ByteArray(self: AvailObject): ByteArray =
		self .. { byteArray }

	override fun o_IsByteArrayTuple(self: AvailObject): Boolean =
		self .. { isByteArrayTuple }

	override fun o_UpdateForNewGrammaticalRestriction(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress,
		treesToVisit: Deque<Pair<A_BundleTree, A_ParsingPlanInProgress>>
	) = self .. {
		updateForNewGrammaticalRestriction(planInProgress, treesToVisit)
	}

	override fun <T> o_Lock(self: AvailObject, body: () -> T): T =
		self .. { lock(body) }

	override fun o_ModuleName(self: AvailObject): A_String =
		self .. { moduleName }

	override fun o_ShortModuleNameNative(self: AvailObject): String =
		self .. { shortModuleNameNative }

	override fun o_BundleMethod(self: AvailObject): A_Method =
		self .. { bundleMethod }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_GetAndSetValue(
		self: AvailObject,
		newValue: A_BasicObject
	): AvailObject = self .. { getAndSetValue(newValue) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_CompareAndSwapValues(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject
	): Boolean = self .. { compareAndSwapValues(reference, newValue) }

	@Throws(VariableSetException::class)
	override fun o_CompareAndSwapValuesNoCheck(
		self: AvailObject,
		reference: A_BasicObject,
		newValue: A_BasicObject
	): Boolean = self .. { compareAndSwapValuesNoCheck(reference, newValue) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_FetchAndAddValue(
		self: AvailObject,
		addend: A_Number
	): A_Number = self .. { fetchAndAddValue(addend) }

	override fun o_FailureContinuation(
		self: AvailObject
	): (Throwable)->Unit =
		self .. { failureContinuation }

	override fun o_ResultContinuation(
		self: AvailObject
	): (AvailObject)->Unit =
		self .. { resultContinuation }

	override fun o_AvailLoader(self: AvailObject): AvailLoader? =
		self .. { availLoader }

	override fun o_SetAvailLoader(self: AvailObject, loader: AvailLoader?) =
		self .. { availLoader = loader }

	override fun o_InterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = self .. { interruptRequestFlag(flag) }

	override fun o_GetAndClearInterruptRequestFlag(
		self: AvailObject,
		flag: InterruptRequestFlag
	): Boolean = self .. { getAndClearInterruptRequestFlag(flag) }

	override fun o_GetAndSetSynchronizationFlag(
		self: AvailObject,
		flag: SynchronizationFlag,
		value: Boolean
	): Boolean = self.. { getAndSetSynchronizationFlag(flag, value) }

	override fun o_FiberResult(self: AvailObject): AvailObject =
		self .. { fiberResult }

	override fun o_SetFiberResult(self: AvailObject, result: A_BasicObject) =
		self .. { fiberResult = result as AvailObject }

	override fun o_JoiningFibers(self: AvailObject): A_Set =
		self .. { joiningFibers }

	override fun o_WakeupTask(self: AvailObject): TimerTask? =
		self .. { wakeupTask }

	override fun o_SetWakeupTask(self: AvailObject, task: TimerTask?) =
		self .. { wakeupTask = task }

	override fun o_SetJoiningFibers(self: AvailObject, joiners: A_Set) =
		self .. { joiningFibers = joiners }

	override fun o_HeritableFiberGlobals(self: AvailObject): A_Map =
		self .. { heritableFiberGlobals }

	override fun o_SetHeritableFiberGlobals(
		self: AvailObject,
		globals: A_Map
	) = self .. { heritableFiberGlobals = globals }

	override fun o_GeneralFlag(self: AvailObject, flag: GeneralFlag): Boolean =
		self .. { generalFlag(flag) }

	override fun o_SetGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		self .. { setGeneralFlag(flag) }

	override fun o_ClearGeneralFlag(self: AvailObject, flag: GeneralFlag) =
		self .. { clearGeneralFlag(flag) }

	override fun o_ByteBuffer(self: AvailObject): ByteBuffer =
		self .. { byteBuffer }

	override fun o_EqualsByteBufferTuple(
		self: AvailObject,
		aByteBufferTuple: A_Tuple
	): Boolean = self .. { equalsByteBufferTuple(aByteBufferTuple) }

	override fun o_CompareFromToWithByteBufferTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithByteBufferTupleStartingAt(
			startIndex1,
			endIndex1,
			aByteBufferTuple,
			startIndex2)
	}

	override fun o_IsByteBufferTuple(self: AvailObject): Boolean =
		self .. { isByteBufferTuple }

	override fun o_FiberName(self: AvailObject): A_String =
		self .. { fiberName }

	override fun o_FiberNameSupplier(
		self: AvailObject,
		supplier: () -> A_String
	) = self .. { fiberNameSupplier(supplier) }

	override fun o_Bundles(self: AvailObject): A_Set =
		self .. { bundles }

	override fun o_MethodAddBundle(self: AvailObject, bundle: A_Bundle) =
		self .. { methodAddBundle(bundle) }

	override fun o_MethodRemoveBundle(self: AvailObject, bundle: A_Bundle) =
		self .. { methodRemoveBundle(bundle) }

	override fun o_DefinitionModule(self: AvailObject): A_Module =
		self .. { definitionModule() }

	override fun o_DefinitionModuleName(self: AvailObject): A_String =
		self .. { definitionModuleName() }

	@Throws(MalformedMessageException::class)
	override fun o_BundleOrCreate(self: AvailObject): A_Bundle =
		self .. { bundleOrCreate() }

	override fun o_BundleOrNil(self: AvailObject): A_Bundle =
		self .. { bundleOrNil }

	override fun o_EntryPoints(self: AvailObject): A_Map =
		self .. { entryPoints }

	override fun o_AllAncestors(self: AvailObject): A_Set =
		self .. { allAncestors }

	override fun o_ArgumentRestrictionSets(self: AvailObject): A_Tuple =
		self .. { argumentRestrictionSets() }

	override fun o_RestrictedBundle(self: AvailObject): A_Bundle =
		self .. { restrictedBundle() }

	override fun o_AtomName(self: AvailObject): A_String =
		self .. { atomName }

	override fun o_AdjustPcAndStackp(self: AvailObject, pc: Int, stackp: Int) =
		self .. { adjustPcAndStackp(pc, stackp) }

	override fun o_TreeTupleLevel(self: AvailObject): Int =
		self .. { treeTupleLevel }

	override fun o_ChildCount(self: AvailObject): Int =
		self .. { childCount }

	override fun o_ChildAt(self: AvailObject, childIndex: Int): A_Tuple =
		self .. { childAt(childIndex) }

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean
	): A_Tuple = self .. { concatenateWith(otherTuple, canDestroy) }

	override fun o_ReplaceFirstChild(
		self: AvailObject,
		newFirst: A_Tuple
	): A_Tuple = self .. { replaceFirstChild(newFirst) }

	override fun o_IsByteString(self: AvailObject): Boolean =
		self .. { isByteString }

	override fun o_IsTwoByteString(self: AvailObject): Boolean =
		self .. { isTwoByteString }

	override fun o_IsIntegerIntervalTuple(self: AvailObject): Boolean =
		self .. { isIntegerIntervalTuple }

	override fun o_IsSmallIntegerIntervalTuple(self: AvailObject): Boolean =
		self .. { isSmallIntegerIntervalTuple }

	override fun o_IsRepeatedElementTuple(self: AvailObject): Boolean =
		self .. { isRepeatedElementTuple }

	override fun o_AddWriteReactor(
		self: AvailObject,
		key: A_Atom,
		reactor: VariableAccessReactor
	) = self .. { addWriteReactor(key, reactor) }

	@Throws(AvailException::class)
	override fun o_RemoveWriteReactor(self: AvailObject, key: A_Atom) =
		self .. { removeWriteReactor(key) }

	override fun o_TraceFlag(self: AvailObject, flag: TraceFlag): Boolean =
		self .. { traceFlag(flag) }

	override fun o_SetTraceFlag(self: AvailObject, flag: TraceFlag) =
		self .. { setTraceFlag(flag) }

	override fun o_ClearTraceFlag(self: AvailObject, flag: TraceFlag) =
		self .. { clearTraceFlag(flag) }

	override fun o_RecordVariableAccess(
		self: AvailObject,
		variable: A_Variable,
		wasRead: Boolean
	) = self.. { recordVariableAccess(variable, wasRead) }

	override fun o_VariablesReadBeforeWritten(self: AvailObject): A_Set =
		self .. { variablesReadBeforeWritten }

	override fun o_VariablesWritten(self: AvailObject): A_Set =
		self .. { variablesWritten }

	override fun o_ValidWriteReactorFunctions(self: AvailObject): A_Set =
		self .. { validWriteReactorFunctions() }

	override fun o_ReplacingCaller(
		self: AvailObject,
		newCaller: A_Continuation
	): A_Continuation = self .. { replacingCaller(newCaller) }

	override fun o_WhenContinuationIsAvailableDo(
		self: AvailObject,
		whenReified: (A_Continuation) -> Unit
	) = self .. { whenContinuationIsAvailableDo(whenReified) }

	override fun o_GetAndClearReificationWaiters(
		self: AvailObject
	): List<(A_Continuation)->Unit> =
		self .. { getAndClearReificationWaiters() }

	override fun o_IsBottom(self: AvailObject): Boolean =
		self .. { isBottom }

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		self .. { isVacuousType }

	override fun o_IsTop(self: AvailObject): Boolean =
		self .. { isTop }

	override fun o_IsAtomSpecial(self: AvailObject): Boolean =
		self .. { isAtomSpecial }

	override fun o_HasValue(self: AvailObject): Boolean =
		self .. { hasValue() }

	override fun o_AddUnloadFunction(
		self: AvailObject,
		unloadFunction: A_Function
	) = self .. { addUnloadFunction(unloadFunction) }

	override fun o_ExportedNames(self: AvailObject): A_Set =
		self .. { exportedNames }

	override fun o_IsInitializedWriteOnceVariable(self: AvailObject): Boolean =
		self .. { isInitializedWriteOnceVariable }

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer
	) = self .. {
		transferIntoByteBuffer(startIndex, endIndex, outputByteBuffer)
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type
	): Boolean = self .. {
		tupleElementsInRangeAreInstancesOf(startIndex, endIndex, type)
	}

	override fun o_IsNumericallyIntegral(self: AvailObject): Boolean =
		self .. { isNumericallyIntegral }

	override fun o_TextInterface(self: AvailObject): TextInterface =
		self .. { textInterface }

	override fun o_SetTextInterface(
		self: AvailObject,
		textInterface: TextInterface
	) = self .. { this.textInterface = textInterface }

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		self .. { writeTo(writer) }

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		self .. { writeSummaryTo(writer) }

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types
	): A_Type =
		self .. { typeIntersectionOfPrimitiveTypeEnum(primitiveTypeEnum) }

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types
	): A_Type = self .. { typeUnionOfPrimitiveTypeEnum(primitiveTypeEnum) }

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int
	): A_Tuple = self .. { tupleOfTypesFromTo(startIndex, endIndex) }

	override fun o_ShowValueInNameForDebugger(
		self: AvailObject
	): Boolean = self .. { showValueInNameForDebugger() }

	override fun o_List(self: AvailObject): A_Phrase = self .. { list }

	override fun o_Permutation(self: AvailObject): A_Tuple =
		self .. { permutation }

	override fun o_EmitAllValuesOn(
		self: AvailObject,
		codeGenerator: AvailCodeGenerator
	) = self .. { emitAllValuesOn(codeGenerator) }

	override fun o_SuperUnionType(self: AvailObject): A_Type =
		self .. { superUnionType }

	override fun o_HasSuperCast(self: AvailObject): Boolean =
		self .. { hasSuperCast }

	override fun o_MacrosTuple(self: AvailObject): A_Tuple =
		self .. { macrosTuple }

	override fun o_LookupMacroByPhraseTuple(
		self: AvailObject,
		argumentPhraseTuple: A_Tuple
	): A_Tuple = self .. { lookupMacroByPhraseTuple(argumentPhraseTuple) }

	override fun o_ExpressionAt(self: AvailObject, index: Int): A_Phrase =
		self .. { expressionAt(index) }

	override fun o_ExpressionsSize(self: AvailObject): Int =
		self .. { expressionsSize }

	override fun o_ParsingPc(self: AvailObject): Int =
		self .. { parsingPc }

	override fun o_IsMacroSubstitutionNode(self: AvailObject): Boolean =
		self .. { isMacroSubstitutionNode }

	override fun o_MessageSplitter(self: AvailObject): MessageSplitter =
		self .. { messageSplitter }

	override fun o_StatementsDo(
		self: AvailObject,
		continuation: (A_Phrase) -> Unit
	) = self .. { statementsDo(continuation) }

	override fun o_MacroOriginalSendNode(self: AvailObject): A_Phrase =
		self .. { macroOriginalSendNode }

	override fun o_EqualsInt(
		self: AvailObject,
		theInt: Int
	): Boolean = self .. { equalsInt(theInt) }

	override fun o_Tokens(self: AvailObject): A_Tuple =
		self .. { tokens }

	override fun o_ChooseBundle(
		self: AvailObject,
		currentModule: A_Module
	): A_Bundle = self .. { chooseBundle(currentModule) }

	override fun o_ValueWasStablyComputed(self: AvailObject): Boolean =
		self .. { valueWasStablyComputed() }

	override fun o_SetValueWasStablyComputed(
		self: AvailObject,
		wasStablyComputed: Boolean
	) = self .. { setValueWasStablyComputed(wasStablyComputed) }

	override fun o_UniqueId(self: AvailObject): Long =
		self .. { uniqueId }

	override fun o_Definition(self: AvailObject): A_Definition =
		self .. { definition }

	override fun o_NameHighlightingPc(self: AvailObject): String =
		self .. { nameHighlightingPc }

	override fun o_SetIntersects(self: AvailObject, otherSet: A_Set): Boolean =
		self .. { setIntersects(otherSet) }

	override fun o_RemovePlanForSendable(
		self: AvailObject,
		sendable: A_Sendable
	) = self .. { removePlanForSendable(sendable) }

	override fun o_DefinitionParsingPlans(self: AvailObject): A_Map =
		self .. { definitionParsingPlans }

	override fun o_EqualsListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): Boolean = self .. { equalsListNodeType(aListNodeType) }

	override fun o_SubexpressionsTupleType(self: AvailObject): A_Type =
		self .. { subexpressionsTupleType }

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): A_Type = self .. { typeUnionOfListNodeType(aListNodeType) }

	override fun o_LazyTypeFilterTreePojo(self: AvailObject): A_BasicObject =
		self .. { lazyTypeFilterTreePojo }

	override fun o_AddPlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = self .. { addPlanInProgress(planInProgress) }

	override fun o_ParsingSignature(self: AvailObject): A_Type =
		self .. { parsingSignature() }

	override fun o_RemovePlanInProgress(
		self: AvailObject,
		planInProgress: A_ParsingPlanInProgress
	) = self .. { removePlanInProgress(planInProgress) }

	override fun o_FieldAt(
		self: AvailObject, field: A_Atom
	): AvailObject = self .. { fieldAt(field) }

	override fun o_FieldAtIndex(self: AvailObject, index: Int): AvailObject =
		self .. { fieldAtIndex(index) }

	override fun o_FieldAtOrNull(
		self: AvailObject, field: A_Atom
	): AvailObject? = self .. { fieldAtOrNull(field) }

	override fun o_FieldAtPuttingCanDestroy(
		self: AvailObject,
		field: A_Atom,
		value: A_BasicObject,
		canDestroy: Boolean
	): A_BasicObject =
		self .. { fieldAtPuttingCanDestroy(field, value, canDestroy) }

	override fun o_FieldTypeAt(
		self: AvailObject, field: A_Atom
	): A_Type = self .. { fieldTypeAt(field) }

	override fun o_FieldTypeAtIndex(self: AvailObject, index: Int): A_Type =
		self .. { fieldTypeAtIndex(index) }

	override fun o_FieldTypeAtOrNull(
		self: AvailObject, field: A_Atom
	): A_Type? = self .. { fieldTypeAtOrNull(field) }

	override fun o_ParsingPlan(self: AvailObject): A_DefinitionParsingPlan =
		self .. { parsingPlan }

	override fun o_CompareFromToWithIntTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int
	): Boolean = self .. {
		compareFromToWithIntTupleStartingAt(
			startIndex1, endIndex1, anIntTuple, startIndex2)
	}

	override fun o_IsIntTuple(self: AvailObject): Boolean =
		self .. { isIntTuple }

	override fun o_IsLongTuple(self: AvailObject): Boolean =
		self .. { isLongTuple }

	override fun o_EqualsIntTuple(
		self: AvailObject, anIntTuple: A_Tuple
	): Boolean = self .. { equalsIntTuple(anIntTuple) }

	override fun o_EqualsLongTuple(
		self: AvailObject, aLongTuple: A_Tuple
	): Boolean = self .. { equalsLongTuple(aLongTuple) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicAddToMap(
		self: AvailObject,
		key: A_BasicObject,
		value: A_BasicObject
	) = self .. { atomicAddToMap(key, value) }

	@Throws(VariableGetException::class, VariableSetException::class)
	override fun o_AtomicRemoveFromMap(
		self: AvailObject,
		key: A_BasicObject
	) = self .. { atomicRemoveFromMap(key) }

	@Throws(VariableGetException::class)
	override fun o_VariableMapHasKey(
		self: AvailObject, key: A_BasicObject
	): Boolean = self .. { variableMapHasKey(key) }

	override fun o_LexerMethod(self: AvailObject): A_Method =
		self .. { lexerMethod }

	override fun o_LexerFilterFunction(self: AvailObject): A_Function =
		self .. { lexerFilterFunction }

	override fun o_LexerBodyFunction(self: AvailObject): A_Function =
		self .. { lexerBodyFunction }

	override fun o_SetLexer(self: AvailObject, lexer: A_Lexer) =
		self .. {
			this.lexer = lexer
		}

	override fun o_AddLexer(self: AvailObject, lexer: A_Lexer) =
		self .. { addLexer(lexer) }

	override fun o_NextLexingState(self: AvailObject): LexingState =
		self .. { nextLexingState() }

	override fun o_NextLexingStatePojo(self: AvailObject): AvailObject =
		self .. { nextLexingStatePojo() }

	override fun o_SetNextLexingStateFromPrior(
		self: AvailObject,
		priorLexingState: LexingState
	) = self .. { setNextLexingStateFromPrior(priorLexingState) }

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int =
		self .. { tupleCodePointAt(index) }

	override fun o_OriginatingPhrase(self: AvailObject): A_Phrase =
		self .. { originatingPhrase }

	override fun o_IsGlobal(self: AvailObject): Boolean =
		self .. { isGlobal() }

	override fun o_GlobalModule(self: AvailObject): A_Module =
		self .. { globalModule() }

	override fun o_GlobalName(self: AvailObject): A_String =
		self .. { globalName() }

	override fun o_CreateLexicalScanner(self: AvailObject): LexicalScanner =
		self .. { createLexicalScanner() }

	override fun o_Lexer(self: AvailObject): A_Lexer =
		self .. { lexer }

	override fun o_SetSuspendingFunction(
		self: AvailObject,
		suspendingFunction: A_Function
	) = self .. { this.suspendingFunction = suspendingFunction }

	override fun o_SuspendingFunction(self: AvailObject): A_Function =
		self .. { suspendingFunction }

	override fun o_IsBackwardJump(self: AvailObject): Boolean =
		self .. { isBackwardJump }

	override fun o_LatestBackwardJump(
		self: AvailObject
	): A_BundleTree = self .. { latestBackwardJump }

	override fun o_HasBackwardJump(self: AvailObject): Boolean =
		self .. { hasBackwardJump }

	override fun o_IsSourceOfCycle(self: AvailObject): Boolean =
		self .. { isSourceOfCycle }

	override fun o_IsSourceOfCycle(
		self: AvailObject,
		isSourceOfCycle: Boolean
	) = self .. { this.isSourceOfCycle = isSourceOfCycle }

	override fun o_DebugLog(self: AvailObject): StringBuilder =
		self .. { debugLog }

	override fun o_NumConstants(self: AvailObject): Int =
		self .. { numConstants }

	override fun o_ConstantTypeAt(self: AvailObject, index: Int): A_Type =
		self .. { constantTypeAt(index) }

	override fun o_ReturnerCheckStat(self: AvailObject): Statistic =
		self .. { returnerCheckStat }

	override fun o_ReturneeCheckStat(self: AvailObject): Statistic =
		self .. { returneeCheckStat }

	override fun o_NumNybbles(self: AvailObject): Int =
		self .. { numNybbles }

	override fun o_LineNumberEncodedDeltas(self: AvailObject): A_Tuple =
		self .. { lineNumberEncodedDeltas }

	override fun o_CurrentLineNumber(
		self: AvailObject, topFrame: Boolean
	): Int = self .. { currentLineNumber(topFrame) }

	override fun o_FiberResultType(self: AvailObject): A_Type =
		self .. { fiberResultType }

	override fun o_TestingTree(
		self: AvailObject): LookupTree<A_Definition, A_Tuple> =
		self .. { testingTree }

	override fun o_ForEach(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) = self .. { forEach(action) }

	override fun o_ForEachInMapBin(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) = self .. { forEachInMapBin(action) }

	override fun o_SetSuccessAndFailure(
		self: AvailObject,
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit
	) = self .. { setSuccessAndFailure(onSuccess, onFailure) }

	override fun o_ClearLexingState(self: AvailObject) =
		self .. { clearLexingState() }

	override fun o_LastExpression(self: AvailObject): A_Phrase =
		self .. { lastExpression }

	override fun o_RegisterDump(self: AvailObject): AvailObject =
		self .. { registerDump() }

	@Throws(SignatureException::class)
	override fun o_BundleAddMacro(
		self: AvailObject,
		macro: A_Macro,
		ignoreSeals: Boolean
	) = self .. { bundleAddMacro(macro, ignoreSeals) }

	override fun o_DefinitionBundle(self: AvailObject): A_Bundle =
		self .. { definitionBundle() }

	override fun o_MembershipChanged(self: AvailObject) =
		self .. { membershipChanged() }

	override fun o_ModuleAddMacro(self: AvailObject, macro: A_Macro) =
		self .. { moduleAddMacro(macro) }

	override fun o_RemoveMacro(self: AvailObject, macro: A_Macro) =
		self .. { removeMacro(macro) }

	override fun o_AddBundle(self: AvailObject, bundle: A_Bundle): Unit =
		self .. { addBundle(bundle) }

	override fun o_ReturnTypeIfPrimitiveFails(self: AvailObject): A_Type =
		self .. { returnTypeIfPrimitiveFails }

	override fun o_ExtractDumpedObjectAt(
		self: AvailObject,
		index: Int
	): AvailObject = self .. { extractDumpedObjectAt(index) }

	override fun o_ExtractDumpedLongAt(self: AvailObject, index: Int): Long =
		self .. { extractDumpedLongAt(index) }

	override fun o_ModuleAddStyler(self: AvailObject, styler: A_Styler) =
		self .. { moduleAddStyler(styler) }

	override fun o_ModuleStylers (self: AvailObject): A_Set =
		self .. { (this as A_Module).stylers }

	override fun o_ModuleState(self: AvailObject): ModuleDescriptor.State =
		self .. { moduleState }

	override fun o_SetModuleState(
		self: AvailObject,
		newState: ModuleDescriptor.State
	) = self .. {
		moduleState = newState
	}

	override fun o_SetAtomBundle(self: AvailObject, bundle: A_Bundle) =
		self .. { setAtomBundle(bundle) }

	override fun o_OriginatingPhraseAtIndex(
		self: AvailObject,
		index: Int
	): A_Phrase = self .. { originatingPhraseAtIndex(index) }

	override fun o_RecordBlockPhrase(
		self: AvailObject,
		blockPhrase: A_Phrase
	): Int = self .. { recordBlockPhrase(blockPhrase) }

	override fun o_GetAndSetTupleOfBlockPhrases(
		self: AvailObject,
		newValue: AvailObject
	): AvailObject = self .. { getAndSetTupleOfBlockPhrases(newValue) }

	override fun o_OriginatingPhraseIndex(self: AvailObject): Int =
		self .. { originatingPhraseIndex }

	override fun o_DeclarationNames(self: AvailObject): A_Tuple =
		self .. { declarationNames }

	override fun o_PackedDeclarationNames(self: AvailObject): A_String =
		self .. { packedDeclarationNames }

	override fun o_SetOriginatingPhraseIndex(
		self: AvailObject,
		index: Int
	) = self .. { originatingPhraseIndex = index }

	override fun o_LexerApplicability(
		self: AvailObject,
		codePoint: Int
	): Boolean? = self .. { lexerApplicability(codePoint) }

	override fun o_SetLexerApplicability(
		self: AvailObject,
		codePoint: Int,
		applicability: Boolean
	) = self .. { setLexerApplicability(codePoint, applicability) }

	override fun o_SerializedObjects(
		self: AvailObject,
		serializedObjects: A_Tuple
	) = self .. { serializedObjects(serializedObjects) }

	override fun o_ApplyModuleHeader(
		self: AvailObject,
		loader: AvailLoader,
		moduleHeader: ModuleHeader
	): String? = self .. { applyModuleHeader(loader, moduleHeader) }

	override fun o_HasAncestor(
		self: AvailObject,
		potentialAncestor: A_Module
	): Boolean = self .. { hasAncestor(potentialAncestor) }

	override fun o_FiberHelper(
		self: AvailObject
	): FiberDescriptor.FiberHelper = self .. { fiberHelper }

	override fun o_TrimType(
		self: AvailObject,
		typeToRemove: A_Type
	): A_Type = self .. { trimType(typeToRemove) }

	override fun o_UpdateStylers(
		self: AvailObject,
		updater: A_Set.() -> A_Set
	) = self .. { updateStylers(updater) }

	override fun o_MethodStylers(
		self: AvailObject
	): A_Set = self .. { methodStylers }

	override fun o_InstanceTag(
		self: AvailObject
	): TypeTag = self .. { instanceTag }

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag =
		self .. { computeInstanceTag() }

	override fun o_SetManifestEntriesIndex(
		self: AvailObject,
		recordNumber: Long
	) = self .. { setManifestEntriesIndex(recordNumber) }

	override fun o_ManifestEntries(
		self: AvailObject
	): List<ModuleManifestEntry> = self .. { manifestEntries() }

	override fun o_SynthesizeCurrentLexingState(
		self: AvailObject
	): LexingState = self .. { synthesizeCurrentLexingState() }

	override fun o_ObjectVariant(self: AvailObject): ObjectLayoutVariant =
		self .. { objectVariant }

	override fun o_ObjectTypeVariant(self: AvailObject): ObjectLayoutVariant =
		self .. { objectTypeVariant }

	override fun o_ModuleNameNative(self: AvailObject): String =
		self .. { moduleNameNative }

	override fun o_ReleaseFromDebugger(self: AvailObject) =
		self .. { releaseFromDebugger() }

	override fun o_DeoptimizedForDebugger(self: AvailObject): A_Continuation =
		self .. { deoptimizedForDebugger() }

	override fun o_GetValueForDebugger(self: AvailObject): AvailObject =
		self .. { getValueForDebugger() }

	override fun o_HighlightPc(self: AvailObject, topFrame: Boolean): Int =
		self .. { highlightPc(topFrame) }

	override fun o_CaptureInDebugger(
		self: AvailObject,
		debugger: AvailDebuggerModel
	) = self .. { captureInDebugger(debugger) }

	override fun o_SetStylingRecordIndex(
		self: AvailObject,
		recordNumber: Long
	) = self .. { setStylingRecordIndex(recordNumber) }

	override fun o_StylingRecord(self: AvailObject): StylingRecord =
		self .. { stylingRecord() }

	override fun o_StylerMethod(self: AvailObject): A_Method =
		self .. { stylerMethod }

	override fun o_GeneratingPhrase(self: AvailObject): A_Phrase =
		self .. { generatingPhrase }

	override fun o_IsInCurrentModule(
		self: AvailObject,
		currentModule: A_Module
	): Boolean = self .. { isInCurrentModule(currentModule) }

	override fun o_SetCurrentModule(
		self: AvailObject,
		currentModule: A_Module
	): Unit = self .. { setCurrentModule(currentModule) }

	override fun o_ApplyStylesThen(
		self: AvailObject,
		context: CompilationContext,
		visitedSet: MutableSet<A_Phrase>,
		then: ()->Unit
	): Unit = self .. { applyStylesThen(context, visitedSet, then) }
}
