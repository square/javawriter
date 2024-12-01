/*
 * Copyright (C) 2014 Google, Inc.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor8;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;

/** A fully-qualified class name for top-level and member classes. */
public final class ClassName extends TypeName implements Comparable<ClassName> {
  public static final ClassName OBJECT = ClassName.get(Object.class);

  /** The name representing the default Java package. */
  private static final String NO_PACKAGE = "";

  /** The package name of this class, or "" if this is in the default package. */
  final String packageName;

  /** The enclosing class, or null if this is not enclosed in another class. */
  final ClassName enclosingClassName;

  /** This class name, like "Entry" for java.util.Map.Entry. */
  final String simpleName;

  private List<String> simpleNames;

  /** The full class name like "java.util.Map.Entry". */
  final String canonicalName;

  private ClassName(String packageName, ClassName enclosingClassName, String simpleName) {
    this(packageName, enclosingClassName, simpleName, Collections.emptyList());
  }

  private ClassName(String packageName, ClassName enclosingClassName, String simpleName,
      List<AnnotationSpec> annotations) {
    super(annotations);
    this.packageName = Objects.requireNonNull(packageName, "packageName == null");
    this.enclosingClassName = enclosingClassName;
    this.simpleName = simpleName;
    this.canonicalName = enclosingClassName != null
        ? (enclosingClassName.canonicalName + '.' + simpleName)
        : (packageName.isEmpty() ? simpleName : packageName + '.' + simpleName);
  }

  @Override public ClassName annotated(List<? extends AnnotationSpec> annotations) {
    return new ClassName(packageName, enclosingClassName, simpleName,
        concatAnnotations(annotations));
  }

  @Override public ClassName withoutAnnotations() {
    if (!isAnnotated()) return this;
    ClassName resultEnclosingClassName = enclosingClassName != null
        ? enclosingClassName.withoutAnnotations()
        : null;
    return new ClassName(packageName, resultEnclosingClassName, simpleName);
  }

  @Override public boolean isAnnotated() {
    return super.isAnnotated() || (enclosingClassName != null && enclosingClassName.isAnnotated());
  }

  /**
   * Returns the package name, like {@code "java.util"} for {@code Map.Entry}. Returns the empty
   * string for the default package.
   */
  public String packageName() {
    return packageName;
  }

  /**
   * Returns the enclosing class, like {@link Map} for {@code Map.Entry}. Returns null if this class
   * is not nested in another class.
   */
  public ClassName enclosingClassName() {
    return enclosingClassName;
  }

  /**
   * Returns the top class in this nesting group. Equivalent to chained calls to {@link
   * #enclosingClassName()} until the result's enclosing class is null.
   */
  public ClassName topLevelClassName() {
    return enclosingClassName != null ? enclosingClassName.topLevelClassName() : this;
  }

  /** Return the binary name of a class. */
  public String reflectionName() {
    return enclosingClassName != null
        ? (enclosingClassName.reflectionName() + '$' + simpleName)
        : (packageName.isEmpty() ? simpleName : packageName + '.' + simpleName);
  }

  public List<String> simpleNames() {
    if (simpleNames != null) {
      return simpleNames;
    }

    if (enclosingClassName == null) {
      simpleNames = Collections.singletonList(simpleName);
    } else {
      List<String> mutableNames = new ArrayList<>();
      mutableNames.addAll(enclosingClassName().simpleNames());
      mutableNames.add(simpleName);
      simpleNames = Collections.unmodifiableList(mutableNames);
    }
    return simpleNames;
  }

  /**
   * Returns a class that shares the same enclosing package or class. If this class is enclosed by
   * another class, this is equivalent to {@code enclosingClassName().nestedClass(name)}. Otherwise
   * it is equivalent to {@code get(packageName(), name)}.
   */
  public ClassName peerClass(String name) {
    return new ClassName(packageName, enclosingClassName, name);
  }

  /**
   * Returns a new {@link ClassName} instance for the specified {@code name} as nested inside this
   * class.
   */
  public ClassName nestedClass(String name) {
    return new ClassName(packageName, this, name);
  }

  /** Returns the simple name of this class, like {@code "Entry"} for {@link Map.Entry}. */
  public String simpleName() {
    return simpleName;
  }

  /**
   * Returns the full class name of this class.
   * Like {@code "java.util.Map.Entry"} for {@link Map.Entry}.
   * */
  public String canonicalName() {
    return canonicalName;
  }

