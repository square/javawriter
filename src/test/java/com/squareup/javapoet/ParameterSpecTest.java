/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.javapoet;

import com.google.testing.compile.CompilationRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import org.junit.Before;
import org.junit.Rule;
import javax.lang.model.element.Modifier;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.squareup.javapoet.TestUtil.findFirst;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.fail;

public class ParameterSpecTest {
  @Rule public final CompilationRule compilation = new CompilationRule();

  private Elements elements;

  @Before public void setUp() {
    elements = compilation.getElements();
  }

  private TypeElement getElement(Class<?> clazz) {
    return elements.getTypeElement(clazz.getCanonicalName());
  }

  @Test public void equalsAndHashCode() {
    ParameterSpec a = ParameterSpec.builder(int.class, "foo").build();
    ParameterSpec b = ParameterSpec.builder(int.class, "foo").build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
    a = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    b = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
    assertThat(a.equals(b)).isTrue();
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a.toString()).isEqualTo(b.toString());
  }

  @Test public void receiverParameterInstanceMethod() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "this");
    assertThat(builder.build().name).isEqualTo("this");
  }

  @Test public void receiverParameterNestedClass() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "Foo.this");
    assertThat(builder.build().name).isEqualTo("Foo.this");
  }

  @Test public void keywordName() {
    try {
      ParameterSpec.builder(int.class, "super");
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("not a valid name: super");
    }
  }

  @Test public void nullAnnotationsAddition() {
    try {
      ParameterSpec.builder(int.class, "foo").addAnnotations(null);
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage())
          .isEqualTo("annotationSpecs == null");
    }
  }

  final class VariableElementFieldClass {
    String name;
  }

  @Test public void fieldVariableElement() {
    TypeElement classElement = getElement(VariableElementFieldClass.class);
    List<VariableElement> methods = fieldsIn(elements.getAllMembers(classElement));
    VariableElement element = findFirst(methods, "name");

    try {
      ParameterSpec.get(element);
      fail();
    } catch (IllegalArgumentException exception) {
      assertThat(exception).hasMessageThat().isEqualTo("element is not a parameter");
    }
  }

  final class VariableElementParameterClass {
    public void foo(@Nullable final String bar) {
    }
  }


  private ExecutableElement findFirstExecutable(Collection<ExecutableElement> elements, String name) {
    for (ExecutableElement executableElement : elements) {
      if (executableElement.getSimpleName().toString().equals(name)) {
        return executableElement;
      }
    }

    throw new IllegalArgumentException(name + " not found in " + elements);
  }

  @Test public void parameterVariableElement() {
    TypeElement classElement = getElement(VariableElementParameterClass.class);
    List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
    ExecutableElement element = findFirstExecutable(methods, "foo");
    VariableElement parameterElement = element.getParameters().get(0);

    assertThat(ParameterSpec.get(parameterElement).toString())
            .isEqualTo("java.lang.String bar");
  }
  @Test public void addNonFinalModifier() {
    List<Modifier> modifiers = new ArrayList<>();
    modifiers.add(Modifier.FINAL);
    modifiers.add(Modifier.PUBLIC);

    try {
      ParameterSpec.builder(int.class, "foo")
          .addModifiers(modifiers);
      fail();
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("unexpected parameter modifier: public");
    }
  }

  @Test public void modifyAnnotations() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "foo")
            .addAnnotation(Override.class)
            .addAnnotation(SuppressWarnings.class);

    builder.annotations.remove(1);
    assertThat(builder.build().annotations).hasSize(1);
  }

  @Test public void modifyModifiers() {
    ParameterSpec.Builder builder = ParameterSpec.builder(int.class, "foo")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

    builder.modifiers.remove(1);
    assertThat(builder.build().modifiers).containsExactly(Modifier.PUBLIC);
  }
}
