package com.squareup.javapoet;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class ObjectInliner {
    private static final String DEFAULT_NAME_PREFIX = "$$javapoet$";
    private static final ObjectInliner DEFAULT = new ObjectInliner();
    private final List<TypeInliner> typeInliners = new ArrayList<>();

    /**
     * This is the Set of all exact types we trust,
     * to prevent malicious classes with malicious setters
     * from being generated.
     */
    private final Set<Class<?>> trustedExactTypes = new HashSet<>();

    /**
     * This is the Set of all assignable types we trust,
     * which allows anything that can be assigned to them to
     * be generated. Be careful with these; there can be a
     * malicious subclass you do not know about.
     */
    private final Set<Class<?>> trustedAssignableTypes = new HashSet<>();
    private final Function<CodeWriter, ObjectEmitter> emitterFactory;
    private String namePrefix;

    public ObjectInliner() {
        this.namePrefix = DEFAULT_NAME_PREFIX;
        this.emitterFactory = codeWriter -> new ObjectEmitter(this, codeWriter);
    }

    public static ObjectInliner getDefault() {
        return DEFAULT;
    }

    public ObjectInliner useNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
        return this;
    }

    public ObjectInliner addTypeInliner(TypeInliner typeInliner) {
        typeInliners.add(typeInliner);
        return this;
    }

    /**
     * Trust everything assignable to the given type. Do not call this method
     * if users can create subclasses of that type, since this will allow
     * the user to execute arbitrary code in the generated class.
     */
    public ObjectInliner trustTypesAssignableTo(Class<?> assignableType) {
        trustedAssignableTypes.add(assignableType);
        return this;
    }

    /**
     * Trust the exact types reachable from the given type.
     * These are:
     * <ul>
     * <li>The type of its fields.
     * <li>The type of any fields inherited from a superclass.
     * <li>The type and its supertypes.
     * </ul>
     */
    public ObjectInliner trustExactTypes(Class<?> exactType) {
        if (trustedExactTypes.contains(exactType)) {
            return this;
        }
        trustedExactTypes.add(exactType);
        for (Field field : exactType.getDeclaredFields()) {
            trustExactTypes(field.getType());
        }
        if (exactType.getSuperclass() != null) {
            trustExactTypes(exactType.getSuperclass());
        }
        return this;
    }

    /**
     * Trust everything. Do not call this method if you are passing in
     * user-generated instances of arbitrary types, since this will allow
     * the user to execute arbitrary code in the generated class.
     */
    public ObjectInliner trustEverything() {
        return trustTypesAssignableTo(Object.class);
    }

    List<TypeInliner> getTypeInliners() {
        return typeInliners;
    }

    Set<Class<?>> getTrustedExactTypes() {
        return trustedExactTypes;
    }

    Set<Class<?>> getTrustedAssignableTypes() {
        return trustedAssignableTypes;
    }

    String getNamePrefix() {
        return namePrefix;
    }

    public static class Inlined {
        private final Function<CodeWriter, ObjectEmitter> emitterFactory;
        private final Object value;

        Inlined(Function<CodeWriter, ObjectEmitter> emitterFactory, Object value) {
            this.emitterFactory = emitterFactory;
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        void emit(CodeWriter codeWriter) throws IOException  {
            emitterFactory.apply(codeWriter).emit(value);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object)
                return true;
            if (object == null || getClass() != object.getClass())
                return false;
            Inlined inlined = (Inlined) object;
            return Objects.equals(emitterFactory, inlined.emitterFactory)
                    && Objects.equals(value, inlined.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(emitterFactory, value);
        }
    }

    public Inlined inlined(Object object) {
        return new Inlined(emitterFactory, object);
    }
}