  public static ClassName get(Class<?> clazz) {
    checkNotNull(clazz, "clazz == null");
    checkArgument(!clazz.isPrimitive(), "primitive types cannot be represented as a ClassName");
    checkArgument(!void.class.equals(clazz), "'void' type cannot be represented as a ClassName");
    checkArgument(!clazz.isArray(), "array types cannot be represented as a ClassName");

    String anonymousSuffix = "";
    while (clazz.isAnonymousClass()) {
      int lastDollar = clazz.getName().lastIndexOf('$');
      anonymousSuffix = clazz.getName().substring(lastDollar) + anonymousSuffix;
      clazz = clazz.getEnclosingClass();
    }
    String name = clazz.getSimpleName() + anonymousSuffix;

    if (clazz.getEnclosingClass() == null) {
      // Avoid unreliable Class.getPackage(). https://github.com/square/javapoet/issues/295
      int lastDot = clazz.getName().lastIndexOf('.');
      String packageName = (lastDot != -1) ? clazz.getName().substring(0, lastDot) : NO_PACKAGE;
      return new ClassName(packageName, null, name);
    }

    return ClassName.get(clazz.getEnclosingClass()).nestedClass(name);
  }

  /**
   * Returns a new {@link ClassName} instance for the given fully-qualified class name string. This
   * method assumes that the input is ASCII and follows typical Java style (lowercase package
   * names, UpperCamelCase class names) and may produce incorrect results or throw
   * {@link IllegalArgumentException} otherwise. For that reason, {@link #get(Class)} and
   * {@link #get(Class)} should be preferred as they can correctly create {@link ClassName}
   * instances without such restrictions.
   */
  public static ClassName bestGuess(String classNameString) {
    // Add the package name, like "java.util.concurrent", or "" for no package.
    int p = 0;
    while (p < classNameString.length() && Character.isLowerCase(classNameString.codePointAt(p))) {
      p = classNameString.indexOf('.', p) + 1;
      checkArgument(p != 0, "couldn't make a guess for %s", classNameString);
    }
    String packageName = p == 0 ? NO_PACKAGE : classNameString.substring(0, p - 1);

    // Add class names like "Map" and "Entry".
    ClassName className = null;
    for (String simpleName : classNameString.substring(p).split("\\.", -1)) {
      checkArgument(!simpleName.isEmpty() && Character.isUpperCase(simpleName.codePointAt(0)),
          "couldn't make a guess for %s", classNameString);
      className = new ClassName(packageName, className, simpleName);
    }

    return className;
  }

  /**
   * Returns a class name created from the given parts. For example, calling this with package name
   * {@code "java.util"} and simple names {@code "Map"}, {@code "Entry"} yields {@link Map.Entry}.
   */
  public static ClassName get(String packageName, String simpleName, String... simpleNames) {
    ClassName className = new ClassName(packageName, null, simpleName);
    for (String name : simpleNames) {
      className = className.nestedClass(name);
    }
    return className;
  }

  /** Returns the class name for {@code element}. */
  public static ClassName get(TypeElement element) {
    checkNotNull(element, "element == null");
    String simpleName = element.getSimpleName().toString();

    return element.getEnclosingElement().accept(new SimpleElementVisitor8<ClassName, Void>() {
      @Override public ClassName visitPackage(PackageElement packageElement, Void p) {
        return new ClassName(packageElement.getQualifiedName().toString(), null, simpleName);
      }

      @Override public ClassName visitType(TypeElement enclosingClass, Void p) {
        return ClassName.get(enclosingClass).nestedClass(simpleName);
      }

      @Override public ClassName visitUnknown(Element unknown, Void p) {
        return get("", simpleName);
      }

      @Override public ClassName defaultAction(Element enclosingElement, Void p) {
        throw new IllegalArgumentException("Unexpected type nesting: " + element);
      }
    }, null);
  }

  @Override public int compareTo(ClassName o) {
    return canonicalName.compareTo(o.canonicalName);
  }

  @Override CodeWriter emit(CodeWriter out) throws IOException {
    boolean charsEmitted = false;
    for (ClassName className : enclosingClasses()) {
      String simpleName;
      if (charsEmitted) {
        // We've already emitted an enclosing class. Emit as we go.
        out.emit(".");
        simpleName = className.simpleName;

      } else if (className.isAnnotated() || className == this) {
        // We encountered the first enclosing class that must be emitted.
        String qualifiedName = out.lookupName(className);
        int dot = qualifiedName.lastIndexOf('.');
        if (dot != -1) {
          out.emitAndIndent(qualifiedName.substring(0, dot + 1));
          simpleName = qualifiedName.substring(dot + 1);
          charsEmitted = true;
        } else {
          simpleName = qualifiedName;
        }

      } else {
        // Don't emit this enclosing type. Keep going so we can be more precise.
        continue;
      }

      if (className.isAnnotated()) {
        if (charsEmitted) out.emit(" ");
        className.emitAnnotations(out);
      }

      out.emit(simpleName);
      charsEmitted = true;
    }

    return out;
  }

  /** Returns all enclosing classes in this, outermost first. */
  private List<ClassName> enclosingClasses() {
    List<ClassName> result = new ArrayList<>();
    for (ClassName c = this; c != null; c = c.enclosingClassName) {
      result.add(c);
    }
    Collections.reverse(result);
    return result;
  }
}
