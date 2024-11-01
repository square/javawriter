package com.squareup.javapoet;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import org.junit.Assert;
import org.junit.Test;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.Compiler.javac;

public class ObjectInlinerTest {
    public static class TrustedPojo {
        public static final String TYPE = TrustedPojo.class.getCanonicalName();
        public String name;
        private int value;
        private TrustedPojo next;

        public TrustedPojo() {
        }

        public TrustedPojo(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public TrustedPojo getNext() {
            return next;
        }

        public void setNext(TrustedPojo next) {
            this.next = next;
        }

        public TrustedPojo withNext(TrustedPojo next) {
            this.next = next;
            return this;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            TrustedPojo that = (TrustedPojo) object;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    private static String getInlineResult(Object object) {
        return getInlineResult(object, (inliner) -> {});
    }

    private static String getInlineResult(Object object,
            Consumer<ObjectInliner> adapter) {
        StringBuilder result = new StringBuilder();
        CodeWriter codeWriter = new CodeWriter(result);
        ObjectInliner inliner = new ObjectInliner();
        adapter.accept(inliner);
        try {
            inliner.inlined(object).emit(codeWriter);
        } catch (IOException e) {
            throw new AssertionError("IOFailure", e);
        }
        return result.toString();
    }

    private String expectedResult(Class<?> type, String lambdaBody) {
        StringBuilder out = new StringBuilder();
        out.append("((");
        out.append(ObjectEmitter.getSerializedType(type).getCanonicalName());
        out.append(")((");
        out.append(Supplier.class.getCanonicalName());
        out.append(")(()->{");
        out.append(lambdaBody);
        out.append("})).get())");
        return out.toString().replaceAll("\\s+", "");
    }

    private String expectedInlinedTrustedPojo(TrustedPojo trustedPojo, String... identifiers) {
        return expectedInlinedTrustedPojo(trustedPojo, Arrays.asList(identifiers));
    }

    private String expectedInlinedTrustedPojo(TrustedPojo trustedPojo, List<String> identifiers) {
        StringBuilder out = new StringBuilder()
            .append(TrustedPojo.TYPE).append(identifiers.get(0)).append(";")
            .append(identifiers.get(0)).append(" = new ")
                .append(TrustedPojo.TYPE).append("();")
            .append(identifiers.get(0)).append(".name = \"")
                .append(trustedPojo.name).append("\";");

        if (trustedPojo.next == null) {
            out.append(identifiers.get(0)).append(".setNext(null);");
        } else if (trustedPojo.next == trustedPojo) {
            out.append(identifiers.get(0)).append(".setNext(")
                    .append(identifiers.get(0)).append(");");
        } else {
            out.append(expectedInlinedTrustedPojo(trustedPojo.next,
                    identifiers.subList(1, identifiers.size())));
            out.append(identifiers.get(0)).append(".setNext(")
                    .append(identifiers.get(1)).append(");");
        }
        out.append(identifiers.get(0)).append(".setValue(").
                append(trustedPojo.value).append(");");
        return out.toString();
    }

    private void assertCompiles(Class<?> type, String value) {
        JavaFile javaFile = JavaFile.builder("", TypeSpec
                .classBuilder("TestClass")
                        .addField(ObjectEmitter.getSerializedType(type), "field", Modifier.STATIC)
                .addStaticBlock(CodeBlock.of("field = $L;", value))
                .build()).build();
        Compilation compilation = javac().compile(javaFile.toJavaFileObject());
        CompilationSubject.assertThat(compilation).succeeded();
    }

    private void assertResult(String expected,
            Object object) {
        assertResult(expected, object, inliner -> {});
    }

    private void assertResult(String expected,
            Object object,
            Consumer<ObjectInliner> adapter) {
        String result = getInlineResult(object, adapter);
        assertThat(result.replaceAll("\\s+", "")).isEqualTo(expectedResult(object.getClass(), expected));
        assertCompiles(object.getClass(), result);
    }

    private void assertThrows(String errorMessage,
            Object object) {
        assertThrows(errorMessage, object, inliner -> {});
    }

    private void assertThrows(String errorMessage,
            Object object,
            Consumer<ObjectInliner> adapter) {
        try {
            getInlineResult(object, adapter);
            Assert.fail("Expected an exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageThat().isEqualTo(errorMessage);
        }

    }

    @Test
    public void testInlineNull() {
        assertThat(getInlineResult(null)).isEqualTo("null");
    }

    @Test
    public void testInlineBool() {
        assertResult("return true;", true);
    }

    @Test
    public void testInlineByte() {
        assertResult("return ((byte) 1);", (byte) 1);
    }

    @Test
    public void testInlineChar() {
        assertResult("return '\\u0061';", 'a');
    }

    @Test
    public void testInlineShort() {
        assertResult("return ((short) 1);", (short) 1);
    }

    @Test
    public void testInlineInt() {
        assertResult("return 1;", 1);
    }

    @Test
    public void testInlineLong() {
        assertResult("return 1L;", 1L);
    }

    @Test
    public void testInlineFloat() {
        assertResult("return 1.0f;", 1.0f);
    }

    @Test
    public void testInlineDouble() {
        assertResult("return 1.0d;", 1.0d);
    }

    @Test
    public void testInlineClass() {
        assertResult("return " + List.class.getCanonicalName() + ".class;", List.class);
    }

    public enum MyEnum {
        VALUE
    }

    @Test
    public void testInlineEnum() {
        assertResult("return " + MyEnum.class.getCanonicalName() + ".VALUE;", MyEnum.VALUE);
    }

    @Test
    public void testPrimitiveArrays() {
        assertResult(new StringBuilder()
                .append("int[] $$javapoet$int__;")
                .append("$$javapoet$int__ = new int[3];")
                .append("$$javapoet$int__[0] = 1;")
                .append("$$javapoet$int__[1] = 2;")
                .append("$$javapoet$int__[2] = 3;")
                .append("return $$javapoet$int__;")
                .toString(), new int[] {1, 2, 3});
    }

    @Test
    public void testObjectArrays() {
        TrustedPojo[] inlined = new TrustedPojo[] {
                new TrustedPojo("a", 1),
                new TrustedPojo("b", 2)
                        .withNext(new TrustedPojo("c", 3))
        };
        assertResult(new StringBuilder()
                .append(TrustedPojo.TYPE).append("[] ")
                .append("$$javapoet$TrustedPojo__;")
                .append("$$javapoet$TrustedPojo__ = new ")
                .append(TrustedPojo.TYPE).append("[2];")
                .append(expectedInlinedTrustedPojo(inlined[0], "$$javapoet$TrustedPojo"))
                .append("$$javapoet$TrustedPojo__[0] = $$javapoet$TrustedPojo;")
                .append(expectedInlinedTrustedPojo(inlined[1],
                        "$$javapoet$TrustedPojo_",
                        "$$javapoet$TrustedPojo___"))
                .append("$$javapoet$TrustedPojo__[1] = $$javapoet$TrustedPojo_;")
                .append("return $$javapoet$TrustedPojo__;")
                .toString(),
                inlined,
                inliner -> inliner.trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testPrimitiveList() {
        assertResult(new StringBuilder()
                .append(List.class.getCanonicalName()).append(" $$javapoet$List;")
                .append("$$javapoet$List = new ")
                .append(ArrayList.class.getCanonicalName()).append("(3);")
                .append("$$javapoet$List.add(1);")
                .append("$$javapoet$List.add(2);")
                .append("$$javapoet$List.add(3);")
                .append("return $$javapoet$List;")
                .toString(), Arrays.asList(1, 2, 3),
                inliner -> inliner.trustTypesAssignableTo(List.class));
    }

    @Test
    public void testObjectList() {
        List<TrustedPojo> inlined = Arrays.asList(
                new TrustedPojo("a", 1),
                new TrustedPojo("b", 2)
                        .withNext(new TrustedPojo("c", 3)));
        assertResult(new StringBuilder()
                        .append(List.class.getCanonicalName()).append(" $$javapoet$List;")
                        .append("$$javapoet$List = new ")
                        .append(ArrayList.class.getCanonicalName()).append("(2);")
                        .append(expectedInlinedTrustedPojo(inlined.get(0), "$$javapoet$TrustedPojo"))
                        .append("$$javapoet$List.add($$javapoet$TrustedPojo);")
                        .append(expectedInlinedTrustedPojo(inlined.get(1),
                                "$$javapoet$TrustedPojo_",
                                "$$javapoet$TrustedPojo__"))
                        .append("$$javapoet$List.add($$javapoet$TrustedPojo_);")
                        .append("return $$javapoet$List;")
                        .toString(),
                inlined,
                inliner -> inliner.trustTypesAssignableTo(List.class)
                        .trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testPrimitiveSet() {
        assertResult(new StringBuilder()
                .append(Set.class.getCanonicalName()).append(" $$javapoet$Set;")
                .append("$$javapoet$Set = new ")
                .append(LinkedHashSet.class.getCanonicalName()).append("(3);")
                .append("$$javapoet$Set.add(1);")
                .append("$$javapoet$Set.add(2);")
                .append("$$javapoet$Set.add(3);")
                .append("return $$javapoet$Set;")
                .toString(), new LinkedHashSet<>(Arrays.asList(1, 2, 3)),
                inliner -> inliner
                        .trustTypesAssignableTo(Set.class));
    }

    @Test
    public void testObjectSet() {
        Set<TrustedPojo> inlined = new LinkedHashSet<>(Arrays.asList(
                new TrustedPojo("a", 1),
                new TrustedPojo("b", 2)
                        .withNext(new TrustedPojo("c", 3))));

        Iterator<TrustedPojo> iterator = inlined.iterator();
        assertResult(new StringBuilder()
                        .append(Set.class.getCanonicalName()).append(" $$javapoet$Set;")
                        .append("$$javapoet$Set = new ")
                        .append(LinkedHashSet.class.getCanonicalName()).append("(2);")
                        .append(expectedInlinedTrustedPojo(iterator.next(), "$$javapoet$TrustedPojo"))
                        .append("$$javapoet$Set.add($$javapoet$TrustedPojo);")
                        .append(expectedInlinedTrustedPojo(iterator.next(),
                                "$$javapoet$TrustedPojo_",
                                "$$javapoet$TrustedPojo__"))
                        .append("$$javapoet$Set.add($$javapoet$TrustedPojo_);")
                        .append("return $$javapoet$Set;")
                        .toString(),
                inlined,
                inliner -> inliner
                        .trustTypesAssignableTo(Set.class)
                        .trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testPrimitiveMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        assertResult(new StringBuilder()
                .append(Map.class.getCanonicalName()).append(" $$javapoet$Map;")
                .append("$$javapoet$Map = new ")
                .append(LinkedHashMap.class.getCanonicalName()).append("(3);")
                .append("$$javapoet$Map.put(\"a\", 1);")
                .append("$$javapoet$Map.put(\"b\", 2);")
                .append("$$javapoet$Map.put(\"c\", 3);")
                .append("return $$javapoet$Map;")
                .toString(), map,
                inliner -> inliner.trustTypesAssignableTo(Map.class));
    }

    @Test
    public void testObjectValueMap() {
        Map<String, TrustedPojo> map = new LinkedHashMap<>();
        map.put("a", new TrustedPojo("a", 1));
        map.put("b", new TrustedPojo("b", 2).withNext(new TrustedPojo("c", 3)));
        assertResult(new StringBuilder()
                .append(Map.class.getCanonicalName()).append(" $$javapoet$Map;")
                .append("$$javapoet$Map = new ")
                .append(LinkedHashMap.class.getCanonicalName()).append("(2);")
                .append(expectedInlinedTrustedPojo(map.get("a"), "$$javapoet$TrustedPojo"))
                .append("$$javapoet$Map.put(\"a\", $$javapoet$TrustedPojo);")
                .append(expectedInlinedTrustedPojo(map.get("b"), "$$javapoet$TrustedPojo_", "$$javapoet$TrustedPojo__"))
                .append("$$javapoet$Map.put(\"b\", $$javapoet$TrustedPojo_);")
                .append("return $$javapoet$Map;")
                .toString(), map,
                inliner -> inliner
                        .trustTypesAssignableTo(Map.class)
                        .trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testObjectKeyMap() {
        Map<TrustedPojo, Integer> map = new LinkedHashMap<>();
        map.put(new TrustedPojo("a", 1), 1);
        map.put(new TrustedPojo("b", 2).withNext(new TrustedPojo("c", 3)), 2);
        Iterator<TrustedPojo> iterator = map.keySet().iterator();
        assertResult(new StringBuilder()
                        .append(Map.class.getCanonicalName()).append(" $$javapoet$Map;")
                        .append("$$javapoet$Map = new ")
                        .append(LinkedHashMap.class.getCanonicalName()).append("(2);")
                        .append(expectedInlinedTrustedPojo(iterator.next(), "$$javapoet$TrustedPojo"))
                        .append("$$javapoet$Map.put($$javapoet$TrustedPojo, 1);")
                        .append(expectedInlinedTrustedPojo(iterator.next(), "$$javapoet$TrustedPojo_", "$$javapoet$TrustedPojo__"))
                        .append("$$javapoet$Map.put($$javapoet$TrustedPojo_, 2);")
                        .append("return $$javapoet$Map;")
                        .toString(), map,
                inliner -> inliner
                        .trustTypesAssignableTo(Map.class)
                        .trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testTrustedObject() {
        TrustedPojo pojo = new TrustedPojo("a", 1)
                .withNext(new TrustedPojo("b", 2));
        assertResult(new StringBuilder()
                .append(expectedInlinedTrustedPojo(pojo, "$$javapoet$TrustedPojo", "$$javapoet$TrustedPojo_"))
                .append("return $$javapoet$TrustedPojo;")
                .toString(), pojo, inliner -> inliner.trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testTrustedObjectWithSelfReference() {
        TrustedPojo pojo = new TrustedPojo("a", 1);
        pojo.setNext(pojo);
        assertResult(new StringBuilder()
                .append(expectedInlinedTrustedPojo(pojo, "$$javapoet$TrustedPojo"))
                .append("return $$javapoet$TrustedPojo;")
                .toString(), pojo, inliner -> inliner.trustExactTypes(TrustedPojo.class));
    }

    @Test
    public void testUntrustedObjectThrows() {
        TrustedPojo pojo = new TrustedPojo("a", 1);
        pojo.setNext(pojo);
        assertThrows("Cannot serialize instance of (" + TrustedPojo.class.getCanonicalName() +
                ") because it is not an instance of a trusted type.", pojo);
    }

    @Test
    public void testUntrustedCollectionThrows() {
        assertThrows("Cannot serialize instance of (" + ArrayList.class.getCanonicalName() +
                ") because it is not an instance of a trusted type.", new ArrayList<>());
        assertThrows("Cannot serialize instance of (" + LinkedHashSet.class.getCanonicalName() +
                ") because it is not an instance of a trusted type.", new LinkedHashSet<>());
        assertThrows("Cannot serialize instance of (" + LinkedHashMap.class.getCanonicalName() +
                ") because it is not an instance of a trusted type.", new LinkedHashMap<>());
    }

    private static class PrivatePojo {

    }

    @Test
    public void testPrivatePojoThrows() {
        PrivatePojo pojo = new PrivatePojo();
        assertThrows("Cannot serialize type (" + PrivatePojo.class.getCanonicalName() +
                ") because it is not public.", pojo,
                inliner -> inliner.trustExactTypes(PrivatePojo.class));
    }

    public static class MissingSetterPojo {
        private String value;

        public MissingSetterPojo() {
        }

        public MissingSetterPojo(String value) {
            this.value = value;
        }
    }

    @Test
    public void testMissingSetterPojoThrows() {
        MissingSetterPojo pojo = new MissingSetterPojo();
        assertThrows("Cannot serialize type (" + MissingSetterPojo.class +
                        ") as it is missing a public setter method for field (value)" +
                        " of type (class java.lang.String).", pojo,
                inliner -> inliner.trustExactTypes(MissingSetterPojo.class));
    }

    public static class PrivateSetterPojo {
        private String value;

        public PrivateSetterPojo() {
        }

        public PrivateSetterPojo(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }

        private void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    public void testPrivateSetterPojoThrows() {
        PrivateSetterPojo pojo = new PrivateSetterPojo();
        assertThrows("Cannot serialize type (" + PrivateSetterPojo.class +
                        ") as it is missing a public setter method for field (value)" +
                        " of type (class java.lang.String).", pojo,
                inliner -> inliner.trustExactTypes(PrivateSetterPojo.class));
    }

    public static class MissingConstructorPojo {
        public MissingConstructorPojo(String value) {
        }
    }

    @Test
    public void testMissingConstructorPojoThrows() {
        MissingConstructorPojo pojo = new MissingConstructorPojo("a");
        assertThrows("Cannot serialize type (" + MissingConstructorPojo.class.getCanonicalName() +
                        ") because it does not have a public no-args constructor.", pojo,
                inliner -> inliner.trustExactTypes(MissingConstructorPojo.class));
    }

    public static class PrivateConstructorPojo {
        private PrivateConstructorPojo() {
        }
    }

    @Test
    public void testPrivateConstructorPojoThrows() {
        PrivateConstructorPojo pojo = new PrivateConstructorPojo();
        assertThrows("Cannot serialize type (" + PrivateConstructorPojo.class.getCanonicalName() +
                        ") because it does not have a public no-args constructor.", pojo,
                inliner -> inliner.trustExactTypes(PrivateConstructorPojo.class));
    }

    public static class RawTypePojo {
        private final static String TYPE = RawTypePojo.class.getCanonicalName();
        public Object value;

        public RawTypePojo() {
        }

        public RawTypePojo(Object value) {
            this.value = value;
        }
    }

    @Test
    public void testRawTypePojoWithTrustedValue() {
        RawTypePojo pojo = new RawTypePojo("taco");
        assertResult(new StringBuilder()
                        .append(RawTypePojo.TYPE).append(" $$javapoet$RawTypePojo;")
                        .append(" $$javapoet$RawTypePojo = new ")
                        .append(RawTypePojo.TYPE).append("();")
                        .append("$$javapoet$RawTypePojo.value = \"taco\";")
                        .append("return $$javapoet$RawTypePojo;")
                        .toString(),
                pojo,
                inliner -> inliner.trustExactTypes(RawTypePojo.class));
    }

    @Test
    public void testRawTypePojoWithUntrustedFieldThrows() {
        RawTypePojo pojo = new RawTypePojo(new ArrayList<>());
        assertThrows("Cannot serialize instance of (" + ArrayList.class.getCanonicalName()
                        + ") because it is not an instance of a trusted type.",
                pojo,
                inliner -> inliner.trustExactTypes(RawTypePojo.class));
    }

    private static class DurationInliner implements TypeInliner {

        @Override
        public boolean canInline(Object object) {
            return object instanceof Duration;
        }

        @Override
        public String inline(ObjectEmitter emitter, Object instance) {
            Duration duration = (Duration) instance;
            return CodeBlock.of("$T.ofNanos($V)", Duration.class,
                    emitter.inlined(duration.toNanos())).toString();
        }
    }

    @Test
    public void testCustomInliner() {
        Duration duration = Duration.ofSeconds(1L);
        String inlinedNanos = CodeBlock.of("$V", duration.toNanos()).toString();
        assertResult(new StringBuilder()
                .append(Duration.class.getCanonicalName())
                .append(" ")
                .append("$$javapoet$")
                .append(Duration.class.getSimpleName())
                .append(" = ")
                .append(Duration.class.getCanonicalName())
                .append(".ofNanos(")
                .append(inlinedNanos)
                .append(");")
                .append("return $$javapoet$")
                        .append(Duration.class.getSimpleName()).append(";")
                .toString(),
                duration,
                inliner -> inliner
                        .trustExactTypes(Duration.class)
                        .addTypeInliner(new DurationInliner()));
    }

    @Test
    public void testCustomInlinerNotCalledOnUntrustedTypes() {
        Duration duration = Duration.ofSeconds(1L);
        assertThrows("Cannot serialize instance of (" + Duration.class.getCanonicalName() + ") because it is not an instance of a trusted type.",
                duration,
                inliner -> inliner
                        .addTypeInliner(new DurationInliner()));
    }

    @Test
    public void testCustomInlinerIgnoresUnknownTypes() {
        TrustedPojo pojo = new TrustedPojo("a", 1);
        assertResult(new StringBuilder()
                        .append(expectedInlinedTrustedPojo(pojo, "$$javapoet$TrustedPojo"))
                        .append("return $$javapoet$TrustedPojo;")
                        .toString(),
                pojo,
                inliner -> inliner
                        .trustExactTypes(TrustedPojo.class)
                        .addTypeInliner(new DurationInliner()));
    }

    @Test
    public void testUseCustomPrefix() {
        TrustedPojo pojo = new TrustedPojo("a", 1);
        assertResult(new StringBuilder()
                        .append(expectedInlinedTrustedPojo(pojo, "test$TrustedPojo"))
                        .append("return test$TrustedPojo;")
                        .toString(),
                pojo,
                inliner -> inliner
                        .useNamePrefix("test$")
                        .trustExactTypes(TrustedPojo.class));
    }

    private static class RecursiveInliner implements TypeInliner {

        @Override
        public boolean canInline(Object object) {
            return object instanceof TrustedPojo;
        }

        @Override
        public String inline(ObjectEmitter emitter, Object instance) throws IOException {
            TrustedPojo pojo = (TrustedPojo) instance;
            emitter.newName(pojo.name, pojo);
            emitter.emit("$T $N = new $T();", TrustedPojo.class, emitter.getName(pojo), TrustedPojo.class);
            emitter.emit("$N.name = $S + $V;", emitter.getName(pojo), "Taco ", emitter.inlined(pojo.name));
            emitter.emit("$N.setValue($V);", emitter.getName(pojo), emitter.inlined(pojo.value));
            emitter.emit("$N.setNext($V);", emitter.getName(pojo), emitter.inlined(pojo.next));
            return emitter.getName(pojo);
        }
    }

    private String expectedCustomInlinedTrustedPojo(TrustedPojo trustedPojo, String prefix, String suffix) {
        StringBuilder out = new StringBuilder()
                .append(TrustedPojo.TYPE).append(prefix).append(trustedPojo.name)
                .append(" = new ")
                .append(TrustedPojo.TYPE).append("();")
                .append(prefix).append(trustedPojo.name).append(".name = \"")
                .append("Taco\" + ").append(CodeBlock.of("$V", trustedPojo.name)).append(";")
                .append(prefix).append(trustedPojo.name).append(".setValue(")
                .append(CodeBlock.of("$V", trustedPojo.value)).append(");");

        if (trustedPojo.next == null) {
            out.append(prefix).append(trustedPojo.name).append(".setNext(null);");
        } else {
            out.append(prefix).append(trustedPojo.name).append(".setNext(")
                    .append("((").append(TrustedPojo.class.getCanonicalName()).append(")((")
                    .append(Supplier.class.getCanonicalName()).append(")(()->{")
                    .append(expectedCustomInlinedTrustedPojo(trustedPojo.next, prefix, suffix + "_"))
                    .append("return ").append(prefix).append(TrustedPojo.class.getSimpleName()).append(suffix).append("_").append(";")
                    .append("})).get())")
                    .append(");");
        }
        out.append(TrustedPojo.class.getCanonicalName())
                .append(prefix).append(TrustedPojo.class.getSimpleName()).append(suffix)
                .append(" = ")
                .append(prefix).append(trustedPojo.name)
                .append(";");
        return out.toString();
    }

    @Test
    public void testCustomInlineTrustedObject() {
        TrustedPojo pojo = new TrustedPojo("a", 1)
                .withNext(new TrustedPojo("b", 2));
        assertResult(new StringBuilder()
                .append(expectedCustomInlinedTrustedPojo(pojo, "$$javapoet$", ""))
                .append("return $$javapoet$").append(TrustedPojo.class.getSimpleName()).append(";")
                .toString(), pojo, inliner -> inliner.trustExactTypes(TrustedPojo.class)
                .addTypeInliner(new RecursiveInliner()));
    }

    @Test
    public void testCustomInlineTrustedObjectWithSelfReference() {
        TrustedPojo pojo = new TrustedPojo("a", 1);
        pojo.setNext(pojo);
        assertThrows("Cannot serialize an object of type ("
                + TrustedPojo.class.getCanonicalName() + ") because it contains a circular reference.", pojo,
                inliner -> inliner.trustExactTypes(TrustedPojo.class)
                .addTypeInliner(new RecursiveInliner()));
    }

    public static class PairPojo {
        private PairPojo left;
        private PairPojo right;

        public PairPojo(PairPojo left, PairPojo right) {
            this.left = left;
            this.right = right;
        }

        public PairPojo getLeft() {
            return left;
        }

        public PairPojo getRight() {
            return right;
        }
    }

    private static class PairPojoInliner implements TypeInliner {

        @Override
        public boolean canInline(Object object) {
            return object instanceof PairPojo;
        }

        @Override
        public String inline(ObjectEmitter emitter, Object instance)  {
            PairPojo pair = (PairPojo) instance;
            return CodeBlock.of("new $T($V, $V)",
                    PairPojo.class,
                    emitter.inlined(pair.getLeft()),
                    emitter.inlined(pair.getRight())).toString();
        }
    }

    private String expectedCustomInlinedPairPojo(PairPojo pairPojo, String prefix, String suffix) {
        StringBuilder out = new StringBuilder()
                .append(PairPojo.class.getCanonicalName())
                .append(" ")
                .append(prefix).append(PairPojo.class.getSimpleName()).append(suffix)
                .append(" = new ")
                .append(PairPojo.class.getCanonicalName())
                .append("(");

        if (pairPojo.left == null) {
            out.append("null");
        } else {
            out.append("((").append(PairPojo.class.getCanonicalName()).append(")((")
                    .append(Supplier.class.getCanonicalName()).append(")(()->{");
            out.append(expectedCustomInlinedPairPojo(pairPojo.left, prefix, suffix + "_"))
                    .append("return ").append(prefix).append(PairPojo.class.getSimpleName()).append(suffix).append("_");
            out.append(";})).get())");
        }

        out.append(", ");

        if (pairPojo.right == null) {
            out.append("null");
        } else {
            out.append("((").append(PairPojo.class.getCanonicalName()).append(")((")
                    .append(Supplier.class.getCanonicalName()).append(")(()->{");
            out.append(expectedCustomInlinedPairPojo(pairPojo.right, prefix, suffix + "_"))
                    .append("return ").append(prefix).append(PairPojo.class.getSimpleName()).append(suffix).append("_");
            out.append(";})).get())");
        }

        out.append(");");


        return out.toString();
    }

    @Test
    public void testCustomInlineTrustedObjectWithSiblingReference() {
        PairPojo common = new PairPojo(null, null);
        PairPojo root = new PairPojo(common, common);
        // Make sure the sibling emitters do not try to share common, since
        // they cannot access each other's variables
        assertResult(new StringBuilder()
                        .append(expectedCustomInlinedPairPojo(root, "$$javapoet$", ""))
                        .append("return $$javapoet$").append(PairPojo.class.getSimpleName()).append(";")
                        .toString(),
                root,
                inliner -> inliner.trustExactTypes(PairPojo.class)
                        .addTypeInliner(new PairPojoInliner()));
    }
}
