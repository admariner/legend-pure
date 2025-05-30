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

package org.finos.legend.pure.m3.tests.projection;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.AnnotatedElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassProjection;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.SimpleFunctionExpression;
import org.finos.legend.pure.m3.exception.PureUnmatchedFunctionException;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.Printer;
import org.finos.legend.pure.m3.navigation.generictype.GenericType;
import org.finos.legend.pure.m3.navigation.profile.Profile;
import org.finos.legend.pure.m3.tests.AbstractPureTestWithCoreCompiledPlatform;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.exception.PureCompilationException;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestProjectionCompilation extends AbstractPureTestWithCoreCompiledPlatform
{
    @BeforeClass
    public static void setUp()
    {
        setUpRuntime(getExtra());
    }

    @After
    public void cleanRuntime()
    {
        runtime.delete("file.pure");
        runtime.delete("projection.pure");
        runtime.delete("model.pure");
        runtime.compile();
    }

    @Test
    public void testUnknownSourceClass()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Firm<T>\n" +
                        "{\n" +
                        "   employees : Person[1];\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Address\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects\n" +
                        "#\n" +
                        "    UnknownFirm\n" +
                        "          {\n " +
                        "             *\n" +
                        "          }\n" +
                        "#\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "UnknownFirm has not been defined!", "file.pure", 16, 5, e);
    }

    @Test
    public void testSourceTypeArgumentMismatch()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Firm<T>\n" +
                        "{\n" +
                        "   employees : Person[1];\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Address\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Class FirmProjection projects\n" +
                        "#Firm\n" +
                        "{\n" +
                        "   *\n" +
                        "}#\n" +
                        "\n" +
                        "function test():Any[*]\n" +
                        "{\n" +
                        "    print(FirmProjection, 1)\n" +
                        "}\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "Type argument mismatch for the class Firm<T> (expected 1, got 0): Firm", "file.pure", 15, 2, e);
    }

    @Test
    public void testUnknownProperty()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Firm<T>\n" +
                        "{\n" +
                        "   employees : Person[1];\n" +
                        "   address:Address[1];\n" +
                        "}\n" +
                        "Class Address\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Class FirmProjection projects\n" +
                        "#Firm{\n" +
                        "   +[employee]\n" +
                        "}#\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "The property 'employee' can't be found in the type 'Firm' (or any supertype).", "file.pure", 16, 6, e);
    }

    @Test
    public void testUnknownProperty2()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person{address:Address[1];} Class Firm {employees : Person[1];address:Address[1];} Class Address{}\n" +
                        "Class MyFirm projects \n #Firm\n" +
                        "{\n" +
                        "   employees \n" +
                        "               {\n" +
                        "                   address2 {} \n" +
                        "               }\n" +
                        "}\n" +
                        "#\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "The property 'address2' can't be found in the type 'Person' (or any supertype).", "file.pure", 7, 20, e);
    }

    @Test
    public void testComplexPropertiesNotAllowed()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person{address:Address[1];} Class Firm {employees : Person[1];address:Address[1];} Class Address{}\n" +
                        "Class MyFirm projects \n #Firm\n" +
                        "{\n" +
                        "   employees \n" +
                        "               {\n" +
                        "                   * \n" +
                        "               }" +
                        "}" +
                        "#\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "Invalid projection specification. Found complex property 'employees', only simple properties are allowed in a class projection.", "file.pure", 5, 4, e);
    }

    @Test
    public void testQualifiedPropertyWithMissingSimpleProperty()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person{ name: String[1]; nameWithTitle(title:String[1]){$title + $this.name}: String[1];address:Address[1]; firm: Firm[1];} Class Firm {employees : Person[1];address:Address[1];} Class Address{ street:String[1]; }\n" +
                        "Class PersonProjection projects #Person" +
                        "{\n" +
                        "      +[ nameWithTitle(String[1]) ]   \n" +
                        "}#");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "Error compiling projection 'PersonProjection'. Property 'nameWithTitle' cannot be resolved due to underlying cause: Can't find the property 'name' in the class PersonProjection", "file.pure", 2, 7, e);
    }

    @Test
    public void testProjectionOfProjection()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person{address:Address[1];} Class Firm {employees : Person[1];address:Address[1];} Class Address{}\n" +
                        "Class FirmProjection projects\n" +
                        "#Firm{\n" +
                        "   *  \n" +
                        "}#\n" +
                        "Class FirmProjectionSubClass extends FirmProjection {}");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "Class FirmProjection is a projection and cannot be extended", "file.pure", 6, 38, e);
    }

    @Test
    public void testSimpleClassProjection()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  yearsEmployed: Integer[1];\n" +
                        "  address: Address[1];\n" +
                        "  firm: Firm[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Firm\n" +
                        "{\n" +
                        "  employees: Person[1];\n" +
                        "  address: Address[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Address\n" +
                        "{\n" +
                        "  street:String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects #Person\n" +
                        "{\n" +
                        "   *\n" +
                        "}#\n");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("PersonProjection");

        Assert.assertNotNull(projection);
        Assert.assertEquals(
                Lists.mutable.with("name", "yearsEmployed"),
                projection._properties().collect(Property::_name, Lists.mutable.empty()).sortThis());
        // TODO should end on column 2
