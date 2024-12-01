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
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;

public final class ParameterizedTypeName extends TypeName {
  private final ParameterizedTypeName enclosingType;
  public final ClassName rawType;
  public final List<TypeName> typeArguments;

  ParameterizedTypeName(ParameterizedTypeName enclosingType, ClassName rawType,
      List<TypeName> typeArguments) {
    this(enclosingType, rawType, typeArguments, new ArrayList<>());
  }

  private ParameterizedTypeName(ParameterizedTypeName enclosingType, ClassName rawType,
      List<TypeName> typeArguments, List<AnnotationSpec> annotations) {
    super(annotations);
    this.rawType = checkNotNull(rawType, "rawType == null").annotated(annotations);
    this.enclosingType = enclosingType;
    this.typeArguments = Util.immutableList(typeArguments);

    checkArgument(!this.typeArguments.isEmpty() || enclosingType != null,
        "no type arguments: %s", rawType);
    for (TypeName typeArgument : this.typeArguments) {
      checkArgument(!typeArgument.isPrimitive() && typeArgument != VOID,
          "invalid type parameter: %s", typeArgument);
    }
  }

  @Override public ParameterizedTypeName annotated(List<AnnotationSpec> annotations) {
    return new ParameterizedTypeName(
        enclosingType, rawType.withoutAnnotations(), typeArguments, concatAnnotations(annotations));
  }

  @Override
  public TypeName withoutAnnotations() {
    return new ParameterizedTypeName(
        enclosingType, rawType.withoutAnnotations(), typeArguments, new ArrayList<>());
  }

  @Override CodeWriter emit(CodeWriter out) throws IOException {
    if (enclosingType != null) {
      enclosingType.emit(out);
      out.emit(".");
      if (isAnnotated()) {
        out.emit(" ");
        emitAnnotations(out);
      }
      out.emit(rawType.simpleName());
    } else {
      rawType.emit(out);
    }
    if (!typeArguments.isEmpty()) {
      out.emitAndIndent("<");
      boolean firstParameter = true;
      for (TypeName parameter : typeArguments) {
        if (!firstParameter) out.emitAndIndent(", ");
        parameter.emit(out);
        firstParameter = false;
      }
      out.emitAndIndent(">");
    }
    return out;
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class.
   */
  public ParameterizedTypeName nestedClass(String name) {
    checkNotNull(name, "name == null");
    return new ParameterizedTypeName(this, rawType.nestedClass(name), new ArrayList<>(),
        new ArrayList<>());
  }

  /**
   * Returns a new {@link ParameterizedTypeName} instance for the specified {@code name} as nested
   * inside this class, with the specified {@code typeArguments}.
   */
  public ParameterizedTypeName nestedClass(String name, List<TypeName> typeArguments) {
    checkNotNull(name, "name == null");
    return new ParameterizedTypeName(this, rawType.nestedClass(name), typeArguments,
        new ArrayList<>());
  }

  /** Returns a parameterized type, applying {@code typeArguments} to {@code rawType}. */
  public static ParameterizedTypeName get(ClassName rawType, TypeName... typeArguments) {
    return new ParameterizedTypeName(null, rawType, Arrays.asList(typeArguments));
  }

  /** Returns a parameterized type, applying {@code typeArguments} to {@code rawType}. */
  public static ParameterizedTypeName get(Class<?> rawType, Type... typeArguments) {
    return new ParameterizedTypeName(null, ClassName.get(rawType), list(typeArguments));
  }

  /** Returns a parameterized type equivalent to {@code type}. */
  public static ParameterizedTypeName get(ParameterizedType type) {
    return get(type, new LinkedHashMap<>());
  }

  /** Returns a parameterized type equivalent to {@code type}. */
  static ParameterizedTypeName get(ParameterizedType type, Map<Type, TypeVariableName> map) {
    ClassName rawType = ClassName.get((Class<?>) type.getRawType());
    ParameterizedType ownerType = (type.getOwnerType() instanceof ParameterizedType)
        && !Modifier.isStatic(((Class<?>) type.getRawType()).getModifiers())
        ? (ParameterizedType) type.getOwnerType() : null;
    List<TypeName> typeArguments = TypeName.list(type.getActualTypeArguments(), map);
    return (ownerType != null)
        ? get(ownerType, map).nestedClass(rawType.simpleName(), typeArguments)
        : new ParameterizedTypeName(null, rawType, typeArguments);
  }
}
