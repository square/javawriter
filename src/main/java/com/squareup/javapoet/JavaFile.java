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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

import static com.squareup.javapoet.Util.checkArgument;
import static com.squareup.javapoet.Util.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/** A Java file containing a single top level class. */
public final class JavaFile {
  private static final Appendable NULL_APPENDABLE = new Appendable() {
    @Override public Appendable append(CharSequence charSequence) {
      return this;
    }
    @Override public Appendable append(CharSequence charSequence, int start, int end) {
      return this;
    }
    @Override public Appendable append(char c) {
      return this;
    }
  };

  public final CodeBlock fileComment;
  public final String packageName;
  public final TypeSpec typeSpec;
  public final boolean skipJavaLangImports;
  private final Set<String> staticImports;
  private final String indent;

  private JavaFile(Builder builder) {
    this.fileComment = builder.fileComment.build();
    this.packageName = builder.packageName;
    this.typeSpec = builder.typeSpec;
    this.skipJavaLangImports = builder.skipJavaLangImports;
    this.staticImports = Util.immutableSet(builder.staticImports);
    this.indent = builder.indent;
  }

  public void writeTo(Appendable out) throws IOException {
    // First pass: emit the entire class, just to collect the types we'll need to import.
    CodeWriter importsCollector = new CodeWriter(NULL_APPENDABLE, indent, staticImports);
    emit(importsCollector);
    Map<String, ClassName> suggestedImports = importsCollector.suggestedImports();

    // Second pass: write the code, taking advantage of the imports.
    CodeWriter codeWriter = new CodeWriter(out, indent, suggestedImports, staticImports);
    emit(codeWriter);
  }

