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

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class NameAllocatorTest {

  @Test public void usage() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("foo", 1)).isEqualTo("foo");
    assertThat(nameAllocator.newName("bar", 2)).isEqualTo("bar");
    assertThat(nameAllocator.get(1)).isEqualTo("foo");
    assertThat(nameAllocator.get(2)).isEqualTo("bar");
  }

  @Test public void nameCollision() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("foo")).isEqualTo("foo");
    assertThat(nameAllocator.newName("foo")).isEqualTo("foo_");
    assertThat(nameAllocator.newName("foo")).isEqualTo("foo__");
  }

  @Test public void nameCollisionWithTag() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("foo", 1)).isEqualTo("foo");
    assertThat(nameAllocator.newName("foo", 2)).isEqualTo("foo_");
    assertThat(nameAllocator.newName("foo", 3)).isEqualTo("foo__");
    assertThat(nameAllocator.get(1)).isEqualTo("foo");
    assertThat(nameAllocator.get(2)).isEqualTo("foo_");
    assertThat(nameAllocator.get(3)).isEqualTo("foo__");
  }

  @Test public void characterMappingSubstitute() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("a-b", 1)).isEqualTo("a_b");
  }

  @Test public void characterMappingSurrogate() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("a\uD83C\uDF7Ab", 1)).isEqualTo("a_b");
  }

  @Test public void characterMappingInvalidStartButValidPart() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("1ab", 1)).isEqualTo("_1ab");
    assertThat(nameAllocator.newName("a-1", 2)).isEqualTo("a_1");
  }

  @Test public void characterMappingInvalidStartIsInvalidPart() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("&ab", 1)).isEqualTo("_ab");
  }

  @Test public void javaKeyword() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("public", 1)).isEqualTo("public_");
    assertThat(nameAllocator.get(1)).isEqualTo("public_");
  }

  @Test public void tagReuseForbidden() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.newName("foo", 1);
    try {
      nameAllocator.newName("bar", 1);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("tag 1 cannot be used for both 'foo' and 'bar'");
    }
  }

  @Test public void useBeforeAllocateForbidden() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    try {
      nameAllocator.get(1);
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().isEqualTo("unknown tag: 1");
    }
  }

  @Test public void cloneUsage() throws Exception {
    NameAllocator outterAllocator = new NameAllocator();
    outterAllocator.newName("foo", 1);

    NameAllocator innerAllocator1 = outterAllocator.clone();
    assertThat(innerAllocator1.newName("bar", 2)).isEqualTo("bar");
    assertThat(innerAllocator1.newName("foo", 3)).isEqualTo("foo_");

    NameAllocator innerAllocator2 = outterAllocator.clone();
    assertThat(innerAllocator2.newName("foo", 2)).isEqualTo("foo_");
    assertThat(innerAllocator2.newName("bar", 3)).isEqualTo("bar");
  }
  //CS304 Issue link: https://github.com/square/javapoet/issues/774
  @Test public void ignoringIgnorableChars() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("ab")).isEqualTo("ab");
    assertThat(nameAllocator.newName("a\u0008b")).isEqualTo("ab_");
  }

  //CS304 Issue link: https://github.com/square/javapoet/issues/774
  @Test public void multipleIgnorableChars() throws Exception {
    NameAllocator nameAllocator = new NameAllocator();
    assertThat(nameAllocator.newName("a\u007F\u0008c")).isEqualTo("ac");
    assertThat(nameAllocator.newName("ac")).isEqualTo("ac_");
  }

}
