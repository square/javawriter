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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.lang.model.SourceVersion;

import static com.squareup.javapoet.Util.checkNotNull;

/**
 * Assigns Java identifier names to avoid collisions, keywords, and invalid characters. To use,
 * first create an instance and allocate all of the names that you need. Typically this is a
 * mix of user-supplied names and constants: <pre>   {@code
 *
 *   NameAllocator nameAllocator = new NameAllocator();
 *   for (MyProperty property : properties) {
 *     nameAllocator.newName(property.name(), property);
 *   }
 *   nameAllocator.newName("sb", "string builder");
 * }</pre>
 *
 * Pass a unique tag object to each allocation. The tag scopes the name, and can be used to look up
 * the allocated name later. Typically the tag is the object that is being named. In the above
 * example we use {@code property} for the user-supplied property names, and {@code "string
 * builder"} for our constant string builder.
 *
 * <p>Once we've allocated names we can use them when generating code: <pre>   {@code
 *
 *   MethodSpec.Builder builder = MethodSpec.methodBuilder("toString")
 *       .addAnnotation(Override.class)
 *       .addModifiers(Modifier.PUBLIC)
 *       .returns(String.class);
 *
 *   builder.addStatement("$1T $2N = new $1T()",
 *       StringBuilder.class, nameAllocator.get("string builder"));
 *   for (MyProperty property : properties) {
 *     builder.addStatement("$N.append($N)",
 *         nameAllocator.get("string builder"), nameAllocator.get(property));
 *   }
 *   builder.addStatement("return $N", nameAllocator.get("string builder"));
 *   return builder.build();
 * }</pre>
 *
 * The above code generates unique names if presented with conflicts. Given user-supplied properties
 * with names {@code ab} and {@code sb} this generates the following:  <pre>   {@code
 *
 *   &#64;Override
 *   public String toString() {
 *     StringBuilder sb_ = new StringBuilder();
 *     sb_.append(ab);
 *     sb_.append(sb);
 *     return sb_.toString();
 *   }
 * }</pre>
 *
 * The underscore is appended to {@code sb} to avoid conflicting with the user-supplied {@code sb}
 * property. Underscores are also prefixed for names that start with a digit, and used to replace
 * name-unsafe characters like space or dash.
 *
 * <p>When dealing with multiple independent inner scopes, use a {@link #clone()} of the
 * NameAllocator used for the outer scope to further refine name allocation for a specific inner
 * scope.
 */
public final class NameAllocator implements Cloneable {
  private final Set<String> allocatedNames;
  private final Map<Object, String> tagToName;

  public NameAllocator() {
    this(new LinkedHashSet<>(), new LinkedHashMap<>());
  }

  private NameAllocator(LinkedHashSet<String> allocatedNames,
                        LinkedHashMap<Object, String> tagToName) {
    this.allocatedNames = allocatedNames;
    this.tagToName = tagToName;
  }

  /**
   * Return a new name using {@code suggestion} that will not be a Java identifier or clash with
   * other names.
   */
  public String newName(String suggestion) {
    return newName(suggestion, UUID.randomUUID().toString());
  }

  /**
   * Return a new name using {@code suggestion} that will not be a Java identifier or clash with
   * other names. The returned value can be queried multiple times by passing {@code tag} to
   * {@link #get(Object)}.
   */
  public String newName(String suggestion, Object tag) {
    checkNotNull(suggestion, "suggestion");
    checkNotNull(tag, "tag");

    suggestion = toJavaIdentifier(suggestion);

    while (SourceVersion.isKeyword(suggestion) || !allocatedNames.add(suggestion)) {
      suggestion = suggestion + "_";
    }

    String replaced = tagToName.put(tag, suggestion);
    if (replaced != null) {
      tagToName.put(tag, replaced); // Put things back as they were!
      throw new IllegalArgumentException("tag " + tag + " cannot be used for both '" + replaced
          + "' and '" + suggestion + "'");
    }

    return suggestion;
  }

  //CS304 Issue link: https://github.com/square/javapoet/issues/774
  /**
   * To change a suggested name to a valid Java identifier name
   * @param suggestion The suggested name
   * @return a valid Java identifier name
   */
  public static String toJavaIdentifier(String suggestion) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < suggestion.length(); ) {
      int codePoint = suggestion.codePointAt(i);
      if (i == 0
          && !Character.isJavaIdentifierStart(codePoint)
          && Character.isJavaIdentifierPart(codePoint)) {
        result.append("_");
      }
      if(!Character.isIdentifierIgnorable(codePoint)) {
        int validCodePoint = Character.isJavaIdentifierPart(codePoint) ? codePoint : '_';
        result.appendCodePoint(validCodePoint);
      }
      i += Character.charCount(codePoint);
    }
    return result.toString();
  }

  /** Retrieve a name created with {@link #newName(String, Object)}. */
  public String get(Object tag) {
    String result = tagToName.get(tag);
    if (result == null) {
      throw new IllegalArgumentException("unknown tag: " + tag);
    }
    return result;
  }

  /**
   * Create a deep copy of this NameAllocator. Useful to create multiple independent refinements
   * of a NameAllocator to be used in the respective definition of multiples, independently-scoped,
   * inner code blocks.
   *
   * @return A deep copy of this NameAllocator.
   */
  @Override
  public NameAllocator clone() {
    return new NameAllocator(
        new LinkedHashSet<>(this.allocatedNames),
        new LinkedHashMap<>(this.tagToName));
  }

}
