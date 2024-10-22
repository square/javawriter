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
import java.lang.reflect.Type;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import static com.squareup.javapoet.Util.checkArgument;

/**
 * A fragment of a .java file, potentially containing declarations, statements, and documentation.
 * Code blocks are not necessarily well-formed Java code, and are not validated. This class assumes
 * javac will check correctness later!
 *
 * <p>Code blocks support placeholders like {@link java.text.Format}. Where {@link String#format}
 * uses percent {@code %} to reference target values, this class uses dollar sign {@code $} and has
 * its own set of permitted placeholders:
 *
 * <ul>
 *   <li>{@code $L} emits a <em>literal</em> value with no escaping. Arguments for literals may be
 *       strings, primitives, {@linkplain TypeSpec type declarations}, {@linkplain AnnotationSpec
 *       annotations} and even other code blocks.
 *   <li>{@code $N} emits a <em>name</em>, using name collision avoidance where necessary. Arguments
 *       for names may be strings (actually any {@linkplain CharSequence character sequence}),
 *       {@linkplain ParameterSpec parameters}, {@linkplain FieldSpec fields}, {@linkplain
 *       MethodSpec methods}, and {@linkplain TypeSpec types}.
 *   <li>{@code $S} escapes the value as a <em>string</em>, wraps it with double quotes, and emits
 *       that. For example, {@code 6" sandwich} is emitted {@code "6\" sandwich"}.
 *   <li>{@code $T} emits a <em>type</em> reference. Types will be imported if possible. Arguments
 *       for types may be {@linkplain Class classes}, {@linkplain javax.lang.model.type.TypeMirror
,*       type mirrors}, and {@linkplain javax.lang.model.element.Element elements}.
 *   <li>{@code $$} emits a dollar sign.
 *   <li>{@code $W} emits a space or a newline, depending on its position on the line. This prefers
 *       to wrap lines before 100 columns.
 *   <li>{@code $Z} acts as a zero-width space. This prefers to wrap lines before 100 columns.
 *   <li>{@code $>} increases the indentation level.
 *   <li>{@code $<} decreases the indentation level.
 *   <li>{@code $[} begins a statement. For multiline statements, every line after the first line
 *       is double-indented.
 *   <li>{@code $]} ends a statement.
 * </ul>
 */
public final class CodeBlock {
  private static final Pattern NAMED_ARGUMENT =
      Pattern.compile("\\$(?<argumentName>[\\w_]+):(?<typeChar>[\\w]).*");
  private static final Pattern LOWERCASE = Pattern.compile("[a-z]+[\\w_]*");

  /** A heterogeneous list containing string literals and value placeholders. */
  final List<String> formatParts;
  final List<Object> args;

  private CodeBlock(Builder builder) {
    this.formatParts = Util.immutableList(builder.formatParts);
    this.args = Util.immutableList(builder.args);
  }

