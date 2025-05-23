// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.m3.compiler.postprocessing.inference;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.Column;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.GenericTypeOperation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.RelationType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.FunctionExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.function.FunctionType;
import org.finos.legend.pure.m3.navigation.generictype.GenericTypeWithXArguments;
import org.finos.legend.pure.m3.navigation.importstub.ImportStub;
import org.finos.legend.pure.m3.navigation.relation._RelationType;
import org.finos.legend.pure.m3.navigation.type.Type;
import org.finos.legend.pure.m3.navigation.typeparameter.TypeParameter;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.SafeAppendable;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class TypeInferenceContext
{
    private static final AtomicInteger counter = new AtomicInteger(0);

    private final int id;
    private final TypeInferenceContext parent;
    private final ProcessorSupport processorSupport;
    private CoreInstance scope;
    private final MutableList<TypeInferenceContextState> states = Lists.mutable.empty();
    private final MutableSet<String> tops = Sets.mutable.empty();

    public TypeInferenceContext(TypeInferenceContext parent, CoreInstance owner, ProcessorSupport processorSupport)
    {
        TypeInferenceContextState state = new TypeInferenceContextState();
        this.id = counter.incrementAndGet();
        this.states.add(state);
        this.parent = parent;
        this.processorSupport = processorSupport;
        if (owner != null)
        {
            RichIterable<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.TypeParameter> typeParameters = (owner instanceof Class) ?
                    ((Class<?>) owner)._typeParameters() :
                    ((owner instanceof org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType) ?
                            ((org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType) owner)._typeParameters() :
                            Lists.immutable.empty());
            typeParameters.forEach(typeParameter ->
            {
                String paramName = typeParameter._name();
                state.putTypeParameterValue(paramName, TypeParameter.wrapGenericType(typeParameter, processorSupport), this, true);
                this.tops.add(paramName);
            });
        }
    }

    public TypeInferenceContext(TypeInferenceContext parent, ProcessorSupport processorSupport)
    {
        this(parent, null, processorSupport);
    }

    public TypeInferenceContext(CoreInstance owner, ProcessorSupport processorSupport)
    {
        this(null, owner, processorSupport);
    }

    public String getId()
    {
        return Integer.toHexString(this.id);
    }

    public void addStateForCollectionElement()
    {
        this.states.add(this.states.getFirst().copy());
    }

    MutableList<TypeInferenceContextState> drop(int size)
    {
        MutableList<TypeInferenceContextState> dropped = Lists.mutable.ofInitialCapacity(Math.min(size, this.states.size()));
        int newSize = this.states.size() - size;
        if (newSize <= 0)
        {
            dropped.addAll(this.states);
            this.states.clear();
        }
        else
        {
            dropped.addAll(this.states.subList(newSize - 1, this.states.size()));
            while (this.states.size() > newSize)
            {
                this.states.remove(this.states.size() - 1);
            }
        }
        return dropped;
    }


    public boolean isTop(String typeParam)
    {
        return getTopContext().tops.contains(typeParam);
    }

    public TypeInferenceContext getParent()
    {
        return this.parent;
    }

    public boolean isTypeParameterResolved(CoreInstance genericType)
    {
        if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericType))
        {
            return true;
        }
        ParameterValueWithFlag gf = this.states.getLast().getTypeParameterValueWithFlag(org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(genericType));

        if (gf != null && (gf.isTerminal() || org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(gf.getParameterValue())))
        {
            return true;
        }

        TypeInferenceContext top = getTopContext();
        if (top != null)
        {
            ParameterValueWithFlag parentGf = top.states.getLast().getTypeParameterValueWithFlag(org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(genericType));
            return (parentGf != null && (parentGf.isTerminal() || org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(parentGf.getParameterValue())));
        }
        return false;
    }

    public TypeInferenceContext getTopContext()
    {
        TypeInferenceContext ctx = this;
        while (ctx.parent != null)
        {
            ctx = ctx.parent;
        }
        return ctx;
    }

    @Override
    public String toString()
    {
        return print(new StringBuilder(128)).toString();
    }

    public <T extends Appendable> T print(T appendable)
    {
        SafeAppendable safeAppendable = SafeAppendable.wrap(appendable);
        safeAppendable.append("##>");
        printRecursive(safeAppendable);
        safeAppendable.append("<##");
        return appendable;
    }

    private void printRecursive(SafeAppendable appendable)
    {
        appendable.append('[').append(getId()).append(":");
        printScope(appendable);
        appendable.append(" ~ ");
        printStates(appendable);
        appendable.append(']');
        if (this.parent != null)
        {
            this.parent.printRecursive(appendable.append(" -> "));
        }
    }

    private void printScope(SafeAppendable appendable)
    {
        if (this.scope == null)
        {
            appendable.append("NULL");
        }
        else if (this.scope instanceof GenericType)
        {
            org.finos.legend.pure.m3.navigation.generictype.GenericType.print(appendable, this.scope, this.processorSupport);
        }
        else
        {
            String functionName = (this.scope instanceof FunctionExpression) ?
                                  ((FunctionExpression) this.scope)._functionName() :
                                  ((this.scope instanceof Function) ? ((Function<?>) this.scope)._functionName() : null);
            appendable.append(functionName);
            FunctionType.print(appendable, this.processorSupport.function_getFunctionType(this.scope), this.processorSupport);
        }
    }

    private void printStates(SafeAppendable appendable)
    {
        this.states.forEachWithIndex((state, i) -> printState((i == 0) ? appendable : appendable.append(" || "), state));
    }

    private void printState(SafeAppendable appendable, TypeInferenceContextState state)
    {
        appendable.append('[');
        state.getTypeParameters().toSortedList().forEachWithIndex((parameter, i) ->
        {
            if (i != 0)
            {
                appendable.append(", ");
            }
            appendable.append(parameter).append(" = ");
            ParameterValueWithFlag valueWithFlag = state.getTypeParameterValueWithFlag(parameter);
            if (valueWithFlag.isTerminal())
            {
                appendable.append('*');
            }
            org.finos.legend.pure.m3.navigation.generictype.GenericType.print(appendable, valueWithFlag.getParameterValue(), this.processorSupport);
            if (!org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(valueWithFlag.getParameterValue()))
            {
                appendable.append(':').append(valueWithFlag.getTargetGenericsContext().getId());
            }
        });
        appendable.append("] / [");
        state.getMultiplicityParameters().toSortedList().forEachWithIndex((parameter, i) ->
        {
            if (i != 0)
            {
                appendable.append(", ");
            }
            appendable.append(parameter).append(" = ");
            ParameterValueWithFlag valueWithFlag = state.getMultiplicityParameterValueWithFlag(parameter);
            if (valueWithFlag.isTerminal())
            {
                appendable.append('*');
            }
            org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.print(appendable, valueWithFlag.getParameterValue(), true);
        });
        appendable.append(']');
    }

    public void registerMul(Multiplicity templateMul, Multiplicity valueMul, TypeInferenceContext targetGenericsContext, TypeInferenceObserver observer)
    {
        Objects.requireNonNull(targetGenericsContext);

        String name = org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.getMultiplicityParameter(templateMul);
        if (name != null)
        {
            RegistrationRequest forward = null;

            ParameterValueWithFlag existing = this.states.getLast().getMultiplicityParameterValueWithFlag(name);
            if (existing == null)
            {
                // New registration
                this.states.getLast().putMultiplicityParameterValue(name, valueMul, targetGenericsContext, false);
            }
            else if (org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.isMultiplicityConcrete(existing.getParameterValue()) && org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.isMultiplicityConcrete(valueMul))
            {
                // Merge two concrete multiplicities
                CoreInstance mul = org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.minSubsumingMultiplicity(Lists.mutable.with(valueMul, existing.getParameterValue()), this.processorSupport);
                this.states.getLast().putMultiplicityParameterValue(name, mul, targetGenericsContext, false);
            }
            else if (this.states.size() > 1)
            {
                // We  are processing elements of a collection, record what we learn for the element which will later
                // be processed by TypeInference.potentiallyUpdateParentTypeParamForInstanceValueWithManyElements later
                this.states.getLast().putMultiplicityParameterValue(name, valueMul, targetGenericsContext, false);
            }
            else if (org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.isMultiplicityConcrete(existing.getParameterValue()))
            {
                // Replace the existing concrete registration with a generic one and move the concrete one to the referenced type
                this.states.getLast().putMultiplicityParameterValue(name, valueMul, targetGenericsContext, false);
                forward = new RegistrationRequest(targetGenericsContext, valueMul, existing.getParameterValue());
            }
            else if (org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.isMultiplicityConcrete(valueMul))
            {
                // forward the registration of this concrete type to the already referenced type
                forward = new RegistrationRequest(existing.getTargetGenericsContext(), existing.getParameterValue(), valueMul);
            }
            else
            {
                // forward the registration of this generic type to the already referenced type
                forward = new RegistrationRequest(existing.getTargetGenericsContext(), existing.getParameterValue(), valueMul);
            }
            observer.registerMul(templateMul, valueMul, this, targetGenericsContext);

            if (forward != null && !forward.context.equals(this))
            {
                observer.shiftTab();
                forward.context.registerMul((Multiplicity) forward.template, (Multiplicity) forward.value, targetGenericsContext, observer);
                observer.unShiftTab();
            }
        }
    }

    public ListIterable<CoreInstance> extractTypes(GenericTypeWithXArguments homogenizedTypeArgs)
    {
        return homogenizedTypeArgs.extractArgumentsAsTypeParameters(this.processorSupport);
    }

    public ListIterable<CoreInstance> extractMuls(GenericTypeWithXArguments homogenizedTypeArgs)
    {
        return homogenizedTypeArgs.extractArgumentsAsMultiplicityParameters(this.processorSupport);
    }

    public void register(GenericType templateGenType, GenericType genericType, TypeInferenceContext targetGenericsContext, TypeInferenceObserver observer)
    {
        register(templateGenType, genericType, targetGenericsContext, false, observer);
    }

    public void register(GenericType templateGenType, GenericType genericType, TypeInferenceContext targetGenericsContext, boolean merge, TypeInferenceObserver observer)
    {
        observer.tryingRegistration(templateGenType, genericType, this, targetGenericsContext);

        Objects.requireNonNull(targetGenericsContext);

        if (genericType == null)
        {
            // nothing to do
            return;
        }

        GenericType genericTypeCopy = (GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.copyGenericType(genericType, true, this.processorSupport);

        if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeOperationEqual(genericTypeCopy) && (targetGenericsContext.getParent() != null))
        {
            GenericTypeOperation gto = (GenericTypeOperation) genericTypeCopy;
            observer.shiftTab();
            getParent().register(gto._left(), gto._right(), targetGenericsContext.getParent(), merge, observer);
            observer.unShiftTab();
        }

        if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeOperationEqual(templateGenType))
        {
            observer.shiftTab();
            register(((GenericTypeOperation) templateGenType)._left(), genericTypeCopy, targetGenericsContext, merge, observer);
            observer.unShiftTab();
        }

        if ((templateGenType instanceof GenericTypeOperation) && (genericTypeCopy instanceof GenericTypeOperation))
        {
            GenericTypeOperation templateGenTypeOperation = (GenericTypeOperation) templateGenType;
            GenericTypeOperation genericTypeOperationCopy = (GenericTypeOperation) genericTypeCopy;
            if (templateGenTypeOperation._type().getName().equals(genericTypeOperationCopy._type().getName()))
            {
                observer.shiftTab();
                register(templateGenTypeOperation._left(), genericTypeOperationCopy._left(), targetGenericsContext, merge, observer);
                register(templateGenTypeOperation._right(), genericTypeOperationCopy._right(), targetGenericsContext, merge, observer);
                observer.unShiftTab();
            }
        }

        String name = org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(templateGenType);
        if (name != null)
        {
            ParameterValueWithFlag existing = this.states.getLast().getTypeParameterValueWithFlag(name);

            List<RegistrationRequest> forwards = Lists.mutable.empty();

            if (existing == null)
            {
                // New registration
                this.states.getLast().putTypeParameterValue(name, genericTypeCopy, targetGenericsContext, false);
            }
            else if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(existing.getParameterValue()) && org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericTypeCopy))
            {
                GenericType existingGenericType = (GenericType) existing.getParameterValue();
                boolean isCovariant = TypeParameter.isCovariant(templateGenType);
                if (_RelationType.isRelationType(existingGenericType.getValueForMetaPropertyToOne(M3Properties.rawType), this.processorSupport) &&
                        _RelationType.isRelationType(genericTypeCopy.getValueForMetaPropertyToOne(M3Properties.rawType), this.processorSupport))
                {
                    GenericType res;
                    if (_RelationType.canConcatenate(existingGenericType, genericTypeCopy, this.processorSupport))
                    {
                        res = _RelationType.merge(existingGenericType, genericTypeCopy, isCovariant, this.processorSupport);
                    }
                    else
                    {
                        res = (GenericType) this.processorSupport.type_wrapGenericType(this.processorSupport.type_TopType());
                    }
                    this.states.getLast().putTypeParameterValue(name, res, targetGenericsContext, false);
                }
                else
                {
                    if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(existingGenericType) && Type.subTypeOf(existingGenericType._rawType(), this.processorSupport.package_getByUserPath(M3Paths.Function), this.processorSupport) &&
                            org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericTypeCopy) && Type.subTypeOf(genericTypeCopy._rawType(), this.processorSupport.package_getByUserPath(M3Paths.Function), this.processorSupport))
                    {
                        RichIterable<? extends GenericType> existingTA = ((GenericType) existing.getParameterValue())._typeArguments();
                        RichIterable<? extends GenericType> replacementTA = genericTypeCopy._typeArguments();
                        if (existingTA.size() == replacementTA.size())
                        {
                            Iterator<? extends GenericType> existingTypeArguments = existingTA.iterator();
                            Iterator<? extends GenericType> replacementTypeArguments = replacementTA.iterator();
                            while (existingTypeArguments.hasNext())
                            {
                                GenericType existingArgument = existingTypeArguments.next();
                                GenericType replacementArgument = replacementTypeArguments.next();
                                forwards.add(new RegistrationRequest(existing.getTargetGenericsContext(), existingArgument, replacementArgument));
                            }
                        }
                    }

                    // Merge two concrete types
                    GenericType merged = (GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.findBestCommonGenericType(Lists.mutable.with(existing.getParameterValue(), genericTypeCopy), TypeParameter.isCovariant(templateGenType), false, genericType.getSourceInformation(), this.processorSupport);
                    this.states.getLast().putTypeParameterValue(name, merged, targetGenericsContext, false);

                    // See if the replacement is the more concrete version of a previously semi-concrete type (List<T> replaced by List<String>)
                    CoreInstance existingRawType = ((GenericType) existing.getParameterValue())._rawType();
                    CoreInstance replacementRawType = merged._rawType();
                    if (existingRawType.equals(replacementRawType))
                    {
                        Iterator<? extends GenericType> existingTypeArguments = ((GenericType) existing.getParameterValue())._typeArguments().iterator();
                        Iterator<? extends GenericType> replacementTypeArguments = merged._typeArguments().iterator();
                        while (existingTypeArguments.hasNext())
                        {
                            GenericType existingArgument = existingTypeArguments.next();
                            GenericType replacementArgument = replacementTypeArguments.next();
                            if (!org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(existingArgument) && org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(replacementArgument))
                            {
                                forwards.add(new RegistrationRequest(existing.getTargetGenericsContext(), existingArgument, replacementArgument));
                            }
                        }
                    }
                }
            }
            else if (this.states.size() > 1)
            {
                // We  are processing elements of a collection, record what we learn for the element which will later
                // be processed by TypeInference.potentiallyUpdateParentTypeParamForInstanceValueWithManyElements later
                this.states.getLast().putTypeParameterValue(name, genericTypeCopy, targetGenericsContext, false);
            }
            else if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(existing.getParameterValue()))
            {
                // Replace the existing concrete registration with a generic one and move the concrete one to the referenced type
                this.states.getLast().putTypeParameterValue(name, genericTypeCopy, targetGenericsContext, false);
                forwards.add(new RegistrationRequest(targetGenericsContext, genericTypeCopy, existing.getParameterValue()));
            }
            else if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericTypeCopy))
            {
                if (existing.getParameterValue() instanceof GenericTypeOperation)
                {
                    this.states.getLast().putTypeParameterValue(name, genericTypeCopy, targetGenericsContext, false);
                }
                else if (!existing.getTargetGenericsContext().equals(this))
                {
                    forwards.add(new RegistrationRequest(existing.getTargetGenericsContext(), existing.getParameterValue(), genericTypeCopy));
                }
                else if (!merge)
                {
                    // We are in the right context and not in 'merge' mode, so it means we are propagating the inference UP.
                    this.states.getLast().putTypeParameterValue(name, genericTypeCopy, targetGenericsContext, false);
                }
                else
                {
                    // 'Merge' means that we are looping through function parameters after parameter inference is true. In this case finding multiple values means that we have to take the most common type!
                    // It is the case for if<T|m>(test:Boolean[1], valid:Function<{->T[m]}>[1], invalid:Function<{->T[m]}>[1]):T[m];
                    // When we find multiple values for T (one is a type Parameter (K) and the other one is concrete), we should return Any after a merge..
                    // We are not currently doing it, but it should eventually be fixed here
//                        GenericType res = (GenericType) org.finos.legend.pure.m3.navigation.generictype.GenericType.findBestCommonGenericType(Lists.mutable.with(existing.getParameterValue(), genericTypeCopy), TypeParameter.isCovariant(templateGenType), false, genericType.getSourceInformation(), this.processorSupport);
//                        this.states.getLast().putTypeParameterValue(name, res, targetGenericsContext, true);
                }
            }
            else
            {
                if (!existing.getTargetGenericsContext().equals(this))
                {
                    // forward the registration of this generic type to the already referenced type
                    forwards.add(new RegistrationRequest(existing.getTargetGenericsContext(), existing.getParameterValue(), genericTypeCopy));
                }
            }
            observer.register(templateGenType, genericTypeCopy, this, targetGenericsContext);

            observer.shiftTab();
            forwards.forEach(request -> request.context.register((GenericType) request.template, (GenericType) request.value, targetGenericsContext, merge, observer));
            observer.unShiftTab();
        }

        if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(templateGenType) && org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericTypeCopy))
        {
            CoreInstance templateRawType = ImportStub.withImportStubByPass(templateGenType._rawTypeCoreInstance(), this.processorSupport);
            CoreInstance copyRawType = ImportStub.withImportStubByPass(genericTypeCopy._rawTypeCoreInstance(), this.processorSupport);
            if (!Type.isBottomType(templateRawType, this.processorSupport) &&
                    !Type.isBottomType(copyRawType, this.processorSupport) &&
                    !Type.isTopType(templateRawType, this.processorSupport) &&
                    !Type.isTopType(copyRawType, this.processorSupport))
            {
                if (_RelationType.isRelationType(templateRawType, this.processorSupport) && _RelationType.isRelationType(copyRawType, this.processorSupport))
                {
                    RichIterable<? extends Column<?, ?>> valColumns = ((RelationType<?>) copyRawType)._columns();
                    RichIterable<? extends Column<?, ?>> templateColumns = ((RelationType<?>) templateRawType)._columns();
                    Pair<ListIterable<? extends Column<?, ?>>, ListIterable<? extends Column<?, ?>>> res = _RelationType.alignColumnSets(valColumns, templateColumns, this.processorSupport);
                    observer.shiftTab();
                    res.getTwo().zip(res.getOne()).forEach(c ->
                            register(ListHelper.wrapListIterable(c.getOne()._classifierGenericType()._typeArguments()).get(1), ListHelper.wrapListIterable(c.getTwo()._classifierGenericType()._typeArguments()).get(1), targetGenericsContext, merge, observer)
                    );
                    observer.unShiftTab();
                }
                else if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(templateGenType) &&
                        FunctionType.isFunctionType(templateRawType, this.processorSupport) &&
                        org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericTypeCopy) &&
                        FunctionType.isFunctionType(copyRawType, this.processorSupport))
                {
                    processFunctionType(targetGenericsContext, false, observer, templateGenType, genericTypeCopy);
                }
                else
                {
                    ListIterable<? extends CoreInstance> typeTemplates;
                    ListIterable<? extends CoreInstance> mulTemplates;
                    ListIterable<? extends CoreInstance> typeValues;
                    ListIterable<? extends CoreInstance> mulValues;

                    if (Type.subTypeOf(templateRawType, copyRawType, this.processorSupport))
                    {
                        typeTemplates = extractTypes(org.finos.legend.pure.m3.navigation.generictype.GenericType.resolveClassTypeParameterUsingInheritance(templateGenType, genericTypeCopy, this.processorSupport));
                        mulTemplates = extractMuls(org.finos.legend.pure.m3.navigation.generictype.GenericType.resolveClassMultiplicityParameterUsingInheritance(templateGenType, copyRawType, this.processorSupport));
                        typeValues = ListHelper.wrapListIterable(genericTypeCopy._typeArguments());
                        mulValues = ListHelper.wrapListIterable(genericTypeCopy._multiplicityArguments());
                    }
                    else if (!org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeFullyConcrete(genericTypeCopy, this.processorSupport) ||
                            !org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeFullyConcrete(templateGenType, this.processorSupport))
                    {
                        typeTemplates = ListHelper.wrapListIterable(templateGenType._typeArguments());
                        mulTemplates = ListHelper.wrapListIterable(templateGenType._multiplicityArguments());
                        typeValues = extractTypes(org.finos.legend.pure.m3.navigation.generictype.GenericType.resolveClassTypeParameterUsingInheritance(genericTypeCopy, templateGenType, this.processorSupport));
                        mulValues = extractMuls(org.finos.legend.pure.m3.navigation.generictype.GenericType.resolveClassMultiplicityParameterUsingInheritance(genericTypeCopy, templateRawType, this.processorSupport));
                    }
                    else
                    {
                        typeTemplates = null;
                        mulTemplates = null;
                        typeValues = null;
                        mulValues = null;
                    }

                    if (mulValues != null)
                    {
                        mulTemplates.forEachWithIndex((mulTemplate, z) -> registerMul((Multiplicity) mulTemplate, (Multiplicity) mulValues.get(z), targetGenericsContext, observer));
                        typeTemplates.forEachWithIndex((typeTemplate, z) ->
                        {
                            GenericType template = (GenericType) typeTemplate;
                            GenericType value = (GenericType) typeValues.get(z);

                            if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(template) &&
                                    FunctionType.isFunctionType(template._rawTypeCoreInstance(), this.processorSupport) &&
                                    org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(value) &&
                                    FunctionType.isFunctionType(value._rawTypeCoreInstance(), this.processorSupport))
                            {
                                processFunctionType(targetGenericsContext, merge, observer, template, value);
                            }
                            else
                            {
                                observer.shiftTab();
                                register(template, value, targetGenericsContext, merge, observer);
                                observer.unShiftTab();
                            }
                        });
                    }
                }
            }
        }
    }

    private void processFunctionType(TypeInferenceContext targetGenericsContext, boolean merge, TypeInferenceObserver observer, GenericType first, GenericType second)
    {
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType firstFuncType = (org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType) first._rawTypeCoreInstance();
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType secondFuncType = (org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType) second._rawTypeCoreInstance();
        observer.register(first, second, this, targetGenericsContext);
        observer.shiftTab();

        ListIterable<? extends VariableExpression> firstParams = ListHelper.wrapListIterable(firstFuncType._parameters());
        ListIterable<? extends VariableExpression> secondParams = ListHelper.wrapListIterable(secondFuncType._parameters());

        if (firstParams.size() == secondParams.size())
        {
            for (int i = 0; i < firstParams.size(); i++)
            {
                register(firstParams.get(i)._genericType(), secondParams.get(i)._genericType(), targetGenericsContext, merge, observer);
                registerMul(firstParams.get(i)._multiplicity(), secondParams.get(i)._multiplicity(), targetGenericsContext, observer);
            }
            register(firstFuncType._returnType(), secondFuncType._returnType(), targetGenericsContext, merge, observer);
            registerMul(firstFuncType._returnMultiplicity(), secondFuncType._returnMultiplicity(), targetGenericsContext, observer);
        }

        observer.unShiftTab();
    }

    public CoreInstance resolve(CoreInstance genericType)
    {
        if (org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(genericType))
        {
            return genericType;
        }
        ParameterValueWithFlag gf = this.states.getLast().getTypeParameterValueWithFlag(org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(genericType));
        if (gf != null)
        {
            return gf.getParameterValue();
        }
        TypeInferenceContext top = getTopContext();
        if (top != null)
        {
            ParameterValueWithFlag parentGf = top.states.getLast().getTypeParameterValueWithFlag(org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(genericType));
            if (parentGf != null)
            {
                return parentGf.getParameterValue();
            }
        }
        return null;
    }

    public void setAhead()
    {
        this.states.getLast().setAhead();
    }

    public boolean isAhead()
    {
        return this.states.getLast().isAhead();
    }

    public boolean isAheadConsumed()
    {
        return this.states.getLast().isAheadConsumed();
    }

    public void aheadConsumed()
    {
        this.states.getLast().setAheadConsumed();
    }

    public MapIterable<String, CoreInstance> getTypeParameterToGenericType()
    {
        return this.states.getLast().getTypeParameters().toMap(p -> p, this::resolveTypeParameter);
    }

    public CoreInstance getTypeParameterValue(String parameter)
    {
        return containsTypeParameter(parameter) ? resolveTypeParameter(parameter) : null;
    }

    public MapIterable<String, CoreInstance> getMultiplicityParameterToMultiplicity()
    {
        return this.states.getLast().getMultiplicityParameters().toMap(p -> p, this::resolveMultiplicityParameter);
    }

    public CoreInstance getMultiplicityParameterValue(String parameter)
    {
        return containsMultiplicityParameter(parameter) ? resolveMultiplicityParameter(parameter) : null;
    }

    CoreInstance resolveTypeParameter(String parameter)
    {
        ParameterValueWithFlag value = this.states.getLast().getTypeParameterValueWithFlag(parameter);
        if (!org.finos.legend.pure.m3.navigation.generictype.GenericType.isGenericTypeConcrete(value.getParameterValue()))
        {
            String referencedName = org.finos.legend.pure.m3.navigation.generictype.GenericType.getTypeParameterName(value.getParameterValue());
            TypeInferenceContext referencedContext = value.getTargetGenericsContext();
            if (!this.equals(referencedContext) && referencedContext.containsTypeParameter(referencedName))
            {
                return referencedContext.resolveTypeParameter(referencedName);
            }
        }
        return value.getParameterValue();
    }

    CoreInstance resolveMultiplicityParameter(String parameter)
    {
        ParameterValueWithFlag value = this.states.getLast().getMultiplicityParameterValueWithFlag(parameter);
        if (!org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.isMultiplicityConcrete(value.getParameterValue()))
        {
            String referencedName = org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity.getMultiplicityParameter(value.getParameterValue());
            TypeInferenceContext referencedContext = value.getTargetGenericsContext();
            if (!this.equals(referencedContext) && referencedContext.containsMultiplicityParameter(referencedName))
            {
                return referencedContext.resolveMultiplicityParameter(referencedName);
            }
        }
        return value.getParameterValue();
    }

    boolean containsTypeParameter(String parameter)
    {
        return this.states.getLast().hasTypeParameter(parameter);
    }

    boolean containsMultiplicityParameter(String parameter)
    {
        return this.states.getLast().hasMultiplicityParameter(parameter);
    }

    public void setScope(CoreInstance scope)
    {
        this.scope = scope;
    }

    public TypeInferenceContext findParentForOperation(CoreInstance actualTemplateToInferColumnType)
    {
        TypeInferenceContextState state = this.states.getLast();
        for (String t : state.getTypeParameters())
        {
            if (org.finos.legend.pure.m3.navigation.generictype.GenericType.genericTypesEqual(state.getTypeParameterValueWithFlag(t).getParameterValue(), actualTemplateToInferColumnType, this.processorSupport))
            {
                return state.getTypeParameterValueWithFlag(t).getTargetGenericsContext().findParentForOperation(actualTemplateToInferColumnType);
            }
        }
        return this;
    }

    public void replace(CoreInstance actualTemplateToInferColumnType, GenericType instanceGenericType)
    {
        TypeInferenceContextState state = this.states.getLast();
        for (String t : state.getTypeParameters())
        {
            if (org.finos.legend.pure.m3.navigation.generictype.GenericType.genericTypesEqual(state.getTypeParameterValueWithFlag(t).getParameterValue(), actualTemplateToInferColumnType, this.processorSupport))
            {
                TypeInferenceContext trace = state.getTypeParameterValueWithFlag(t).getTargetGenericsContext();
                state.putTypeParameterValue(t, instanceGenericType, this, true);
                trace.replace(actualTemplateToInferColumnType, instanceGenericType);
            }
        }
    }

    private static class RegistrationRequest
    {
        private final TypeInferenceContext context;
        private final CoreInstance template;
        private final CoreInstance value;

        private RegistrationRequest(TypeInferenceContext context, CoreInstance template, CoreInstance value)
        {
            this.value = value;
            this.template = template;
            this.context = context;
        }
    }
}