//        Assert.assertEquals(new SourceInformation("file.pure", 20, 1, 20, 7, 23, 2), projection.getSourceInformation());
        Assert.assertEquals(new SourceInformation("file.pure", 20, 1, 20, 7, 23, 1), projection.getSourceInformation());
    }

    @Test
    public void testSimpleClassProjectionWithoutDSL()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  yearsEmployed: Integer[1];\n" +
                        "  address: Address[1];\n" +
                        "  firm: Firm[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Firm\n" +
                        "{\n" +
                        "  employees: Person[1];\n" +
                        "  address: Address[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Address\n" +
                        "{\n" +
                        "  street: String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects Person\n" +
                        "{\n" +
                        "   *\n" +
                        "}");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("PersonProjection");

        Assert.assertNotNull(projection);
        Assert.assertEquals(
                Lists.mutable.with("name", "yearsEmployed"),
                projection._properties().collect(Property::_name, Lists.mutable.empty()).sortThis());
        Assert.assertEquals(new SourceInformation("file.pure", 20, 1, 20, 7, 23, 1), projection.getSourceInformation());
    }

    @Test
    public void testClassProjectionWithAnnotations()
    {
        runtime.createInMemorySource("file.pure",
                "Profile TPP\n" +
                        "{\n" +
                        "   stereotypes:[Root, ExistingProperty, DerivedProperty, SimpleProperty];\n" +
                        "   tags: [name, description];\n" +
                        "}\n" +
                        "\n" +
                        "Class {TPP.name='Person Class'} Person\n" +
                        "{\n" +
                        "  {TPP.name = 'name prop'} name: String[1];\n" +
                        "  <<TPP.SimpleProperty>> nameWithPrefix(prefix:String[1])\n" +
                        "  {\n" +
                        "     $prefix + ' ' + $this.name;\n" +
                        "  }:String[1];\n" +
                        "  yearsEmployed: Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects #Person <<TPP.Root>> {TPP.description = 'Person Class Projection'}\n" +
                        "{\n" +
                        "   +[name {TPP.description='Full Name'}, nameWithPrefix(String[1]) <<TPP.ExistingProperty>>]\n" +
                        "}#");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(projection);

        // TODO should end on column 2
//        Assert.assertEquals(new SourceInformation("file.pure", 17, 1, 17, 7, 20, 2), projection.getSourceInformation());
        Assert.assertEquals(new SourceInformation("file.pure", 17, 1, 17, 7, 20, 1), projection.getSourceInformation());

        CoreInstance tppProfile = runtime.getCoreInstance("TPP");
        Assert.assertNotNull(tppProfile);

        CoreInstance rootST = Profile.findStereotype(tppProfile, "Root");
        CoreInstance existingPropertyST = Profile.findStereotype(tppProfile, "ExistingProperty");
        CoreInstance derivedPropertyST = Profile.findStereotype(tppProfile, "DerivedProperty");
        CoreInstance simplePropertyST = Profile.findStereotype(tppProfile, "SimpleProperty");
        CoreInstance nameTag = Profile.findTag(tppProfile, "name");
        CoreInstance descriptionTag = Profile.findTag(tppProfile, "description");
        Assert.assertNotNull(rootST);
        Assert.assertNotNull(existingPropertyST);
        Assert.assertNotNull(derivedPropertyST);
        Assert.assertNotNull(simplePropertyST);
        Assert.assertNotNull(nameTag);
        Assert.assertNotNull(descriptionTag);

        Property<?, ?> nameProp = projection._properties().detect(p -> "name".equals(p._name()));
        QualifiedProperty<?> nameWithPrefixProp = projection._qualifiedProperties().getOnly();
        Assert.assertNotNull(nameProp);
        Assert.assertNotNull(nameWithPrefixProp);

        validateAnnotations(projection, Sets.mutable.with(rootST), Maps.mutable.with(nameTag, Sets.mutable.with("Person Class"), descriptionTag, Sets.mutable.with("Person Class Projection")));
        validateAnnotations(nameProp, Sets.mutable.empty(), Maps.mutable.with(descriptionTag, Sets.mutable.with("Full Name"), nameTag, Sets.mutable.with("name prop")));
        validateAnnotations(nameWithPrefixProp, Sets.mutable.with(existingPropertyST, simplePropertyST), Maps.mutable.empty());
    }

    @Test
    public void testClassProjectionWithoutDSLWithAnnotations()
    {
        runtime.createInMemorySource("file.pure",
                "Profile TPP\n" +
                        "{\n" +
                        "   stereotypes:[Root, ExistingProperty, DerivedProperty, SimpleProperty];\n" +
                        "   tags: [name, description];\n" +
                        "}\n" +
                        "\n" +
                        "Class {TPP.name='Person Class'} Person\n" +
                        "{\n" +
                        "  {TPP.name = 'name prop'} name: String[1];\n" +
                        "  <<TPP.SimpleProperty>> nameWithPrefix(prefix:String[1])\n" +
                        "  {\n" +
                        "    $prefix + ' ' + $this.name;\n" +
                        "  }:String[1];\n" +
                        "  yearsEmployed: Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects Person <<TPP.Root>> {TPP.description = 'Person Class Projection'}\n" +
                        "{\n" +
                        "   +[name {TPP.description='Full Name'}, nameWithPrefix(String[1]) <<TPP.ExistingProperty>>]\n" +
                        "}");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(projection);

        Assert.assertEquals(new SourceInformation("file.pure", 17, 1, 17, 7, 20, 1), projection.getSourceInformation());

        CoreInstance tppProfile = runtime.getCoreInstance("TPP");
        Assert.assertNotNull(tppProfile);

        CoreInstance rootST = Profile.findStereotype(tppProfile, "Root");
        CoreInstance existingPropertyST = Profile.findStereotype(tppProfile, "ExistingProperty");
        CoreInstance derivedPropertyST = Profile.findStereotype(tppProfile, "DerivedProperty");
        CoreInstance simplePropertyST = Profile.findStereotype(tppProfile, "SimpleProperty");
        CoreInstance nameTag = Profile.findTag(tppProfile, "name");
        CoreInstance descriptionTag = Profile.findTag(tppProfile, "description");
        Assert.assertNotNull(rootST);
        Assert.assertNotNull(existingPropertyST);
        Assert.assertNotNull(derivedPropertyST);
        Assert.assertNotNull(simplePropertyST);
        Assert.assertNotNull(nameTag);
        Assert.assertNotNull(descriptionTag);

        Property<?, ?> nameProp = projection._properties().detect(p -> "name".equals(p._name()));
        QualifiedProperty<?> nameWithPrefixProp = projection._qualifiedProperties().getOnly();
        Assert.assertNotNull(nameProp);
        Assert.assertNotNull(nameWithPrefixProp);

        validateAnnotations(projection, Sets.mutable.with(rootST), Maps.mutable.with(nameTag, Sets.mutable.with("Person Class"), descriptionTag, Sets.mutable.with("Person Class Projection")));
        validateAnnotations(nameProp, Sets.mutable.empty(), Maps.mutable.with(descriptionTag, Sets.mutable.with("Full Name"), nameTag, Sets.mutable.with("name prop")));
        validateAnnotations(nameWithPrefixProp, Sets.mutable.with(existingPropertyST, simplePropertyST), Maps.mutable.empty());
    }

    private void validateAnnotations(AnnotatedElement instance, MutableSet<CoreInstance> expectedStereotypes, MutableMap<CoreInstance, MutableSet<String>> expectedTaggedValues)
    {
        // Check that we have the expected stereotypes
        Assert.assertEquals(expectedStereotypes, Sets.mutable.withAll(instance._stereotypes()));

        // Check that we have the expected tagged values
        MutableMap<CoreInstance, MutableSet<String>> actualTaggedValues = Maps.mutable.empty();
        instance._taggedValues().forEach(tv -> actualTaggedValues.getIfAbsentPut(tv._tag(), Sets.mutable::empty).add(tv._value()));
        Assert.assertEquals(expectedTaggedValues, actualTaggedValues);

        // Check that the stereotypes and tags have the appropriate model elements
        for (CoreInstance stereotype : expectedStereotypes)
        {
            ListIterable<? extends CoreInstance> modelElements = Instance.getValueForMetaPropertyToManyResolved(stereotype, M3Properties.modelElements, processorSupport);
            if (!modelElements.contains(instance))
            {
                Assert.fail("model elements for " + stereotype + " did not contain " + instance);
            }
        }
        for (CoreInstance tag : expectedTaggedValues.keysView())
        {
            ListIterable<? extends CoreInstance> modelElements = Instance.getValueForMetaPropertyToManyResolved(tag, M3Properties.modelElements, processorSupport);
            if (!modelElements.contains(instance))
            {
                Assert.fail("model elements for " + tag + " did not contain " + instance);
            }
        }
    }

    @Test
    public void testClassProjectionFlattening()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  yearsEmployed: Integer[1];\n" +
                        "  address: Address[1];\n" +
                        "  firm: Firm[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Firm\n" +
                        "{\n" +
                        "  employees: Person[1];\n" +
                        "  address: Address[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class Address\n" +
                        "{\n" +
                        "  street: String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class PersonProjection projects #Person\n" +
                        "{\n" +
                        "   *\n" +
                        "   >address [$this.address.street]\n" +
                        "}#");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("PersonProjection");

        Assert.assertNotNull(projection);
        Assert.assertEquals(
                Lists.mutable.with("address", "name", "yearsEmployed"),
                projection._properties().collect(Property::_name, Lists.mutable.empty()).sortThis());
        // TODO should end on column 2
