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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.type;

import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.GenericTypeOperation;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.function.FunctionType;
import org.finos.legend.pure.m3.navigation.generictype.GenericType;
import org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.navigation.type.Type;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.ProcessorContext;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.type._class.ClassProcessor;

public class TypeProcessor
{
    public static String typeToJavaPrimitiveWithMul(CoreInstance genericType, CoreInstance multiplicity, boolean typeParameter, ProcessorContext processorContext)
    {
        if (Multiplicity.isToZeroOrOne(multiplicity))
        {
            return typeToJavaObjectSingleWithMul(genericType, multiplicity, typeParameter, processorContext.getSupport());
        }
        else
        {
            return "RichIterable<? extends " + typeToJavaObjectSingle(genericType, typeParameter, processorContext.getSupport()) + ">";
        }
    }

    public static String typeToJavaObjectWithMul(CoreInstance genericType, CoreInstance multiplicity, ProcessorSupport processorSupport)
    {
        return typeToJavaObjectWithMul(genericType, multiplicity, true, processorSupport);
    }

    public static String typeToJavaObjectWithMul(CoreInstance genericType, CoreInstance multiplicity, boolean typeParam, ProcessorSupport processorSupport)
    {
        if (Multiplicity.isToZeroOrOne(multiplicity))
        {
            return typeToJavaObjectSingle(genericType, typeParam, processorSupport);
        }
        else
        {
            return "RichIterable<? extends " + typeToJavaObjectSingle(genericType, typeParam, processorSupport) + ">";
        }
    }

    public static String typeToJavaObjectSingleWithMul(CoreInstance genericType, CoreInstance multiplicity, ProcessorSupport processorSupport)
    {
        return typeToJavaObjectSingleWithMul(genericType, multiplicity, false, processorSupport);
    }

    public static String typeToJavaObjectSingleWithMul(CoreInstance genericType, CoreInstance multiplicity, boolean typeParam, ProcessorSupport processorSupport)
    {
        return pureTypeToJava(genericType, typeParam, Multiplicity.isToOne(multiplicity, true), processorSupport);
    }

    public static String typeToJavaPrimitiveSingle(CoreInstance genericType, ProcessorSupport processorSupport)
    {
        return pureTypeToJava(genericType, true, true, processorSupport);
    }

    public static String typeToJavaObjectSingle(CoreInstance genericType, boolean typeParam, ProcessorSupport processorSupport)
    {
        return pureTypeToJava(genericType, typeParam, false, processorSupport);
    }

    public static String pureTypeToJava(CoreInstance genericType, boolean typeParam, boolean primitiveIfPossible, ProcessorSupport processorSupport)
    {
        return pureTypeToJava(genericType, typeParam, primitiveIfPossible, true, processorSupport);
    }

    public static String pureTypeToJava(CoreInstance genericType, boolean typeParam, boolean primitiveIfPossible, boolean fullyQualify, ProcessorSupport processorSupport)
    {
        if (genericType instanceof GenericTypeOperation)
        {
            return "java.lang.Object";
        }
        CoreInstance rawType = Instance.getValueForMetaPropertyToOneResolved(genericType, M3Properties.rawType, processorSupport);
        if (rawType == null)
        {
            return typeParam ? GenericType.getTypeParameterName(genericType) : "java.lang.Object";
        }

        if ("RelationType".equals(processorSupport.getClassifier(rawType).getName()))
        {
            return "java.lang.Object";
        }

        if ("FunctionType".equals(processorSupport.getClassifier(rawType).getName()))
        {
            return "java.lang.Object";
        }

        if (Type.isExtendedPrimitiveType(rawType, processorSupport))
        {
            rawType = Type.findPrimitiveTypeFromExtendedPrimitiveType(rawType, processorSupport);
            primitiveIfPossible = false;
        }

        String javaType = pureSystemPathToJava_simpleCases(PackageableElement.getUserPathForPackageableElement(rawType), primitiveIfPossible);
        if (javaType != null)
        {
            return javaType;
        }
        if (processorSupport.instance_instanceOf(rawType, M3Paths.Enumeration))
        {
            return FullJavaPaths.Enum;
        }
        String finalRawTypeSystemPath = fullyQualify || M3Paths.Package.equals(rawType.getName()) ? fullyQualifiedJavaInterfaceNameForType(rawType, processorSupport) : javaInterfaceForType(rawType, processorSupport);

        // Manage magical TDS structures
        if ("org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.ColSpec".equals(finalRawTypeSystemPath) ||
                "org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.ColSpecArray".equals(finalRawTypeSystemPath) ||
                "org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.FuncColSpec".equals(finalRawTypeSystemPath) ||
                "org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.FuncColSpecArray".equals(finalRawTypeSystemPath) ||
                "org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.AggColSpec".equals(finalRawTypeSystemPath) ||
                "org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.AggColSpecArray".equals(finalRawTypeSystemPath))
        {
            CoreInstance typeArg = genericType.getValueForMetaPropertyToMany("typeArguments").getLast();
            if (typeArg != null)
            {
                typeArg.setKeyValues(Lists.mutable.with("rawType"), Lists.mutable.with(processorSupport.type_TopType()));
                typeArg.setKeyValues(Lists.mutable.with("typeArguments"), Lists.mutable.empty());
            }
        }

        return typeParam ? (finalRawTypeSystemPath + buildTypeArgumentsString(genericType, true, processorSupport)) : finalRawTypeSystemPath;
    }

