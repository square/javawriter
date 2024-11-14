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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static com.squareup.javapoet.Util.checkState;
import static com.squareup.javapoet.Util.stringLiteralWithDoubleQuotes;
import static java.lang.String.join;

/**
 * Converts a {@link JavaFile} to a string suitable to both human- and javac-consumption. This
 * honors imports, indentation, and deferred variable names.
 */
final class CodeWriter {
  /** Sentinel value that indicates that no user-provided package has been set. */
  private static final String NO_PACKAGE = new String();
  private static final Pattern LINE_BREAKING_PATTERN = Pattern.compile("\\R");

  private final String indent;
  private final LineWrapper out;
  private int indentLevel;

  private boolean javadoc = false;
  private boolean comment = false;
  private String packageName = NO_PACKAGE;
  private final List<TypeSpec> typeSpecStack = new ArrayList<>();
  private final Set<String> staticImportClassNames;
  private final Set<String> staticImports;
  private final Set<String> alwaysQualify;
  private final Map<String, ClassName> importedTypes;
  private final Map<String, ClassName> importableTypes = new LinkedHashMap<>();
  private final Set<String> referencedNames = new LinkedHashSet<>();
  private final Multiset<String> currentTypeVariables = new Multiset<>();
  private boolean trailingNewline;

  /**
   * When emitting a statement, this is the line of the statement currently being written. The first
   * line of a statement is indented normally and subsequent wrapped lines are double-indented. This
   * is -1 when the currently-written line isn't part of a statement.
   */
  int statementLine = -1;

  CodeWriter(Appendable out) {
    this(out, "  ", Collections.emptySet(), Collections.emptySet());
  }

  CodeWriter(Appendable out, String indent, Set<String> staticImports, Set<String> alwaysQualify) {
    this(out, indent, Collections.emptyMap(), staticImports, alwaysQualify);
  }

  CodeWriter(Appendable out,
      String indent,
      Map<String, ClassName> importedTypes,
      Set<String> staticImports,
      Set<String> alwaysQualify) {
    this.out = new LineWrapper(out, indent, 100);
    this.indent = checkNotNull(indent, "indent == null");
    this.importedTypes = checkNotNull(importedTypes, "importedTypes == null");
    this.staticImports = checkNotNull(staticImports, "staticImports == null");
    this.alwaysQualify = checkNotNull(alwaysQualify, "alwaysQualify == null");
    this.staticImportClassNames = new LinkedHashSet<>();
    for (String signature : staticImports) {
      staticImportClassNames.add(signature.substring(0, signature.lastIndexOf('.')));
    }
  }

  public Map<String, ClassName> importedTypes() {
    return importedTypes;
  }

  public CodeWriter indent() {
    return indent(1);
  }

  public CodeWriter indent(int levels) {
    indentLevel += levels;
    return this;
  }

  public CodeWriter unindent() {
    return unindent(1);
  }

  public CodeWriter unindent(int levels) {
    checkArgument(indentLevel - levels >= 0, "cannot unindent %s from %s", levels, indentLevel);
    indentLevel -= levels;
    return this;
  }

  public CodeWriter pushPackage(String packageName) {
    checkState(this.packageName == NO_PACKAGE, "package already set: %s", this.packageName);
    this.packageName = checkNotNull(packageName, "packageName == null");
    return this;
  }

  public CodeWriter popPackage() {
    checkState(this.packageName != NO_PACKAGE, "package not set");
    this.packageName = NO_PACKAGE;
    return this;
  }

  public CodeWriter pushType(TypeSpec type) {
    this.typeSpecStack.add(type);
    return this;
  }

  public CodeWriter popType() {
    this.typeSpecStack.remove(typeSpecStack.size() - 1);
    return this;
  }

  public void emitComment(CodeBlock codeBlock) throws IOException {
    trailingNewline = true; // Force the '//' prefix for the comment.
    comment = true;
    try {
      emit(codeBlock);
      emit("\n");
    } finally {
      comment = false;
    }
  }

  public void emitJavadoc(CodeBlock javadocCodeBlock) throws IOException {
    if (javadocCodeBlock.isEmpty()) return;

    emit("/**\n");
    javadoc = true;
    try {
      emit(javadocCodeBlock, true);
    } finally {
      javadoc = false;
    }
    emit(" */\n");
  }