  /** Writes this to {@code directory} as UTF-8 using the standard directory structure. */
  public void writeTo(Path directory) throws IOException {
    checkArgument(Files.notExists(directory) || Files.isDirectory(directory),
        "path %s exists but is not a directory.", directory);
    Path outputDirectory = directory;
    if (!packageName.isEmpty()) {
      for (String packageComponent : packageName.split("\\.")) {
        outputDirectory = outputDirectory.resolve(packageComponent);
      }
      Files.createDirectories(outputDirectory);
    }

    Path outputPath = outputDirectory.resolve(typeSpec.name + ".java");
    try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outputPath), UTF_8)) {
      writeTo(writer);
    }
  }

  /** Writes this to {@code directory} as UTF-8 using the standard directory structure. */
  public void writeTo(File directory) throws IOException {
    writeTo(directory.toPath());
  }

  /** Writes this to {@code filer}. */
  public void writeTo(Filer filer) throws IOException {
    String fileName = packageName.isEmpty()
        ? typeSpec.name
        : packageName + "." + typeSpec.name;
    List<Element> originatingElements = typeSpec.originatingElements;
    JavaFileObject filerSourceFile = filer.createSourceFile(fileName,
        originatingElements.toArray(new Element[originatingElements.size()]));
    try (Writer writer = filerSourceFile.openWriter()) {
      writeTo(writer);
    } catch (Exception e) {
      try {
        filerSourceFile.delete();
      } catch (Exception ignored) {
      }
      throw e;
    }
  }

  private void emit(CodeWriter codeWriter) throws IOException {
    codeWriter.pushPackage(packageName);

    if (!fileComment.isEmpty()) {
      codeWriter.emitComment(fileComment);
    }

    if (!packageName.isEmpty()) {
      codeWriter.emit("package $L;\n", packageName);
      codeWriter.emit("\n");
    }

    if (!staticImports.isEmpty()) {
      for (String signature : staticImports) {
        codeWriter.emit("import static $L;\n", signature);
      }
      codeWriter.emit("\n");
    }

    int importedTypesCount = 0;
    for (ClassName className : new TreeSet<>(codeWriter.importedTypes().values())) {
      if (skipJavaLangImports && className.packageName().equals("java.lang")) continue;
      codeWriter.emit("import $L;\n", className.withoutAnnotations());
      importedTypesCount++;
    }

    if (importedTypesCount > 0) {
      codeWriter.emit("\n");
    }

    typeSpec.emit(codeWriter, null, Collections.emptySet());

    codeWriter.popPackage();
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
    try {
      StringBuilder result = new StringBuilder();
      writeTo(result);
      return result.toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  public JavaFileObject toJavaFileObject() {
    URI uri = URI.create((packageName.isEmpty()
        ? typeSpec.name
        : packageName.replace('.', '/') + '/' + typeSpec.name)
        + Kind.SOURCE.extension);
    return new SimpleJavaFileObject(uri, Kind.SOURCE) {
      private final long lastModified = System.currentTimeMillis();
      @Override public String getCharContent(boolean ignoreEncodingErrors) {
        return JavaFile.this.toString();
      }
      @Override public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(getCharContent(true).getBytes(UTF_8));
      }
      @Override public long getLastModified() {
        return lastModified;
      }
    };
  }

  public static Builder builder(String packageName, TypeSpec typeSpec) {
    checkNotNull(packageName, "packageName == null");
    checkNotNull(typeSpec, "typeSpec == null");
    return new Builder(packageName, typeSpec);
  }

  public Builder toBuilder() {
    Builder builder = new Builder(packageName, typeSpec);
    builder.fileComment.add(fileComment);
    builder.skipJavaLangImports = skipJavaLangImports;
    builder.indent = indent;
    return builder;
  }

  public static final class Builder {
    private final String packageName;
    private final TypeSpec typeSpec;
    private final CodeBlock.Builder fileComment = CodeBlock.builder();
    private final Set<String> staticImports = new TreeSet<>();
    private boolean skipJavaLangImports;
    private IndentChar indentChar = IndentChar.SPACE;
    private String indent = "  ";

    private Builder(String packageName, TypeSpec typeSpec) {
      this.packageName = packageName;
      this.typeSpec = typeSpec;
    }

    public Builder addFileComment(String format, Object... args) {
      this.fileComment.add(format, args);
      return this;
    }

    public Builder addStaticImport(Enum<?> constant) {
      return addStaticImport(ClassName.get(constant.getDeclaringClass()), constant.name());
    }

    public Builder addStaticImport(Class<?> clazz, String... names) {
      return addStaticImport(ClassName.get(clazz), names);
    }

    public Builder addStaticImport(ClassName className, String... names) {
      checkArgument(className != null, "className == null");
      checkArgument(names != null, "names == null");
      checkArgument(names.length > 0, "names array is empty");
      for (String name : names) {
        checkArgument(name != null, "null entry in names array: %s", Arrays.toString(names));
        staticImports.add(className.canonicalName + "." + name);
      }
      return this;
    }

    /**
     * Call this to omit imports for classes in {@code java.lang}, such as {@code java.lang.String}.
     *
     * <p>By default, JavaPoet explicitly imports types in {@code java.lang} to defend against
     * naming conflicts. Suppose an (ill-advised) class is named {@code com.example.String}. When
     * {@code java.lang} imports are skipped, generated code in {@code com.example} that references
     * {@code java.lang.String} will get {@code com.example.String} instead.
     */
    public Builder skipJavaLangImports(boolean skipJavaLangImports) {
      this.skipJavaLangImports = skipJavaLangImports;
      return this;
    }

    /**
     * Sets the indentation of the Java file to the given String, filtering out any invalid characters, such as letters or
     * symbols. Uses the {@link IndentChar} enum to define which characters are allowed.
     *
     * @param indent Value of the indent
    */
    public Builder indent(String indent) {
      String allowedChars = Arrays.stream(IndentChar.values())
                                  .map(IndentChar::toString).reduce((a, b) -> a+b).orElse(" ");
      this.indent = indent.replaceAll("[^"+allowedChars+"]", "");
      return this;
    }

    /**
     * Sets the length of the indentation in the Java file. The character used in the indent is
     * stored in {@link #indentChar}. See {@link #indentChar(IndentChar)} for setting this value.
     *
     * @param indentCount Number of characters to indent with
     */
    public Builder indent(int indentCount) {
      char[] indentChars = new char[indentCount];
      Arrays.fill(indentChars, indentChar.value());
      this.indent = new String(indentChars);
      return this;
    }

    /**
     * Sets the indentation in the Java file to a given number of given characters. Combines the functionality of
     * {@link #indent(int)} and {@link #indentChar(IndentChar)}.
     *
     * @param indentCount Number of characters to indent with
     * @param indentChar The character used to indent the file
     */
    public Builder indent(int indentCount, IndentChar indentChar) {
      this.indentChar = indentChar;
      this.indent(indentCount);
      return this;
    }

    /**
     * Sets the characters of the indentation in the Java file to the given characters. See {@link #indent(int)} for
     * setting the number of characters to use.
     *
     * @param indentChar The character used to indent the file
     */
    public Builder indentChar(IndentChar indentChar) {
      this.indentChar = indentChar;
      this.indent = this.indent.replaceAll(".", indentChar.value().toString());
      return this;
    }

    public JavaFile build() {
      return new JavaFile(this);
    }
  }

  /**
   * An enum defining characters allowed for use in indentations.
   */
  public enum IndentChar {
    TAB('\t'), SPACE(' ');
    private final Character indentChar;

    IndentChar(Character indentChar) {
      this.indentChar = indentChar;
    }

    public Character value() {
      return indentChar;
    }

    @Override
    public String toString() {
      return indentChar.toString();
    }
  }
}
