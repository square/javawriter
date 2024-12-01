package com.squareup.javapoet;

/*
 * Copyright (C) 2019 Square, Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeVariableName;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Since it is impossible to import classes from the default package into other
 * modules, this test must live in this package.
 */
public class AnnotationIssueTest {
    //CS304 (manually written) Issue link: https://github.com/square/javapoet/issues/826
    List<AnnotationSpec> list = new ArrayList<>();

    @Test public void annotationOnTypeVariableName() {
        list.add(AnnotationSpec.builder(Double.class).build());

        TypeVariableName typeVar1 = TypeVariableName.get("T", Comparator.class, Serializable.class);
        typeVar1 = (TypeVariableName) typeVar1.annotated(AnnotationSpec.builder(Float.class).build());
        typeVar1 = typeVar1.annotated(list);
        assertThat(typeVar1.toString()).contains("Float");
        assertThat(typeVar1.toString()).contains("Double");

    }

    @Test public void annotationOnTypeVariableName2(){
        list.add(AnnotationSpec.builder(Integer.class).build());
        List<AnnotationSpec> subList = new ArrayList<>();
        subList.add(AnnotationSpec.builder(Float.class).build());

        TypeVariableName typeVar = TypeVariableName.get("int");
        typeVar = typeVar.annotated(list);
        typeVar = typeVar.annotated(subList);
        assertThat(typeVar.toString()).contains("Float");
        assertThat(typeVar.toString()).contains("Integer");
    }

}