    public static String pureRawTypeToJava(CoreInstance rawType, boolean primitiveIfPossible, ProcessorSupport processorSupport)
    {
        if (rawType == null)
        {
            return "java.lang.Object";
        }
        if (FunctionType.isFunctionType(rawType, processorSupport))
        {
            return "java.lang.Object";
        }
        String systemPath = fullyQualifiedJavaInterfaceNameForType(rawType, processorSupport);
        String javaType = pureSystemPathToJava_simpleCases(PackageableElement.getUserPathForPackageableElement(rawType), primitiveIfPossible);
        if (javaType != null)
        {
            return javaType;
        }
        if (processorSupport.instance_instanceOf(rawType, M3Paths.Enumeration))
        {
            return FullJavaPaths.Enum;
        }
        return systemPath;
    }


    public static String javaInterfaceForType(CoreInstance rawType, ProcessorSupport processorSupport)
    {
        return ClassProcessor.isPlatformClass(rawType) ? fullyQualifiedJavaInterfaceNameForType(rawType, processorSupport) : JavaPackageAndImportBuilder.buildInterfaceNameFromType(rawType, processorSupport);
    }

    public static String javaInterfaceNameForType(CoreInstance rawType, ProcessorSupport processorSupport)
    {
        return ClassProcessor.isPlatformClass(rawType) ? rawType.getName() : JavaPackageAndImportBuilder.buildInterfaceNameFromType(rawType, processorSupport);
    }

    public static String fullyQualifiedJavaInterfaceNameForType(CoreInstance element, ProcessorSupport processorSupport)
    {
        return JavaPackageAndImportBuilder.buildInterfaceReferenceFromType(element, processorSupport);
    }

    private static String pureSystemPathToJava_simpleCases(String fullUserPath, boolean primitiveIfPossible)
    {
        switch (fullUserPath)
        {
            case M3Paths.Any:
            case M3Paths.Nil:
            {
                return "java.lang.Object";
            }
            case M3Paths.Integer:
            {
                return primitiveIfPossible ? "long" : "java.lang.Long";
            }
            case M3Paths.Float:
            {
                return primitiveIfPossible ? "double" : "java.lang.Double";
            }
            case M3Paths.Decimal:
            {
                return "java.math.BigDecimal";
            }
            case M3Paths.Boolean:
            {
                return primitiveIfPossible ? "boolean" : "java.lang.Boolean";
            }
            case M3Paths.Date:
            {
                return "org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate";
            }
            case M3Paths.StrictDate:
            {
                return "org.finos.legend.pure.m4.coreinstance.primitive.date.StrictDate";
            }
            case M3Paths.StrictTime:
            {
                return "org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime";
            }
            case M3Paths.DateTime:
            {
                return "org.finos.legend.pure.m4.coreinstance.primitive.date.DateTime";
            }
            case M3Paths.LatestDate:
            {
                return "org.finos.legend.pure.m4.coreinstance.primitive.date.LatestDate";
            }
            case M3Paths.String:
            {
                return "java.lang.String";
            }
            case M3Paths.Number:
            {
                return "java.lang.Number";
            }
            case M3Paths.Map:
            {
                return "org.finos.legend.pure.runtime.java.compiled.generation.processors.support.map.PureMap";
            }
            case M3Paths.Byte:
            {
                return primitiveIfPossible ? "byte" : "java.lang.Byte";
            }
            default:
            {
                return null;
            }
        }
    }

    public static String buildTypeArgumentsString(CoreInstance genericType, boolean addExtends, final ProcessorSupport processorSupport)
    {
        ListIterable<? extends CoreInstance> typeArgs = Instance.getValueForMetaPropertyToManyResolved(genericType, M3Properties.typeArguments, processorSupport);
        return typeArgs.isEmpty() ? "" : "<" + (addExtends ? "? extends " : "") + typeArgs.collect(arg -> typeToJavaObjectSingle(arg, true, processorSupport)).makeString("," + (addExtends ? "? extends " : "")) + ">";
    }

    public static boolean isJavaPrimitivePossible(CoreInstance genericType, ProcessorSupport processorSupport)
    {
        CoreInstance rawType = Instance.getValueForMetaPropertyToOneResolved(genericType, M3Properties.rawType, processorSupport);
        if (rawType == null)
        {
            return false;
        }

        String rawTypeString = PackageableElement.getUserPathForPackageableElement(rawType);
        return M3Paths.Boolean.equals(rawTypeString) || M3Paths.Float.equals(rawTypeString) || M3Paths.Integer.equals(rawTypeString);
    }

    /**
     * Get default value allowing for Primitive types as per
     * <a href="https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html">Java spec</a>.
     *
     * @param rawType The Raw Type of an item
     * @return A Java-compatible String
     */
    public static String defaultValue(CoreInstance rawType)
    {
        if (rawType == null)
        {
            return "null";
        }

        switch (PackageableElement.getUserPathForPackageableElement(rawType, "."))
        {
            case M3Paths.Integer:
            {
                return "0L";
            }
            case M3Paths.Float:
            {
                return "0.0d";
            }
            case M3Paths.Boolean:
            {
                return "false";
            }
            default:
            {
                return "null";
            }
        }
    }
}
