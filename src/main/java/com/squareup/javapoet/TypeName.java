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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Any type in Java's type system, plus {@code void}. This class is an identifier for primitive
 * types like {@code int} and raw reference types like {@code String} and {@code List}. It also
 * identifies composite types like {@code char[]} and {@code Set<Long>}.
 *
 * <p>Type names are dumb identifiers only and do not model the values they name. For example, the
 * type name for {@code java.util.List} doesn't know about the {@code size()} method, the fact that
 * lists are collections, or even that it accepts a single type parameter.
 *
 * <p>Instances of this class are immutable value objects that implement {@code equals()} and {@code
 * hashCode()} properly.
 *
 * <h3>Referencing existing types</h3>
 *
 * <p>Primitives and void are constants that you can reference directly: see {@link #INT}, {@link
 * #DOUBLE}, and {@link #VOID}.
 *
 * <p>In an annotation processor you can get a type name instance for a type mirror by calling
 * {@link #get(TypeMirror)}. In reflection code, you can use {@link #get(Type)}.
 *
 * <h3>Defining new types</h3>
 *
 * <p>Create new reference types like {@code com.example.HelloWorld} with {@link
 * ClassName#get(String, String, String...)}. To build composite types like {@code char[]} and
 * {@code Set<Long>}, use the factory methods on {@link ArrayTypeName}, {@link
 * ParameterizedTypeName}, {@link TypeVariableName}, and {@link WildcardTypeName}.
 */
public class TypeName {
  public static final TypeName VOID = new TypeName("void");
  public static final TypeName BOOLEAN = new TypeName("boolean");
  public static final TypeName BYTE = new TypeName("byte");
  public static final TypeName SHORT = new TypeName("short");
  public static final TypeName INT = new TypeName("int");
  public static final TypeName LONG = new TypeName("long");
  public static final TypeName CHAR = new TypeName("char");
  public static final TypeName FLOAT = new TypeName("float");
  public static final TypeName DOUBLE = new TypeName("double");
  public static final ClassName OBJECT = ClassName.get("java.lang", "Object");

  private static final ClassName BOXED_VOID = ClassName.get("java.lang", "Void");
  private static final ClassName BOXED_BOOLEAN = ClassName.get("java.lang", "Boolean");
  private static final ClassName BOXED_BYTE = ClassName.get("java.lang", "Byte");
  private static final ClassName BOXED_SHORT = ClassName.get("java.lang", "Short");
  private static final ClassName BOXED_INT = ClassName.get("java.lang", "Integer");
  private static final ClassName BOXED_LONG = ClassName.get("java.lang", "Long");
  private static final ClassName BOXED_CHAR = ClassName.get("java.lang", "Character");
  private static final ClassName BOXED_FLOAT = ClassName.get("java.lang", "Float");
  private static final ClassName BOXED_DOUBLE = ClassName.get("java.lang", "Double");

  /** The name of this type if it is a keyword, or null. */
  private final String keyword;
  public final List<AnnotationSpec> annotations;

  /** Lazily-initialized toString of this type name. */
  private String cachedString;

  private TypeName(String keyword) {
    this(keyword, new ArrayList<>());
  }

  private TypeName(String keyword, List<AnnotationSpec> annotations) {
    this.keyword = keyword;
    this.annotations = Util.immutableList(annotations);
  }

  // Package-private constructor to prevent third-party subclasses.
  TypeName(List<AnnotationSpec> annotations) {
    this(null, annotations);
  }

  public final TypeName annotated(AnnotationSpec... annotations) {
    return annotated(Arrays.asList(annotations));
  }

  public TypeName annotated(List<? extends AnnotationSpec> annotations) {
    return annotated((Iterable<? extends AnnotationSpec>) annotations);
  }

  public TypeName annotated(Iterable<? extends AnnotationSpec> annotations) {
    Util.checkNotNull(annotations, "annotations == null");
    return new TypeName(keyword, concatAnnotations(annotations));
  }

  public TypeName withoutAnnotations() {
    if (annotations.isEmpty()) {
      return this;
    }
    return new TypeName(keyword);
  }

  protected final List<AnnotationSpec> concatAnnotations(
      Iterable<? extends AnnotationSpec> annotations) {
    List<AnnotationSpec> allAnnotations = new ArrayList<>(this.annotations);
    Util.addAll(allAnnotations, annotations);
    return allAnnotations;
  }

  public boolean isAnnotated() {
    return !annotations.isEmpty();
  }

  /**
   * Returns true if this is a primitive type like {@code int}. Returns false for all other types
   * types including boxed primitives and {@code void}.
   */
  public boolean isPrimitive() {
    return keyword != null && this != VOID;
  }

  /**
   * Returns true if this is a boxed primitive type like {@code Integer}. Returns false for all
   * other types types including unboxed primitives and {@code java.lang.Void}.
   */
  public boolean isBoxedPrimitive() {
    TypeName thisWithoutAnnotations = withoutAnnotations();
    return thisWithoutAnnotations.equals(BOXED_BOOLEAN)
        || thisWithoutAnnotations.equals(BOXED_BYTE)
        || thisWithoutAnnotations.equals(BOXED_SHORT)
        || thisWithoutAnnotations.equals(BOXED_INT)
        || thisWithoutAnnotations.equals(BOXED_LONG)
        || thisWithoutAnnotations.equals(BOXED_CHAR)
        || thisWithoutAnnotations.equals(BOXED_FLOAT)
        || thisWithoutAnnotations.equals(BOXED_DOUBLE);
  }

  /**
   * Returns a boxed type if this is a primitive type (like {@code Integer} for {@code int}) or
   * {@code void}. Returns this type if boxing doesn't apply.
   */
  public TypeName box() {
    if (keyword == null) return this; // Doesn't need boxing.
    TypeName boxed = null;
    if (keyword.equals(VOID.keyword)) boxed = BOXED_VOID;
    else if (keyword.equals(BOOLEAN.keyword)) boxed = BOXED_BOOLEAN;
    else if (keyword.equals(BYTE.keyword)) boxed = BOXED_BYTE;
    else if (keyword.equals(SHORT.keyword)) boxed = BOXED_SHORT;
    else if (keyword.equals(INT.keyword)) boxed = BOXED_INT;
    else if (keyword.equals(LONG.keyword)) boxed = BOXED_LONG;
    else if (keyword.equals(CHAR.keyword)) boxed = BOXED_CHAR;
    else if (keyword.equals(FLOAT.keyword)) boxed = BOXED_FLOAT;
    else if (keyword.equals(DOUBLE.keyword)) boxed = BOXED_DOUBLE;
    else throw new AssertionError(keyword);
    return annotations.isEmpty() ? boxed : boxed.annotated(annotations);
  }

  /**
   * Returns an unboxed type if this is a boxed primitive type (like {@code int} for {@code
   * Integer}) or {@code Void}. Returns this type if it is already unboxed.
   *
   * @throws UnsupportedOperationException if this type isn't eligible for unboxing.
   */
  public TypeName unbox() {
    if (keyword != null) return this; // Already unboxed.
    TypeName thisWithoutAnnotations = withoutAnnotations();
    TypeName unboxed = null;
    if (thisWithoutAnnotations.equals(BOXED_VOID)) unboxed = VOID;
    else if (thisWithoutAnnotations.equals(BOXED_BOOLEAN)) unboxed = BOOLEAN;
    else if (thisWithoutAnnotations.equals(BOXED_BYTE)) unboxed = BYTE;
    else if (thisWithoutAnnotations.equals(BOXED_SHORT)) unboxed = SHORT;
    else if (thisWithoutAnnotations.equals(BOXED_INT)) unboxed = INT;
    else if (thisWithoutAnnotations.equals(BOXED_LONG)) unboxed = LONG;
    else if (thisWithoutAnnotations.equals(BOXED_CHAR)) unboxed = CHAR;
    else if (thisWithoutAnnotations.equals(BOXED_FLOAT)) unboxed = FLOAT;
    else if (thisWithoutAnnotations.equals(BOXED_DOUBLE)) unboxed = DOUBLE;
    else throw new UnsupportedOperationException("cannot unbox " + this);
    return annotations.isEmpty() ? unboxed : unboxed.annotated(annotations);
  }

  @Override public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) return false;
    return toString().equals(o.toString());
  }

  @Override public final int hashCode() {
    return toString().hashCode();
  }

  @Override public final String toString() {
    String result = cachedString;
    if (result == null) {
      try {
        StringBuilder resultBuilder = new StringBuilder();
        CodeWriter codeWriter = new CodeWriter(resultBuilder);
        emit(codeWriter);
        result = resultBuilder.toString();
        cachedString = result;
      } catch (IOException e) {
        throw new AssertionError();
      }
    }
    return result;
  }

  CodeWriter emit(CodeWriter out) throws IOException {
    if (keyword == null) throw new AssertionError();

    if (isAnnotated()) {
      out.emit("");
      emitAnnotations(out);
    }
    return out.emitAndIndent(keyword);
  }

  CodeWriter emitAnnotations(CodeWriter out) throws IOException {
    for (AnnotationSpec annotation : annotations) {
      annotation.emit(out, true);
      out.emit(" ");
    }
    return out;
  }


  /** Returns a type name equivalent to {@code mirror}. */
  public static TypeName get(TypeMirror mirror) {
    return get(mirror, new LinkedHashMap<>());
  }

  static TypeName get(TypeMirror mirror,
      final Map<TypeParameterElement, TypeVariableName> typeVariables) {
    return mirror.accept(new SimpleTypeVisitor8<TypeName, Void>() {
      @Override public TypeName visitPrimitive(PrimitiveType t, Void p) {
        switch (t.getKind()) {
          case BOOLEAN:
            return TypeName.BOOLEAN;
          case BYTE:
            return TypeName.BYTE;
          case SHORT:
            return TypeName.SHORT;
          case INT:
            return TypeName.INT;
          case LONG:
            return TypeName.LONG;
          case CHAR:
            return TypeName.CHAR;
          case FLOAT:
            return TypeName.FLOAT;
          case DOUBLE:
            return TypeName.DOUBLE;
          default:
            throw new AssertionError();
        }
      }

      @Override public TypeName visitDeclared(DeclaredType t, Void p) {
        ClassName rawType = ClassName.get((TypeElement) t.asElement());
        TypeMirror enclosingType = t.getEnclosingType();
        TypeName enclosing =
            (enclosingType.getKind() != TypeKind.NONE)
                    && !t.asElement().getModifiers().contains(Modifier.STATIC)
                ? enclosingType.accept(this, null)
                : null;
        if (t.getTypeArguments().isEmpty() && !(enclosing instanceof ParameterizedTypeName)) {
          return rawType;
        }

        List<TypeName> typeArgumentNames = new ArrayList<>();
        for (TypeMirror mirror : t.getTypeArguments()) {
          typeArgumentNames.add(get(mirror, typeVariables));
        }
        return enclosing instanceof ParameterizedTypeName
            ? ((ParameterizedTypeName) enclosing).nestedClass(
            rawType.simpleName(), typeArgumentNames)
            : new ParameterizedTypeName(null, rawType, typeArgumentNames);
      }

      @Override public TypeName visitError(ErrorType t, Void p) {
        return visitDeclared(t, p);
      }

      @Override public ArrayTypeName visitArray(ArrayType t, Void p) {
        return ArrayTypeName.get(t, typeVariables);
      }

      @Override public TypeName visitTypeVariable(javax.lang.model.type.TypeVariable t, Void p) {
        return TypeVariableName.get(t, typeVariables);
      }

      @Override public TypeName visitWildcard(javax.lang.model.type.WildcardType t, Void p) {
        return WildcardTypeName.get(t, typeVariables);
      }

      @Override public TypeName visitNoType(NoType t, Void p) {
        if (t.getKind() == TypeKind.VOID) return TypeName.VOID;
        return super.visitUnknown(t, p);
      }

      @Override protected TypeName defaultAction(TypeMirror e, Void p) {
        throw new IllegalArgumentException("Unexpected type mirror: " + e);
      }
    }, null);
  }

  /** Returns a type name equivalent to {@code type}. */
  public static TypeName get(Type type) {
    return get(type, new LinkedHashMap<>());
  }

  static TypeName get(Type type, Map<Type, TypeVariableName> map) {
    if (type instanceof Class<?>) {
      Class<?> classType = (Class<?>) type;
      if (type == void.class) return VOID;
      if (type == boolean.class) return BOOLEAN;
      if (type == byte.class) return BYTE;
      if (type == short.class) return SHORT;
      if (type == int.class) return INT;
      if (type == long.class) return LONG;
      if (type == char.class) return CHAR;
      if (type == float.class) return FLOAT;
      if (type == double.class) return DOUBLE;
      if (classType.isArray()) return ArrayTypeName.of(get(classType.getComponentType(), map));
      return ClassName.get(classType);

    } else if (type instanceof ParameterizedType) {
      return ParameterizedTypeName.get((ParameterizedType) type, map);

    } else if (type instanceof WildcardType) {
      return WildcardTypeName.get((WildcardType) type, map);

    } else if (type instanceof TypeVariable<?>) {
      return TypeVariableName.get((TypeVariable<?>) type, map);

    } else if (type instanceof GenericArrayType) {
      return ArrayTypeName.get((GenericArrayType) type, map);

    } else {
      throw new IllegalArgumentException("unexpected type: " + type);
    }
  }

  /** Converts an array of types to a list of type names. */
  static List<TypeName> list(Type[] types) {
    return list(Arrays.asList(types), new LinkedHashMap<>());
  }

  /** Converts an array of types to a list of type names. */
  static List<TypeName> list(Iterable<? extends Type> types) {
    return list(types, new LinkedHashMap<>());
  }

  static List<TypeName> list(Type[] types, Map<Type, TypeVariableName> map) {
    return list(Arrays.asList(types), map);
  }

  static List<TypeName> list(Iterable<? extends Type> types, Map<Type, TypeVariableName> map) {
    List<TypeName> result = Util.newArrayListWithSize(types);
    for (Type type : types) {
      result.add(get(type, map));
    }
    return result;
  }

  /** Returns the array component of {@code type}, or null if {@code type} is not an array. */
  static TypeName arrayComponent(TypeName type) {
    return type instanceof ArrayTypeName
        ? ((ArrayTypeName) type).componentType
        : null;
  }

  /** Returns {@code type} as an array, or null if {@code type} is not an array. */
  static ArrayTypeName asArray(TypeName type) {
    return type instanceof ArrayTypeName
        ? ((ArrayTypeName) type)
        : null;
  }

}