//        Assert.assertEquals(new SourceInformation("file.pure", 20, 1, 20, 7, 24, 2), projection.getSourceInformation());
        Assert.assertEquals(new SourceInformation("file.pure", 20, 1, 20, 7, 24, 1), projection.getSourceInformation());
    }

    @Test
    public void testClassProjectionWithFunctionRecompile()
    {
        runtime.createInMemorySource("file.pure",
                "Class demo::A\n" +
                        "{\n" +
                        "    name()\n" +
                        "    {\n" +
                        "       $this->printName()->toOne();\n" +
                        "    }:String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class demo::AP projects\n" +
                        "#demo::A{\n" +
                        "  +[name()]\n" +
                        "}\n" +
                        "#\n" +
                        "\n" +
                        "function printName(a:demo::A[1]):String[*]\n" +
                        "{   \n" +
                        "   $a->type().name;\n" +
                        "}\n" +
                        "\n" +
                        "function printName(a:demo::AP[1]):String[*]\n" +
                        "{\n" +
                        "   'Projection ' +  $a->type().name->toOne();\n" +
                        "}");
        runtime.compile();

        ClassProjection<?> projection = (ClassProjection<?>) runtime.getCoreInstance("demo::AP");
        Assert.assertNotNull(projection);
        // TODO should end on line 13
//        Assert.assertEquals(new SourceInformation("file.pure", 9, 1, 9, 13, 13, 1), projection.getSourceInformation());
        Assert.assertEquals(new SourceInformation("file.pure", 9, 1, 9, 13, 12, 1), projection.getSourceInformation());

        Assert.assertEquals(Lists.mutable.with("name()"), projection._qualifiedProperties().collect(QualifiedProperty::_id, Lists.mutable.empty()));

        String expressionVariableGenericType = GenericType.print(((SimpleFunctionExpression) ((SimpleFunctionExpression) projection._qualifiedProperties().getOnly()._expressionSequence().getOnly())._parametersValues().getOnly())
                ._parametersValues().getOnly()._genericType(), true, processorSupport);

        Assert.assertEquals("demo::AP", expressionVariableGenericType);
    }

    @Test
    public void testClassProjectionWithFunctionRecompileExceptionScenario()
    {
        runtime.createInMemorySource("file.pure", "Class demo::A\n" +
                "{\n" +
                "    name()\n" +
                "    {\n" +
                "       $this->printName()->toOne();\n" +
                "    }: String[1];\n" +
                "}\n" +
                "Class demo::AP projects \n" +
                "#demo::A{\n" +
                "  +[name()]" +
                "}\n" +
                "#\n" +
                "\n" +
                "function printName(a:demo::A[1]):String[*]\n" +
                "{   \n" +
                "   $a->type().name;\n" +
                "}\n" +
                "\n");
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class,
                "Error compiling projection 'demo::AP'. Property 'name' cannot be resolved due to underlying cause: " + PureUnmatchedFunctionException.FUNCTION_UNMATCHED_MESSAGE + "printName(_:AP[1])\n" +
                        PureUnmatchedFunctionException.NONEMPTY_CANDIDATES_WITH_PACKAGE_IMPORTED_MESSAGE +
                        "\tprintName(A[1]):String[*]\n" +
                        PureUnmatchedFunctionException.EMPTY_CANDIDATES_WITH_PACKAGE_NOT_IMPORTED_MESSAGE,
                "file.pure", 8, 13, e);
    }

    @Test
    public void testSimpleProjectionWithQualifiedProperties()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; nameWithTitle(title:String[1]){$title + $this.name}: String[1];address:Address[1]; firm: Firm[1];} Class Firm {employees : Person[1];address:Address[1];} Class Address{ street:String[1]; }\n" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ name, nameWithTitle(String[1]) ]   \n" +
                "}#");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("nameWithTitle", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testSimpleProjectionWithQualifiedPropertiesIntLiteral()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; propInt(){1}: Integer[1];}" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ name, propInt() ]   \n" +
                "}#");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("propInt", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testSimpleProjectionWithQualifiedPropertiesBooleanLiteral()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; propBool(){true}: Boolean[1];}" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ propBool() ]   \n" +
                "}#");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("propBool", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testSimpleProjectionWithQualifiedPropertiesDateLiteral()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; propDate(){%12-12-12}: Date[1];}" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ propDate() ]   \n" +
                "}#");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("propDate", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testSimpleProjectionWithQualifiedPropertiesEnumLiteral()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; propEnum(){NumNum.Cookie}: NumNum[1];}" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ propEnum() ]   \n" +
                "}#" +
                "\n" +
                "Enum NumNum" +
                "{" +
                "   Cookie, Monster" +
                "}");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("propEnum", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testSimpleProjectionWithQualifiedPropertiesLiteralArrayAny()
    {
        runtime.createInMemorySource("file.pure", "Class Person{ name: String[1]; propManyAny(){[NumNum.Cookie, 1 , true, %12-12-12, 'pi', 3.14]}: Any[*];}" +
                "Class PersonProjection projects #Person" +
                "{\n" +
                "      +[ propManyAny() ]   \n" +
                "}#" +
                "\n" +
                "Enum NumNum" +
                "{" +
                "   Cookie, Monster" +
                "}");
        runtime.compile();
        CoreInstance tree = runtime.getCoreInstance("PersonProjection");
        Assert.assertNotNull(tree);
        RichIterable<? extends CoreInstance> children = Instance.getValueForMetaPropertyToManyResolved(tree, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals("Missing properties", 1, children.size());
        Assert.assertEquals("propManyAny", children.getFirst().getValueForMetaPropertyToOne("name").getName());
    }

    @Test
    public void testMultipleParameters()
    {
        runtime.createInMemorySource("file.pure",
                "Class Person\n" +
                        "{\n" +
                        "    firstName : String[1];\n" +
                        "    lastName : String[1];\n" +
                        "    nameWithTitle(title:String[1]){$title+' '+$this.firstName+' '+$this.lastName}:String[1];" +
                        "    nameWithPrefixAndSuffix(prefix:String[0..1], suffixes:String[*])\n" +
                        "    {\n" +
                        "        if($prefix->isEmpty(),\n" +
                        "           | if($suffixes->isEmpty(),\n" +
                        "                | $this.firstName + ' ' + $this.lastName,\n" +
                        "                | $this.firstName + ' ' + $this.lastName + ', ' + $suffixes->joinStrings(', ')),\n" +
                        "           | if($suffixes->isEmpty(),\n" +
                        "                | $prefix->toOne() + ' ' + $this.firstName + ' ' + $this.lastName,\n" +
                        "                | $prefix->toOne() + ' ' + $this.firstName + ' ' + $this.lastName + ', ' + $suffixes->joinStrings(', ')))\n" +
                        "    }:String[1];" +
                        "    memberOf(org:Organization[1]){true}:Boolean[1];" +
                        "}\n" +
                        "Class Organization\n" +
                        "{\n" +
                        "}" +
                        "Class Team extends Organization\n" +
                        "{\n" +
                        "}");

        runtime.createInMemorySource("projection.pure",
                "Class PersonProjection projects #Person\n" +
                        "{-[nameWithPrefixAndSuffix(String[0..1], String[*])]}" +
                        "#\n");
        runtime.compile();

        CoreInstance projection = runtime.getCoreInstance("PersonProjection");

        Assert.assertNotNull(projection);
        RichIterable<? extends CoreInstance> properties = Instance.getValueForMetaPropertyToManyResolved(projection, M3Properties.properties, processorSupport);
        Assert.assertEquals(2, properties.size());
        RichIterable<? extends CoreInstance> qualifiedProperties = Instance.getValueForMetaPropertyToManyResolved(projection, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals(2, qualifiedProperties.size());
    }

    @Test
    public void testClassProjectionWithQualifiedPropertyBoundToOtherType()
    {
        runtime.createInMemorySource("file.pure", "import meta::pure::tests::model::simple::*;\n" +
                "Class meta::pure::tests::model::simple::Trade\n" +
                "{\n" +
                "   id : Integer[1];\n" +
                "   date : Date[1];\n" +
                "   quantity : Float[1];\n" +
                "   settlementDateTime : Date[0..1];\n" +
                "   latestEventDate : Date[0..1];\n" +
                "\n" +
                "   customerQuantity()\n" +
                "   {\n" +
                "      $this.quantity + $this.quantity;\n" +
                "   }:Float[1];\n" +
                "   \n" +
                "   daysToLastEvent()\n" +
                "   {\n" +
                "      dateDiff($this.latestEventDate->toOne(), $this.date, DurationUnit.DAYS);\n" +
                "   }:Integer[1];\n" +
                "   \n" +
                "   latestEvent()\n" +
                "   {\n" +
                "      $this.events->filter(e | $e.date == $this.latestEventDate)->toOne()\n" +
                "   }:TradeEvent[1];\n" +
                "   \n" +
                "   eventsByDate(date:Date[1])\n" +
                "   {\n" +
                "      $this.events->filter(e | $e.date == $date)\n" +
                "   }:TradeEvent[*];\n" +
                "   \n" +
                "   tradeDateEventType()\n" +
                "   {\n" +
                "      $this.eventsByDate($this.date->toOne()).eventType->toOne()\n" +
                "   }:String[1];\n" +
                "   \n" +
                "   tradeDateEventTypeInlined()\n" +
                "   {\n" +
                "      $this.events->filter(e | $e.date == $this.date).eventType->toOne()\n" +
                "   }:String[1];\n" +
                "}\n" +
                "\n" +
                "Class meta::pure::tests::model::simple::TradeEvent\n" +
                "{\n" +
                "   eventType : String[0..1];\n" +
                "   date: Date[1];\n" +
                "}\n" +
                "Class meta::pure::tests::model::simple::TradeProjection projects \n" +
                "#\n" +
                "   Trade\n" +
                "   {\n" +
                "      -[tradeDateEventType()]\n" +
                "   }\n" +
                "#\n" +
                "\n" +
                "Class meta::pure::tests::model::simple::TradeEventProjection projects \n" +
                "#\n" +
                "   TradeEvent\n" +
                "   {\n" +
                "      *\n" +
                "   }\n" +
                "#\n" +
                "\n" +
                "Association meta::pure::tests::model::simple::TP_TEP projects meta::pure::tests::model::simple::Trade_TradeEvent<meta::pure::tests::model::simple::TradeProjection, meta::pure::tests::model::simple::TradeEventProjection>\n" +
                "Association meta::pure::tests::model::simple::Trade_TradeEvent \n" +
                "{\n" +
                "   trade:  Trade[*];\n" +
                "   events: TradeEvent [*];\n" +
                "}\n");
        runtime.compile();
        CoreInstance tradeProjection = runtime.getCoreInstance("meta::pure::tests::model::simple::TradeProjection");
        Assert.assertNotNull(tradeProjection);
        RichIterable<? extends CoreInstance> properties = Instance.getValueForMetaPropertyToManyResolved(tradeProjection, M3Properties.properties, processorSupport);
        Assert.assertEquals(5, properties.size());
        RichIterable<? extends CoreInstance> qualifiedProperties = Instance.getValueForMetaPropertyToManyResolved(tradeProjection, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals(3, qualifiedProperties.size());
    }

    @Test
    public void testProjectionWithNonResolvableQualifiedProperties()
    {
        runtime.createInMemorySource("file.pure", "import meta::pure::tests::model::simple::*;\n" +
                "import meta::pure::tests::model::simple::projection::*;\n" +
                "\n" +
                "\n" +
                "native function average(s:Number[*]):Float[1];\n" +
                "native function sum(s:Integer[*]):Integer[1];\n" +
                "Class meta::pure::tests::model::simple::projection::EntityWithAddressProjection projects\n" +
                "#\n" +
                "EntityWithAddress\n" +
                "{\n" +
                "    > address [$this.address.name] \n" +
                "}\n" +
                "#\n" +
                "\n" +
                "\n" +
                "Class meta::pure::tests::model::simple::projection::FirmProjection projects\n" +
                "#\n" +
                "meta::pure::tests::model::simple::Firm\n" +
                "{\n" +
                "   *\n" +
                "}\n" +
                "#");
        runtime.createInMemorySource("model.pure", testModel);
        PureCompilationException e = Assert.assertThrows(PureCompilationException.class, runtime::compile);
        assertPureException(PureCompilationException.class, "Error compiling projection 'meta::pure::tests::model::simple::projection::FirmProjection'. Property 'nameAndAddress' cannot be resolved due to underlying cause: Can't find the property 'address' in the class meta::pure::tests::model::simple::projection::FirmProjection", "file.pure", 16, 53, e);
    }

    @Test
    public void testPrintFlattenedProperty()
    {
        runtime.createInMemorySource("file.pure", "import meta::pure::tests::model::simple::*;\n" +
                "import meta::pure::tests::model::simple::projection::*;\n" +
                "\n" +
                "native function average(s:Number[*]):Float[1];\n" +
                "native function sum(s:Integer[*]):Integer[1];\n" +
                "Class meta::pure::tests::model::simple::projection::EntityWithAddressProjection projects\n" +
                "#\n" +
                "EntityWithAddress\n" +
                "{\n" +
                "    > address [$this.address.name] \n" +
                "}\n" +
                "#\n" +
                "\n" +
                "function testPrint():Any[*]" +
                "{" +
                "   print(EntityWithAddressProjection,1);" +
                "}");
        runtime.createInMemorySource("model.pure", testModel);
        runtime.compile();
        CoreInstance projection = runtime.getCoreInstance("meta::pure::tests::model::simple::projection::EntityWithAddressProjection");
        Printer.print(projection, runtime.getProcessorSupport());

        Assert.assertNotNull(projection);
        RichIterable<? extends CoreInstance> properties = Instance.getValueForMetaPropertyToManyResolved(projection, M3Properties.properties, processorSupport);
        Assert.assertEquals(1, properties.size());
    }

    @Test
    public void testProjectionWithQualifiedPropertyInclude()
    {
        runtime.createInMemorySource("projection.pure", "import meta::pure::tests::model::simple::*;" +
                "import meta::pure::tests::model::simple::projection::*;\n" +
                "native function average(s:Number[*]):Float[1];\n" +
                "native function sum(s:Integer[*]):Integer[1];\n" +
                "Class meta::pure::tests::model::simple::projection::FirmProjection projects\n" +
                "meta::pure::tests::model::simple::Firm\n" +
                "{\n" +
                "   +[legalName,sumEmployeesAge]\n" +
                "}\n" +
                "\n" +
                "Class meta::pure::tests::model::simple::projection::PersonProjection projects\n" +
                "meta::pure::tests::model::simple::Person\n" +
                "{\n" +
                "   +[firstName, lastName, age, name()]\n" +
                "   >employerName [$this.firm.legalName]\n" +
                "}\n" +
                "\n" +
                "Class meta::pure::tests::model::simple::projection::AddressProjection projects\n" +
                "Address\n" +
                "{\n" +
                "   *\n" +
                "}\n" +
                "\n" +
                "Association meta::pure::tests::model::simple::projection::EmploymentProjection projects Employment<FirmProjection, PersonProjection> \n");
        runtime.createInMemorySource("model.pure", testModel);
        runtime.compile();
        CoreInstance projection = runtime.getCoreInstance("meta::pure::tests::model::simple::projection::PersonProjection");
        Assert.assertNotNull(projection);
        RichIterable<? extends CoreInstance> properties = Instance.getValueForMetaPropertyToManyResolved(projection, M3Properties.properties, processorSupport);
        RichIterable<? extends CoreInstance> qProperties = Instance.getValueForMetaPropertyToManyResolved(projection, M3Properties.qualifiedProperties, processorSupport);
        Assert.assertEquals(4, properties.size());
        Assert.assertEquals(1, qProperties.size());
    }

    private static final String testModel = "import meta::pure::profiles::*;\n" +
            "import meta::pure::tests::model::simple::*;\n" +
            "\n" +
            "native function in(s:String[1],c:String[*]):Boolean[1];\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::EntityWithAddress\n" +
            "{\n" +
            "    address : Address[0..1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::EntityWithLocations\n" +
            "{\n" +
            "    locations : Location[*];\n" +
            "    locationsByType(types:GeographicEntityType[*])\n" +
            "    {\n" +
            "        $this.locations->filter(l | $types->exists(type | is($l.type, $type)))\n" +
            "    }:Location[*];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Firm extends EntityWithAddress\n" +
            "{\n" +
            "    <<equality.Key>> legalName : String[1];\n" +
            "    averageEmployeesAge(){$this.employees.age->average()*2.0}:Float[1];\n" +
            "    sumEmployeesAge(){$this.employees.age->sum()}:Integer[1];\n" +
            "\n" +
            "    nameAndAddress(){\n" +
            "       $this.legalName + ',' + $this.address.name->toOne();\n" +
            "    }:String[1];\n" +
            "\n" +
            "    isFirmX(){\n" +
            "       if ($this.legalName->toOne() == 'FirmX', | 'Yes', | 'No')\n" +
            "    }:String[1];\n" +
            "    \n" +
            "    nameAndMaskedAddress(){\n" +
            "       if ($this.legalName == 'FirmX', | $this.legalName + ' , LegalFirm', |  $this.legalName + ',' + $this.address.name->toOne())\n" +
            "    }:String[1];\n" +
            "\n" +
            "    employeeByLastName(lastName:String[1]){$this.employees->filter(e|$e.lastName == $lastName)->toOne()}:Person[0..1];\n" +
            "\n" +
            "    employeesByAge(age:Integer[1]){$this.employees->filter(e|$e.age->toOne() < $age)}:Person[*];\n" +
            "\n" +
            "    employeesByCityOrManager(city:String[1], managerName:String[1]){$this.employees->filter(e|$e.address.name == $city || $e.manager.name == $managerName)}:Person[*];\n" +
            "\n" +
            "    employeesByCityOrManagerAndLastName(name:String[1], city:String[1], managerName:String[1]){$this.employees->filter(e|$e.lastName == $name && ($e.address.name == $city || $e.manager.name == $managerName))->toOne()}:Person[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::PersonNameParameter\n" +
            "{\n" +
            "   lastNameFirst:Boolean[1];\n" +
            "   nested:PersonNameParameterNested[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::PersonNameParameterNested\n" +
            "{\n" +
            "   prefix:String[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Person extends EntityWithAddress, EntityWithLocations\n" +
            "{\n" +
            "    firstName : String[1];\n" +
            "    lastName : String[1];\n" +
            "    otherNames : String[*];\n" +
            "    name(){$this.firstName+' '+$this.lastName}:String[1];\n" +
            "    nameWithTitle(title:String[1]){$title+' '+$this.firstName+' '+$this.lastName}:String[1];\n" +
            "    nameWithPrefixAndSuffix(prefix:String[0..1], suffixes:String[*])\n" +
            "    {\n" +
            "        if($prefix->isEmpty(),\n" +
            "           | if($suffixes->isEmpty(),\n" +
            "                | $this.firstName + ' ' + $this.lastName,\n" +
            "                | $this.firstName + ' ' + $this.lastName + ', ' + $suffixes->joinStrings(', ')),\n" +
            "           | if($suffixes->isEmpty(),\n" +
            "                | $prefix->toOne() + ' ' + $this.firstName + ' ' + $this.lastName,\n" +
            "                | $prefix->toOne() + ' ' + $this.firstName + ' ' + $this.lastName + ', ' + $suffixes->joinStrings(', ')))\n" +
            "    }:String[1];\n" +
            "\n" +
            "    fullName(lastNameFirst:Boolean[1])\n" +
            "    {\n" +
            "        if($lastNameFirst, | $this.lastName + ', ' + $this.firstName, | $this.firstName + ' ' + $this.lastName)\n" +
            "    }:String[1];\n" +
            "\n" +
            "    parameterizedName(personNameParameter:PersonNameParameter[1])\n" +
            "    {\n" +
            "        if($personNameParameter.lastNameFirst, | $personNameParameter.nested.prefix+' '+$this.lastName + ', ' + $this.firstName, | $this.firstName + ' ' + $this.lastName)\n" +
            "    }:String[1];\n" +
            "\n" +
            "    allOrganizations()\n" +
            "    {\n" +
            "        concatenate($this.organizations, $this.organizations->map(o | $o.superOrganizations()))->removeDuplicates()\n" +
            "    }:Organization[*];\n" +
            "    extraInformation : String[0..1];\n" +
            "    manager : Person[0..1];\n" +
            "    age : Integer[0..1];\n" +
            "    constant() { 'constant' } : String[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Interaction\n" +
            "{\n" +
            "   id : String[1];\n" +
            "   source : Person[0..1];\n" +
            "   target : Person[0..1];\n" +
            "   active : Boolean[1];\n" +
            "   time : Integer[1];\n" +
            "   longestInteractionBetweenSourceAndTarget : Integer[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::GeographicEntity\n" +
            "{\n" +
            "    type : GeographicEntityType[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Location extends GeographicEntity\n" +
            "{\n" +
            "    place : String[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Address extends GeographicEntity\n" +
            "{\n" +
            "    name : String[1];\n" +
            "}\n" +
            "\n" +
            "Enum meta::pure::tests::model::simple::GeographicEntityType\n" +
            "{\n" +
            "    {doc.doc = 'A city, town, village, or other urban area.'} CITY,\n" +
            "    <<doc.deprecated>> COUNTRY,\n" +
            "    {doc.doc = 'Any geographic entity other than a city or country.'} REGION\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Organization\n" +
            "{\n" +
            "    name : String[1];\n" +
            "    superOrganizations()\n" +
            "    {\n" +
            "        let parent = $this.parent;\n" +
            "        if($parent->isEmpty(), |[], |concatenate($parent, $parent->toOne().superOrganizations()));\n" +
            "    }:Organization[*];\n" +
            "    subOrganizations()\n" +
            "    {\n" +
            "        concatenate($this.children, $this.children->map(c | $c.subOrganizations()))->removeDuplicates()\n" +
            "    }:Organization[*];\n" +
            "    child(name:String[1])\n" +
            "    {\n" +
            "        $this.children->filter(c | $c.name == $name)->toOne()\n" +
            "    }:Organization[1];\n" +
            "    allMembers()\n" +
            "    {\n" +
            "        concatenate($this.members, $this.subOrganizations()->map(o | $o.members))->removeDuplicates()\n" +
            "    }:Person[*];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Division extends Organization\n" +
            "{\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Department extends Organization\n" +
            "{\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Team extends Organization\n" +
            "{\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::Employment\n" +
            "{\n" +
            "    firm : Firm[0..1];\n" +
            "    employees : Person[*];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::FirmOrganizations\n" +
            "{\n" +
            "    firm : Firm[1];\n" +
            "    organizations : Organization[*];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::SubOrganization\n" +
            "{\n" +
            "    parent : Organization[0..1];\n" +
            "    children : Organization[*];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::Membership\n" +
            "{\n" +
            "    organizations : Organization[*];\n" +
            "    members : Person[*];\n" +
            "}\n" +
            "\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Product\n" +
            "{\n" +
            "   name : String[1];\n" +
            "   synonymByType(type:ProductSynonymType[1]){$this.synonyms->filter(s|$s.type == $type)->toOne()}:Synonym[1];\n" +
            "   cusip(){$this.synonymByType(ProductSynonymType.CUSIP).name}:String[1];\n" +
            "   isin(){$this.synonymByType(ProductSynonymType.ISIN).name}:String[1];\n" +
            "   cusipSynonym(){$this.synonymByType(ProductSynonymType.CUSIP)}:Synonym[1];\n" +
            "   isinSynonym(){$this.synonymByType(ProductSynonymType.ISIN)}:Synonym[1];\n" +
            "   classification : ProductClassification[0..1];\n" +
            "}\n" +
            "\n" +
            "Class <<temporal.businesstemporal>> meta::pure::tests::model::simple::ProductClassification{\n" +
            "   type : String[1];\n" +
            "   description : String[1];\n" +
            "}\n" +
            "\n" +
            "Enum meta::pure::tests::model::simple::ProductSynonymType\n" +
            "{\n" +
            "   CUSIP,\n" +
            "   ISIN,\n" +
            "   GSN\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Synonym\n" +
            "{\n" +
            "   typeAsString : String[1];\n" +
            "   type : ProductSynonymType[1];\n" +
            "   name : String[1];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::ProdSynonym\n" +
            "{\n" +
            "   synonyms : Synonym[*];\n" +
            "   product : Product[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Account\n" +
            "{\n" +
            "   name : String[1];\n" +
            "   createDate : Date[1];\n" +
            "   \n" +
            "   accountCategory(){\n" +
            "      if ( $this.name->in(['Account 1', 'Account 2']), | 'A', | 'B')\n" +
            "   }:String[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Trade\n" +
            "{\n" +
            "   id : Integer[1];\n" +
            "   date : Date[1];\n" +
            "   quantity : Float[1];\n" +
            "   product : Product[0..1];\n" +
            "   settlementDateTime : Date[0..1];\n" +
            "   latestEventDate : Date[0..1];\n" +
            "   events: TradeEvent[*];\n" +
            "\n" +
            "   productIdentifier()\n" +
            "   {\n" +
            "       $this.product.cusip->toOne();\n" +
            "   }:String[1];\n" +
            "   \n" +
            "      \n" +
            "\n" +
            "\n" +
            "   productDescription()\n" +
            "   {\n" +
            "      if ($this.product->isEmpty(), | 'Unknown', | $this.product.name->toOne())\n" +
            "   }:String[1];\n" +
            "     \n" +
            "   accountDescription()\n" +
            "   {\n" +
            "     $this.account.name->toOne();\n" +
            "   }:String[1];\n" +
            "   \n" +
            "   productIdentifierWithNull()\n" +
            "   {\n" +
            "      $this.product.cusip;\n" +
            "   }:String[0..1];\n" +
            "\n" +
            "   customerQuantity()\n" +
            "   {\n" +
            "      -$this.quantity;\n" +
            "   }:Float[1];\n" +
            "   \n" +
            "   daysToLastEvent()\n" +
            "   {\n" +
            "      dateDiff($this.latestEventDate->toOne(), $this.date, DurationUnit.DAYS);\n" +
            "   }:Integer[1];\n" +
            "   \n" +
            "   latestEvent()\n" +
            "   {\n" +
            "      $this.events->filter(e | $e.date == $this.latestEventDate)->toOne()\n" +
            "   }:TradeEvent[1];\n" +
            "   \n" +
            "\n" +
            "   eventsByDate(date:Date[1])\n" +
            "   {\n" +
            "      $this.events->filter(e | $e.date == $date)\n" +
            "   }:TradeEvent[*];\n" +
            "   \n" +
            "   tradeDateEventType()\n" +
            "   {\n" +
            "      $this.eventsByDate($this.date->toOne()).eventType->toOne()\n" +
            "   }:String[1];\n" +
            "   \n" +
            "   tradeDateEventTypeInlined()\n" +
            "   {\n" +
            "      $this.events->filter(e | $e.date == $this.date).eventType->toOne()\n" +
            "   }:String[1];\n" +
            "\n" +
            "   initiator()\n" +
            "   {\n" +
            "      $this.eventsByDate($this.date).initiator->toOne()\n" +
            "   }:Person[0..1];\n" +
            "\n" +
            "   initiatorInlined()\n" +
            "   {\n" +
            "      $this.events->filter(e | $e.date == $this.date).initiator->toOne()\n" +
            "   }:Person[0..1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::TradeEvent\n" +
            "{\n" +
            "   eventType : String[0..1];\n" +
            "   date: Date[1];\n" +
            "   initiator: Person[0..1];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::Trade_Accounts\n" +
            "{\n" +
            "   account : Account[0..1];\n" +
            "   trades : Trade[*];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Contract\n" +
            "{\n" +
            "   id : String[1];\n" +
            "   money : Money[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Currency\n" +
            "{\n" +
            "   currency : String[1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Money\n" +
            "{\n" +
            "   amount: Float[1];\n" +
            "   usdRates:  FxReferenceRate[*];\n" +
            "   currency : Currency[1];\n" +
            "   usdRate(d:Date[1], t:NonStandardTenorQualifier[1])\n" +
            "   {\n" +
            "      $this.usdRates->filter(u|$u.observationDate == $d && $u.nonStandardTenorQualifier == $t)->toOne()\n" +
            "   }:FxReferenceRate[1];\n" +
            "   usdValueWithMap(d:Date[1], t:NonStandardTenorQualifier[1])\n" +
            "   {\n" +
            "      if ($this.currency.currency == 'USD',|$this.amount, |$this.amount * $this.usdRate($d, $t)->map(u|$u.rate))\n" +
            "   }:Float[1] ;\n" +
            "   usdValueNoMap(d:Date[1], t:NonStandardTenorQualifier[1])\n" +
            "   {\n" +
            "      if ($this.currency.currency == 'USD',|$this.amount, |$this.amount * $this.usdRate($d, $t).rate)\n" +
            "   }:Float[1] ;\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::FxReferenceRate\n" +
            "{\n" +
            "   observationDate: Date[1];\n" +
            "   nonStandardTenorQualifier: NonStandardTenorQualifier[0..1];\n" +
            "   rate: Float[1];\n" +
            "}\n" +
            "\n" +
            "Enum meta::pure::tests::model::simple::NonStandardTenorQualifier\n" +
            "{\n" +
            "   S,   // Spot\n" +
            "   F,   // Forward\n" +
            "   None // None\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::BridgeAsso1\n" +
            "{\n" +
            "    bridge : Bridge[0..1];\n" +
            "    employees : Person[*];\n" +
            "}\n" +
            "\n" +
            "Association meta::pure::tests::model::simple::BridgeAsso2\n" +
            "{\n" +
            "    bridge : Bridge[0..1];\n" +
            "    firm : Firm[0..1];\n" +
            "}\n" +
            "\n" +
            "Class meta::pure::tests::model::simple::Bridge\n" +
            "{\n" +
            "}\n" +
            "\n" +
            "\n";
}