  public void emitAnnotations(List<AnnotationSpec> annotations, boolean inline) throws IOException {
    for (AnnotationSpec annotationSpec : annotations) {
      annotationSpec.emit(this, inline);
      emit(inline ? " " : "\n");
    }
  }

  /**
   * Emits {@code modifiers} in the standard order. Modifiers in {@code implicitModifiers} will not
   * be emitted.
   */
  public void emitModifiers(Set<Modifier> modifiers, Set<Modifier> implicitModifiers)
      throws IOException {
    if (modifiers.isEmpty()) return;
    for (Modifier modifier : EnumSet.copyOf(modifiers)) {
      if (implicitModifiers.contains(modifier)) continue;
      emitAndIndent(modifier.name().toLowerCase(Locale.US));
      emitAndIndent(" ");
    }
  }

  public void emitModifiers(Set<Modifier> modifiers) throws IOException {
    emitModifiers(modifiers, Collections.emptySet());
  }

  /**
   * Emit type variables with their bounds. This should only be used when declaring type variables;
   * everywhere else bounds are omitted.
   */
  public void emitTypeVariables(List<TypeVariableName> typeVariables) throws IOException {
    if (typeVariables.isEmpty()) return;

    typeVariables.forEach(typeVariable -> currentTypeVariables.add(typeVariable.name));

    emit("<");
    boolean firstTypeVariable = true;
    for (TypeVariableName typeVariable : typeVariables) {
      if (!firstTypeVariable) emit(", ");
      emitAnnotations(typeVariable.annotations, true);
      emit("$L", typeVariable.name);
      boolean firstBound = true;
      for (TypeName bound : typeVariable.bounds) {
        emit(firstBound ? " extends $T" : " & $T", bound);
        firstBound = false;
      }
      firstTypeVariable = false;
    }
    emit(">");
  }

  public void popTypeVariables(List<TypeVariableName> typeVariables) throws IOException {
    typeVariables.forEach(typeVariable -> currentTypeVariables.remove(typeVariable.name));
  }

  public CodeWriter emit(String s) throws IOException {
    return emitAndIndent(s);
  }

  public CodeWriter emit(String format, Object... args) throws IOException {
    return emit(CodeBlock.of(format, args));
  }

  public CodeWriter emit(CodeBlock codeBlock) throws IOException {
    return emit(codeBlock, false);
  }

  public CodeWriter emit(CodeBlock codeBlock, boolean ensureTrailingNewline) throws IOException {
    int a = 0;
    ClassName deferredTypeName = null; // used by "import static" logic
    ListIterator<String> partIterator = codeBlock.formatParts.listIterator();
    while (partIterator.hasNext()) {
      String part = partIterator.next();
      switch (part) {
        case "$L":
          emitLiteral(codeBlock.args.get(a++));
          break;

        case "$N":
          emitAndIndent((String) codeBlock.args.get(a++));
          break;

        case "$S":
          String string = (String) codeBlock.args.get(a++);
          // Emit null as a literal null: no quotes.
          emitAndIndent(string != null
              ? stringLiteralWithDoubleQuotes(string, indent)
              : "null");
          break;

        case "$T":
          TypeName typeName = (TypeName) codeBlock.args.get(a++);
          // defer "typeName.emit(this)" if next format part will be handled by the default case
          if (typeName instanceof ClassName && partIterator.hasNext()) {
            if (!codeBlock.formatParts.get(partIterator.nextIndex()).startsWith("$")) {
              ClassName candidate = (ClassName) typeName;
              if (staticImportClassNames.contains(candidate.canonicalName)) {
                checkState(deferredTypeName == null, "pending type for static import?!");
                deferredTypeName = candidate;
                break;
              }
            }
          }
          typeName.emit(this);
          break;

        case "$$":
          emitAndIndent("$");
          break;

        case "$>":
          indent();
          break;

        case "$<":
          unindent();
          break;

        case "$[":
          checkState(statementLine == -1, "statement enter $[ followed by statement enter $[");
          statementLine = 0;
          break;

        case "$]":
          checkState(statementLine != -1, "statement exit $] has no matching statement enter $[");
          if (statementLine > 0) {
            unindent(2); // End a multi-line statement. Decrease the indentation level.
          }
          statementLine = -1;
          break;

        case "$W":
          out.wrappingSpace(indentLevel + 2);
          break;

        case "$Z":
          out.zeroWidthSpace(indentLevel + 2);
          break;

        default:
          // handle deferred type
          if (deferredTypeName != null) {
            if (part.startsWith(".")) {
              if (emitStaticImportMember(deferredTypeName.canonicalName, part)) {
                // okay, static import hit and all was emitted, so clean-up and jump to next part
                deferredTypeName = null;
                break;
              }
            }
            deferredTypeName.emit(this);
            deferredTypeName = null;
          }
          emitAndIndent(part);
          break;
      }
    }
    if (ensureTrailingNewline && out.lastChar() != '\n') {
      emit("\n");
    }
    return this;
  }

