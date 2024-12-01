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

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;

import static com.squareup.javapoet.Util.checkNotNull;

public final class ArrayTypeName extends TypeName {
  public final TypeName componentType;

  private ArrayTypeName(TypeName componentType) {
    this(componentType, new ArrayList<>());
  }

  private ArrayTypeName(TypeName componentType, List<AnnotationSpec> annotations) {
    super(annotations);
    this.componentType = checkNotNull(componentType, "rawType == null");
  }

  @Override public ArrayTypeName annotated(List<? extends AnnotationSpec> annotations) {
    return new ArrayTypeName(componentType, concatAnnotations(annotations));
  }

  @Override public TypeName withoutAnnotations() {
    return new ArrayTypeName(componentType);
  }

  @Override CodeWriter emit(CodeWriter out) throws IOException {
    return emit(out, false);
  }

  CodeWriter emit(CodeWriter out, boolean varargs) throws IOException {
    emitLeafType(out);
    return emitBrackets(out, varargs);
  }

  private CodeWriter emitLeafType(CodeWriter out) throws IOException {
    if (TypeName.asArray(componentType) != null) {
      return TypeName.asArray(componentType).emitLeafType(out);
    }
    return componentType.emit(out);
  }

  private CodeWriter emitBrackets(CodeWriter out, boolean varargs) throws IOException {
    if (isAnnotated()) {
      out.emit(" ");
      emitAnnotations(out);
    }

    if (TypeName.asArray(componentType) == null) {
      // Last bracket.
      return out.emit(varargs ? "..." : "[]");
    }
    out.emit("[]");
    return TypeName.asArray(componentType) .emitBrackets(out, varargs);
  }


  /** Returns an array type whose elements are all instances of {@code componentType}. */
  public static ArrayTypeName of(TypeName componentType) {
    return new ArrayTypeName(componentType);
  }

  /** Returns an array type whose elements are all instances of {@code componentType}. */
  public static ArrayTypeName of(Type componentType) {
    return of(TypeName.get(componentType));
  }

  /** Returns an array type equivalent to {@code mirror}. */
  public static ArrayTypeName get(ArrayType mirror) {
    return get(mirror, new LinkedHashMap<>());
  }

  static ArrayTypeName get(
      ArrayType mirror, Map<TypeParameterElement, TypeVariableName> typeVariables) {
    return new ArrayTypeName(get(mirror.getComponentType(), typeVariables));
  }

  /** Returns an array type equivalent to {@code type}. */
  public static ArrayTypeName get(GenericArrayType type) {
    return get(type, new LinkedHashMap<>());
  }

  static ArrayTypeName get(GenericArrayType type, Map<Type, TypeVariableName> map) {
    return ArrayTypeName.of(get(type.getGenericComponentType(), map));
  }
}