  public boolean isEmpty() {
    return formatParts.isEmpty();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) return false;
    return toString().equals(o.toString());
  }

  @Override public int hashCode() {
    return toString().hashCode();
  }

  @Override public String toString() {
    StringBuilder out = new StringBuilder();
    try {
      new CodeWriter(out).emit(this);
      return out.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  public static CodeBlock of(String format, Object... args) {
    return new Builder().add(format, args).build();
  }

  /**
   * Joins {@code codeBlocks} into a single {@link CodeBlock}, each separated by {@code separator}.
   * For example, joining {@code String s}, {@code Object o} and {@code int i} using {@code ", "}
   * would produce {@code String s, Object o, int i}.
   */
  public static CodeBlock join(Iterable<CodeBlock> codeBlocks, String separator) {
    return StreamSupport.stream(codeBlocks.spliterator(), false).collect(joining(separator));
  }

  /**
   * A {@link Collector} implementation that joins {@link CodeBlock} instances together into one
   * separated by {@code separator}. For example, joining {@code String s}, {@code Object o} and
   * {@code int i} using {@code ", "} would produce {@code String s, Object o, int i}.
   */
  public static Collector<CodeBlock, ?, CodeBlock> joining(String separator) {
    return Collector.of(
        () -> new CodeBlockJoiner(separator, builder()),
        CodeBlockJoiner::add,
        CodeBlockJoiner::merge,
        CodeBlockJoiner::join);
  }

  /**
   * A {@link Collector} implementation that joins {@link CodeBlock} instances together into one
   * separated by {@code separator}. For example, joining {@code String s}, {@code Object o} and
   * {@code int i} using {@code ", "} would produce {@code String s, Object o, int i}.
   */
  public static Collector<CodeBlock, ?, CodeBlock> joining(
      String separator, String prefix, String suffix) {
    Builder builder = builder().add("$N", prefix);
    return Collector.of(
        () -> new CodeBlockJoiner(separator, builder),
        CodeBlockJoiner::add,
        CodeBlockJoiner::merge,
        joiner -> {
            builder.add(CodeBlock.of("$N", suffix));
            return joiner.join();
        });
  }

  public static Builder builder() {
    return new Builder();
  }
  /**
   * add lambda support
   * @param params name for parameters
   * @return builder
   */
  public static LambdaBuilder lambdaBuilder(String... params) {
    return new LambdaBuilder(params);
  }
  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.formatParts.addAll(formatParts);
    builder.args.addAll(args);
    return builder;
  }
  public static final class LambdaBuilder extends Builder {

    /**
     * initing builder
     * @param params
     */
    public LambdaBuilder(String... params) {
      if (params.length == 0) {
        throw new InvalidParameterException();
      } else if (params.length == 1) {
        add(params[0] + " -> {\n").indent();
      } else {
        add("(" + params[0]);
        for (int i = 1; i < params.length; i++) {
          add(", " + params[i]);
        }
        add(") -> {\n").indent();
      }
    }

    /**
     * build code block
     * @return
     */
    @Override
    public CodeBlock build() {
      unindent().add("}");
      return super.build();
    }
  }
  public static class Builder {
    final List<String> formatParts = new ArrayList<>();
    final List<Object> args = new ArrayList<>();

    private Builder() {
    }

    public boolean isEmpty() {
      return formatParts.isEmpty();
    }

    /**
     * Adds code using named arguments.
     *
     * <p>Named arguments specify their name after the '$' followed by : and the corresponding type
     * character. Argument names consist of characters in {@code a-z, A-Z, 0-9, and _} and must
     * start with a lowercase character.
     *
     * <p>For example, to refer to the type {@link java.lang.Integer} with the argument name {@code
     * clazz} use a format string containing {@code $clazz:T} and include the key {@code clazz} with
     * value {@code java.lang.Integer.class} in the argument map.
     */
    public Builder addNamed(String format, Map<String, ?> arguments) {
      int p = 0;

      for (String argument : arguments.keySet()) {
        checkArgument(LOWERCASE.matcher(argument).matches(),
            "argument '%s' must start with a lowercase character", argument);
      }

      while (p < format.length()) {
        int nextP = format.indexOf("$", p);
        if (nextP == -1) {
          formatParts.add(format.substring(p));
          break;
        }

        if (p != nextP) {
          formatParts.add(format.substring(p, nextP));
          p = nextP;
        }

        Matcher matcher = null;
        int colon = format.indexOf(':', p);
        if (colon != -1) {
          int endIndex = Math.min(colon + 2, format.length());
          matcher = NAMED_ARGUMENT.matcher(format.substring(p, endIndex));
        }
        if (matcher != null && matcher.lookingAt()) {
          String argumentName = matcher.group("argumentName");
          checkArgument(arguments.containsKey(argumentName), "Missing named argument for $%s",
              argumentName);
          char formatChar = matcher.group("typeChar").charAt(0);
          addArgument(format, formatChar, arguments.get(argumentName));
          formatParts.add("$" + formatChar);
          p += matcher.regionEnd();
        } else {
          checkArgument(p < format.length() - 1, "dangling $ at end");
          checkArgument(isNoArgPlaceholder(format.charAt(p + 1)),
              "unknown format $%s at %s in '%s'", format.charAt(p + 1), p + 1, format);
          formatParts.add(format.substring(p, p + 2));
          p += 2;
        }
      }

      return this;
    }

    /**
     * Add code with positional or relative arguments.
     *
     * <p>Relative arguments map 1:1 with the placeholders in the format string.
     *
     * <p>Positional arguments use an index after the placeholder to identify which argument index
     * to use. For example, for a literal to reference the 3rd argument: "$3L" (1 based index)
     *
     * <p>Mixing relative and positional arguments in a call to add is invalid and will result in an
     * error.
     */
    public Builder add(String format, Object... args) {
      boolean hasRelative = false;
      boolean hasIndexed = false;

      int relativeParameterCount = 0;
      int[] indexedParameterCount = new int[args.length];

      for (int p = 0; p < format.length(); ) {
        if (format.charAt(p) != '$') {
          int nextP = format.indexOf('$', p + 1);
          if (nextP == -1) nextP = format.length();
          formatParts.add(format.substring(p, nextP));
          p = nextP;
          continue;
        }

        p++; // '$'.

        // Consume zero or more digits, leaving 'c' as the first non-digit char after the '$'.
        int indexStart = p;
        char c;
        do {
          checkArgument(p < format.length(), "dangling format characters in '%s'", format);
          c = format.charAt(p++);
        } while (c >= '0' && c <= '9');
        int indexEnd = p - 1;

        // If 'c' doesn't take an argument, we're done.
        if (isNoArgPlaceholder(c)) {
          checkArgument(
              indexStart == indexEnd, "$$, $>, $<, $[, $], $W, and $Z may not have an index");
          formatParts.add("$" + c);
          continue;
        }

        // Find either the indexed argument, or the relative argument. (0-based).
        int index;
        if (indexStart < indexEnd) {
          index = Integer.parseInt(format.substring(indexStart, indexEnd)) - 1;
          hasIndexed = true;
          if (args.length > 0) {
            indexedParameterCount[index % args.length]++; // modulo is needed, checked below anyway
          }
        } else {
          index = relativeParameterCount;
          hasRelative = true;
          relativeParameterCount++;
        }

        checkArgument(index >= 0 && index < args.length,
            "index %d for '%s' not in range (received %s arguments)",
            index + 1, format.substring(indexStart - 1, indexEnd + 1), args.length);
        checkArgument(!hasIndexed || !hasRelative, "cannot mix indexed and positional parameters");

        addArgument(format, c, args[index]);

        formatParts.add("$" + c);
      }

      if (hasRelative) {
        checkArgument(relativeParameterCount >= args.length,
            "unused arguments: expected %s, received %s", relativeParameterCount, args.length);
      }
      if (hasIndexed) {
        List<String> unused = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
          if (indexedParameterCount[i] == 0) {
            unused.add("$" + (i + 1));
          }
        }
        String s = unused.size() == 1 ? "" : "s";
        checkArgument(unused.isEmpty(), "unused argument%s: %s", s, String.join(", ", unused));
      }
      return this;
    }

    private boolean isNoArgPlaceholder(char c) {
      return c == '$' || c == '>' || c == '<' || c == '[' || c == ']' || c == 'w' || c == 'z';
    }

    private void addArgument(String format, char c, Object arg) {
      switch (c) {
        case 'N':
          this.args.add(argToName(arg));
          break;
        case 'L':
          this.args.add(argToLiteral(arg));
          break;
        case 'S':
          this.args.add(argToString(arg));
          break;
        case 'T':
          this.args.add(argToType(arg));
          break;
        default:
          throw new IllegalArgumentException(
              String.format("invalid format string: '%s'", format));
      }
    }

    private String argToName(Object o) {
      if (o instanceof CharSequence) return o.toString();
      if (o instanceof ParameterSpec) return ((ParameterSpec) o).name;
      if (o instanceof FieldSpec) return ((FieldSpec) o).name;
      if (o instanceof MethodSpec) return ((MethodSpec) o).name;
      if (o instanceof TypeSpec) return ((TypeSpec) o).name;
      throw new IllegalArgumentException("expected name but was " + o);
    }

    private Object argToLiteral(Object o) {
      return o;
    }

    private String argToString(Object o) {
      return o != null ? String.valueOf(o) : null;
    }

    private TypeName argToType(Object o) {
      if (o instanceof TypeName) return (TypeName) o;
      if (o instanceof TypeMirror) return TypeName.get((TypeMirror) o);
      if (o instanceof Element) return TypeName.get(((Element) o).asType());
      if (o instanceof Type) return TypeName.get((Type) o);
      throw new IllegalArgumentException("expected type but was " + o);
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "if (foo == 5)".
     * Shouldn't contain braces or newline characters.
     */
    public Builder beginControlFlow(String controlFlow, Object... args) {
      add(controlFlow + " {\n", args);
      indent();
      return this;
    }

    /**
     * @param controlFlow the control flow construct and its code, such as "else if (foo == 10)".
     *     Shouldn't contain braces or newline characters.
     */
    public Builder nextControlFlow(String controlFlow, Object... args) {
      unindent();
      add("} " + controlFlow + " {\n", args);
      indent();
      return this;
    }

    public Builder endControlFlow() {
      unindent();
      add("}\n");
      return this;
    }

    /**
     * @param controlFlow the optional control flow construct and its code, such as
     *     "while(foo == 20)". Only used for "do/while" control flows.
     */
    public Builder endControlFlow(String controlFlow, Object... args) {
      unindent();
      add("} " + controlFlow + ";\n", args);
      return this;
    }

    public Builder addStatement(String format, Object... args) {
      add("$[");
      add(format, args);
      add(";\n$]");
      return this;
    }

    public Builder addStatement(CodeBlock codeBlock) {
      return addStatement("$L", codeBlock);
    }

    public Builder add(CodeBlock codeBlock) {
      formatParts.addAll(codeBlock.formatParts);
      args.addAll(codeBlock.args);
      return this;
    }

    public Builder indent() {
      this.formatParts.add("$>");
      return this;
    }

    public Builder unindent() {
      this.formatParts.add("$<");
      return this;
    }

    public Builder clear() {
      formatParts.clear();
      args.clear();
      return this;
    }

    public CodeBlock build() {
      return new CodeBlock(this);
    }
  }

  private static final class CodeBlockJoiner {
    private final String delimiter;
    private final Builder builder;
    private boolean first = true;

    CodeBlockJoiner(String delimiter, Builder builder) {
      this.delimiter = delimiter;
      this.builder = builder;
    }

    CodeBlockJoiner add(CodeBlock codeBlock) {
      if (!first) {
        builder.add(delimiter);
      }
      first = false;

      builder.add(codeBlock);
      return this;
    }

    CodeBlockJoiner merge(CodeBlockJoiner other) {
      CodeBlock otherBlock = other.builder.build();
      if (!otherBlock.isEmpty()) {
        add(otherBlock);
      }
      return this;
    }

    CodeBlock join() {
      return builder.build();
    }
  }
}