  public CodeWriter emitWrappingSpace() throws IOException {
    out.wrappingSpace(indentLevel + 2);
    return this;
  }

  private static String extractMemberName(String part) {
    checkArgument(Character.isJavaIdentifierStart(part.charAt(0)), "not an identifier: %s", part);
    for (int i = 1; i <= part.length(); i++) {
      if (!SourceVersion.isIdentifier(part.substring(0, i))) {
        return part.substring(0, i - 1);
      }
    }
    return part;
  }

  private boolean emitStaticImportMember(String canonical, String part) throws IOException {
    String partWithoutLeadingDot = part.substring(1);
    if (partWithoutLeadingDot.isEmpty()) return false;
    char first = partWithoutLeadingDot.charAt(0);
    if (!Character.isJavaIdentifierStart(first)) return false;
    String explicit = canonical + "." + extractMemberName(partWithoutLeadingDot);
    String wildcard = canonical + ".*";
    if (staticImports.contains(explicit) || staticImports.contains(wildcard)) {
      emitAndIndent(partWithoutLeadingDot);
      return true;
    }
    return false;
  }

  private void emitLiteral(Object o) throws IOException {
    if (o instanceof TypeSpec) {
      TypeSpec typeSpec = (TypeSpec) o;
      typeSpec.emit(this, null, Collections.emptySet());
    } else if (o instanceof AnnotationSpec) {
      AnnotationSpec annotationSpec = (AnnotationSpec) o;
      annotationSpec.emit(this, true);
    } else if (o instanceof CodeBlock) {
      CodeBlock codeBlock = (CodeBlock) o;
      emit(codeBlock);
    } else {
      emitAndIndent(String.valueOf(o));
    }
  }

  /**
   * Returns the best name to identify {@code className} with in the current context. This uses the
   * available imports and the current scope to find the shortest name available. It does not honor
   * names visible due to inheritance.
   */
  String lookupName(ClassName className) {
    // If the top level simple name is masked by a current type variable, use the canonical name.
    String topLevelSimpleName = className.topLevelClassName().simpleName();
    if (currentTypeVariables.contains(topLevelSimpleName)) {
      return className.canonicalName;
    }

    // Find the shortest suffix of className that resolves to className. This uses both local type
    // names (so `Entry` in `Map` refers to `Map.Entry`). Also uses imports.
    boolean nameResolved = false;
    for (ClassName c = className; c != null; c = c.enclosingClassName()) {
      ClassName resolved = resolve(c.simpleName());
      nameResolved = resolved != null;

      if (resolved != null && Objects.equals(resolved.canonicalName, c.canonicalName)) {
        int suffixOffset = c.simpleNames().size() - 1;
        return join(".", className.simpleNames().subList(
            suffixOffset, className.simpleNames().size()));
      }
    }

    // If the name resolved but wasn't a match, we're stuck with the fully qualified name.
    if (nameResolved) {
      return className.canonicalName;
    }

    // If the class is in the same package, we're done.
    if (Objects.equals(packageName, className.packageName())) {
      String conflictClassName = packageName+"."+className.simpleName;//From same package (Nest)
      //For nested conflict situation, the simpleName should be in QualifyList
      //But its full name dont contains its super name
      if(alwaysQualify.contains(className.simpleName)
              &&Objects.equals(conflictClassName,className.canonicalName)){

      }else{//If not, do regular trim
        referencedNames.add(topLevelSimpleName);
        return join(".", className.simpleNames());
      }
    }

    // We'll have to use the fully-qualified name. Mark the type as importable for a future pass.
    if (!javadoc) {
      importableType(className);
    }

    return className.canonicalName;
  }

  private void importableType(ClassName className) {
    if (className.packageName().isEmpty()) {
      return;
    } else if (alwaysQualify.contains(className.simpleName)) {
      // TODO what about nested types like java.util.Map.Entry?
      return;
    }
    ClassName topLevelClassName = className.topLevelClassName();
    String simpleName = topLevelClassName.simpleName();
    ClassName replaced = importableTypes.put(simpleName, topLevelClassName);
    if (replaced != null) {
      importableTypes.put(simpleName, replaced); // On collision, prefer the first inserted.
    }
  }

  /**
   * Returns the class referenced by {@code simpleName}, using the current nesting context and
   * imports.
   */
  // TODO(jwilson): also honor superclass members when resolving names.
  private ClassName resolve(String simpleName) {
    // Match a child of the current (potentially nested) class.
    for (int i = typeSpecStack.size() - 1; i >= 0; i--) {
      TypeSpec typeSpec = typeSpecStack.get(i);
      if (typeSpec.nestedTypesSimpleNames.contains(simpleName)) {
        return stackClassName(i, simpleName);
      }
    }

    // Match the top-level class.
    if (typeSpecStack.size() > 0 && Objects.equals(typeSpecStack.get(0).name, simpleName)) {
      return ClassName.get(packageName, simpleName);
    }

    // Match an imported type.
    ClassName importedType = importedTypes.get(simpleName);
    if (importedType != null) return importedType;

    // No match.
    return null;
  }

  /** Returns the class named {@code simpleName} when nested in the class at {@code stackDepth}. */
  private ClassName stackClassName(int stackDepth, String simpleName) {
    ClassName className = ClassName.get(packageName, typeSpecStack.get(0).name);
    for (int i = 1; i <= stackDepth; i++) {
      className = className.nestedClass(typeSpecStack.get(i).name);
    }
    return className.nestedClass(simpleName);
  }

  /**
   * Emits {@code s} with indentation as required. It's important that all code that writes to
   * {@link #out} does it through here, since we emit indentation lazily in order to avoid
   * unnecessary trailing whitespace.
   */
  CodeWriter emitAndIndent(String s) throws IOException {
    boolean first = true;
    for (String line : LINE_BREAKING_PATTERN.split(s, -1)) {
      // Emit a newline character. Make sure blank lines in Javadoc & comments look good.
      if (!first) {
        if ((javadoc || comment) && trailingNewline) {
          emitIndentation();
          out.append(javadoc ? " *" : "//");
        }
        out.append("\n");
        trailingNewline = true;
        if (statementLine != -1) {
          if (statementLine == 0) {
            indent(2); // Begin multiple-line statement. Increase the indentation level.
          }
          statementLine++;
        }
      }

      first = false;
      if (line.isEmpty()) continue; // Don't indent empty lines.

      // Emit indentation and comment prefix if necessary.
      if (trailingNewline) {
        emitIndentation();
        if (javadoc) {
          out.append(" * ");
        } else if (comment) {
          out.append("// ");
        }
      }

      out.append(line);
      trailingNewline = false;
    }
    return this;
  }

  private void emitIndentation() throws IOException {
    for (int j = 0; j < indentLevel; j++) {
      out.append(indent);
    }
  }

  /**
   * Returns the types that should have been imported for this code. If there were any simple name
   * collisions, that type's first use is imported.
   */
  Map<String, ClassName> suggestedImports() {
    Map<String, ClassName> result = new LinkedHashMap<>(importableTypes);
    result.keySet().removeAll(referencedNames);
    return result;
  }

  // A makeshift multi-set implementation
  private static final class Multiset<T> {
    private final Map<T, Integer> map = new LinkedHashMap<>();

    void add(T t) {
      int count = map.getOrDefault(t, 0);
      map.put(t, count + 1);
    }

    void remove(T t) {
      int count = map.getOrDefault(t, 0);
      if (count == 0) {
        throw new IllegalStateException(t + " is not in the multiset");
      }
      map.put(t, count - 1);
    }

    boolean contains(T t) {
      return map.getOrDefault(t, 0) > 0;
    }
  }
}
