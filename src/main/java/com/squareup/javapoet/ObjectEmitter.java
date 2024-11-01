package com.squareup.javapoet;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class ObjectEmitter {
    private final IdentityHashMap<Object, String> objectIdentifierMap;
    private final Set<Object> possibleCircularReferenceSet;
    private final List<TypeInliner> typeInliners;

    /**
     * This is the Set of all exact types we trust,
     * to prevent malicious classes with malicious setters
     * from being generated.
     */
    private final Set<Class<?>> trustedExactTypes;

    /**
     * This is the Set of all assignable types we trust,
     * which allows anything that can be assigned to them to
     * be generated. Be careful with these; there can be a
     * malicious subclass you do not know about.
     */
    private final Set<Class<?>> trustedAssignableTypes;

    private final Function<CodeWriter, ObjectEmitter> emitterFactory;
    private final NameAllocator nameAllocator;
    private final String namePrefix;
    private final CodeWriter codeWriter;

    ObjectEmitter(ObjectInliner objectInliner, CodeWriter codeWriter) {
        this.typeInliners = new ArrayList<>(objectInliner.getTypeInliners());
        this.trustedAssignableTypes = new HashSet<>(objectInliner.getTrustedAssignableTypes());
        this.trustedExactTypes = new HashSet<>(objectInliner.getTrustedExactTypes());
        this.namePrefix = objectInliner.getNamePrefix();
        this.objectIdentifierMap = new IdentityHashMap<>();
        this.possibleCircularReferenceSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.nameAllocator = new NameAllocator();
        this.codeWriter = codeWriter;
        this.emitterFactory = newCodeWriter -> new ObjectEmitter(this, newCodeWriter);
    }

    private ObjectEmitter(ObjectEmitter objectEmitter, CodeWriter codeWriter) {
        this.typeInliners = objectEmitter.typeInliners;
        this.trustedAssignableTypes = objectEmitter.trustedAssignableTypes;
        this.trustedExactTypes = objectEmitter.trustedExactTypes;
        this.namePrefix = objectEmitter.namePrefix;
        // Use a copy of the existing name map so sibling emitters cannot
        // see variables defined in this emitter (since they cannot access them)
        this.objectIdentifierMap = new IdentityHashMap<>(objectEmitter.objectIdentifierMap);
        this.nameAllocator = objectEmitter.nameAllocator.clone();

        // Use the same possibleCircularReferenceSet, so we can detect circular references
        // caused by nested TypeInliners
        this.possibleCircularReferenceSet = objectEmitter.possibleCircularReferenceSet;
        this.codeWriter = codeWriter;
        this.emitterFactory = newCodeWriter -> new ObjectEmitter(this, newCodeWriter);
    }

    public ObjectInliner.Inlined inlined(Object value) {
        return new ObjectInliner.Inlined(emitterFactory, value);
    }

    public void emit(String s) throws IOException {
        codeWriter.emit(s);
    }

    public void emit(String format, Object... args) throws IOException {
        codeWriter.emit(format, args);
    }

    public String newName(String suggestedSuffix) {
        return nameAllocator.newName(namePrefix + suggestedSuffix);
    }

    public String newName(String suggestedSuffix, Object tag) {
        return nameAllocator.newName(namePrefix + suggestedSuffix, tag);
    }

    /**
     * Reserves a {@link String} that can be used as an identifier
     * for an object.
     */
    private String reserveObjectIdentifier(Object object, Class<?> expressionType) {
        String reservedIdentifier = nameAllocator
                .newName(namePrefix + expressionType.getSimpleName());
        objectIdentifierMap.put(object, reservedIdentifier);
        return reservedIdentifier;
    }

    /**
     * Return a string that can be used in a {@link CodeWriter} to
     * access a complex object.
     *
     * @param object The object to be accessed
     * @return A string that can be used by a {@link CodeWriter} to
     * access the object.
     */
    private String getObjectIdentifier(Object object) {
        return objectIdentifierMap.get(object);
    }

    public String getName(Object tag) {
        return nameAllocator.get(tag);
    }

    void emit(Object object) throws IOException {
        if (object == null) {
            codeWriter.emit("null");
            return;
        }

        // Does a neat trick, so we can get a code block in a fragment
        // It defines an inline supplier and immediately calls it.
        codeWriter.emit("(($T)(($T)(()->{", getSerializedType(object.getClass()), Supplier.class);
        codeWriter.emit("\nreturn $L;\n})).get())", getInlinedObject(object));
    }

    /**
     * Serializes a Pojo to code that uses its no-args constructor
     * and setters to create the object.
     *
     * @param object The object to be serialized.
     * @return A string that can be used by a {@link CodeWriter} to access the object
     */
    private String getInlinedObject(Object object) throws IOException  {
        // Some inliners cannot inline circular references, so bail out if we detect an
        // unsupported circular reference
        if (possibleCircularReferenceSet.contains(object)) {
            throw new IllegalArgumentException(
                    "Cannot serialize an object of type (" + object.getClass().getCanonicalName()
                            + ") because it contains a circular reference.");
        }
        // If we already serialized the object, we should just return
        // its identifier
        if (objectIdentifierMap.containsKey(object)) {
            return getObjectIdentifier(object);
        }
        // First, check for primitives
        if (object == null) {
            return "null";
        }
        if (object instanceof Boolean) {
            return object.toString();
        }
        if (object instanceof Byte) {
            // Cast to byte
            return "((byte) " + object + ")";
        }
        if (object instanceof Character) {
            // A char is 16-bits, so its max value is 0xFFFF.
            // So if we get the hex string of (value | 0x10000),
            // we get a five-digit hex string 0x1abcd where 1
            // is the known first digit and abcd are the hex
            // digits for the character (with "a" being the most significant bit).
            // Any 16-bit Java character can be accessed using the expression
            // '\uABCD' where ABCD are the hex digits for the Java character.
            return "'\\u" + Integer.toHexString(((char) object) | 0x10000)
                    .substring(1) + "'";
        }
        if (object instanceof Short) {
            // Cast to short
            return "((short) " + object + ")";
        }
        if (object instanceof Integer) {
            return object.toString();
        }
        if (object instanceof Long) {
            // Add long suffix to number string
            return object + "L";
        }
        if (object instanceof Float) {
            // Add float suffix to number string
            return object + "f";
        }
        if (object instanceof Double) {
            // Add double suffix to number string
            return object + "d";
        }

        // Check for builtin classes
        if (object instanceof String) {
            return CodeBlock.builder().add("$S", object).build().toString();
        }
        if (object instanceof Class<?>) {
            Class<?> value = (Class<?>) object;
            if (!Modifier.isPublic(value.getModifiers())) {
                throw new IllegalArgumentException("Cannot serialize (" + value
                        + ") because it is not a public class.");
            }
            return value.getCanonicalName() + ".class";
        }
        if (object.getClass().isEnum()) {
            // Use field access to read the enum
            Class<?> enumClass = object.getClass();
            Enum<?> objectEnum = (Enum<?>) object;
            if (!Modifier.isPublic(enumClass.getModifiers())) {
                // Use name() since toString() can be malicious
                throw new IllegalArgumentException(
                        "Cannot serialize (" + objectEnum.name()
                                + ") because its type (" + enumClass
                                + ") is not a public class.");
            }

            return enumClass.getCanonicalName() + "." + objectEnum.name();
        }

        // We need to use a custom in-liner, which will potentially
        // call methods on a user-supplied instances. Make sure we trust
        // the type before continuing
        if (!isTrustedType(object.getClass())) {
            throw new IllegalArgumentException("Cannot serialize instance of ("
                    + object.getClass().getCanonicalName()
                    + ") because it is not an instance of a trusted type.");
        }
        // Check if any registered TypeInliner matches the class
        for (TypeInliner typeInliner : typeInliners) {
            if (typeInliner.canInline(object)) {
                Class<?> expressionType = typeInliner.getInlinedType(object);
                String identifier = reserveObjectIdentifier(object, expressionType);
                possibleCircularReferenceSet.add(object);
                String out = typeInliner.inline(this, object);
                possibleCircularReferenceSet.remove(object);
                emit("\n$T $N = $L;", expressionType,
                        identifier,
                        out);
                return identifier;
            }
        }

        return getInlinedComplexObject(object);
    }

    private boolean isTrustedType(Class<?> query) {
        if (query.isArray()) {
            return query.getComponentType().isPrimitive()
                    || isTrustedType(query.getComponentType());
        }
        for (Class<?> trustedAssignableType : trustedAssignableTypes) {
            if (trustedAssignableType.isAssignableFrom(query)) {
                return true;
            }
        }
        for (Class<?> trustedExactType : trustedExactTypes) {
            if (trustedExactType.equals(query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRecord(Object object) {
        Class<?> superClass = object.getClass().getSuperclass();
        return superClass != null && superClass.getName()
                .equals("java.lang.Record");
    }

    /**
     * Serializes collections and complex POJOs to code.
     */
    private String getInlinedComplexObject(Object object) throws IOException {
        if (isRecord(object)) {
            // Records must set all fields at initialization time,
            // so we delay the declaration of its variable
            return getInlinedRecord(object);
        }
        // Object is not serialized yet
        // Create a new variable to store its value when setting its fields
        String newIdentifier = reserveObjectIdentifier(object,
                getSerializedType(object.getClass()));

        // First, check if it is a collection type
        if (object.getClass().isArray()) {
            return getInlinedArray(newIdentifier, object);
        }
        if (object instanceof List) {
            return getInlinedList(newIdentifier, (List<?>) object);
        }
        if (object instanceof Set) {
            return getInlinedSet(newIdentifier, (Set<?>) object);
        }
        if (object instanceof Map) {
            return getInlinedMap(newIdentifier, (Map<?, ?>) object);
        }

        if (!Modifier.isPublic(object.getClass().getModifiers())) {
            throw new IllegalArgumentException("Cannot serialize type ("
                    + object.getClass().getCanonicalName()
                    + ") because it is not public.");
        }
        codeWriter.emit("\n$T $N;", object.getClass(), newIdentifier);
        try {
            Constructor<?> constructor = object.getClass().getConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new IllegalArgumentException("Cannot serialize type ("
                        + object.getClass().getCanonicalName()
                        + ") because its no-args constructor is not public.");
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot serialize type ("
                    + object.getClass().getCanonicalName()
                    + ") because it does not have a public no-args constructor.");
        }
        codeWriter.emit("\n$N = new $T();", newIdentifier, object.getClass());
        inlineFieldsOfPojo(object.getClass(), newIdentifier, object);
        return getObjectIdentifier(object);
    }

    private String getInlinedArray(String newIdentifier, Object array) throws IOException {
        Class<?> componentType = array.getClass().getComponentType();
        if (!Modifier.isPublic(componentType.getModifiers())) {
            throw new IllegalArgumentException(
                    "Cannot serialize array of type (" + componentType.getCanonicalName()
                            + ") because (" + componentType.getCanonicalName()
                            + ") is not public.");
        }
        codeWriter.emit("\n$T $N;", array.getClass(), newIdentifier);

        // Get the length of the array
        int length = Array.getLength(array);

        // Create a new array from the component type with the given length
        codeWriter.emit("\n$N = new $T[$L];", newIdentifier,
                componentType, Integer.toString(length));
        for (int i = 0; i < length; i++) {
            // Set the elements of the array
            codeWriter.emit("\n$N[$L] = $L;",
                    newIdentifier,
                    Integer.toString(i),
                    getInlinedObject(Array.get(array, i)));
        }
        return getObjectIdentifier(array);
    }

    private String getInlinedList(String newIdentifier, List<?> list) throws IOException  {
        codeWriter.emit("\n$T $N;", List.class, newIdentifier);

        // Create an ArrayList
        codeWriter.emit("\n$N = new $T($L);", newIdentifier, ArrayList.class,
                Integer.toString(list.size()));
        for (Object item : list) {
            // Add each item of the list to the ArrayList
            codeWriter.emit("\n$N.add($L);",
                    newIdentifier,
                    getInlinedObject(item));
        }
        return getObjectIdentifier(list);
    }

    private String getInlinedSet(String newIdentifier, Set<?> set) throws IOException  {
        codeWriter.emit("\n$T $N;", Set.class, newIdentifier);

        // Create a new HashSet
        codeWriter.emit("\n$N = new $T($L);", newIdentifier, LinkedHashSet.class,
                Integer.toString(set.size()));
        for (Object item : set) {
            // Add each item of the set to the HashSet
            codeWriter.emit("\n$N.add($L);",
                    newIdentifier,
                    getInlinedObject(item));
        }
        return getObjectIdentifier(set);
    }

    private String getInlinedMap(String newIdentifier,
            Map<?, ?> map) throws IOException  {
        codeWriter.emit("\n$T $N;", Map.class, newIdentifier);

        // Create a HashMap
        codeWriter.emit("\n$N = new $T($L);", newIdentifier, LinkedHashMap.class,
                Integer.toString(map.size()));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            // Put each entry of the map into the HashMap
            codeWriter.emit("\n$N.put($L, $L);",
                    newIdentifier,
                    getInlinedObject(entry.getKey()),
                    getInlinedObject(entry.getValue()));
        }
        return getObjectIdentifier(map);
    }

    // Workaround for Java 8
    private static final class RecordComponent {
        private final Class<?> type;
        private final String name;

        private RecordComponent(Class<?> type, String name) {
            this.type = type;
            this.name = name;
        }

        public Class<?> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Object getValue(Object record) {
            try {
                return record.getClass().getMethod(name).invoke(record);
            } catch (InvocationTargetException | IllegalAccessException
                    | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Workaround for Java 8
    private static RecordComponent[] getRecordComponents(Class<?> recordClass) {
        try {
            Object[] components = (Object[]) recordClass.
                    getMethod("getRecordComponents").invoke(recordClass);
            RecordComponent[] out = new RecordComponent[components.length];
            for (int i = 0; i < components.length; i++) {
                Object component = components[i];
                Class<?> componentClass = component.getClass();
                Class<?> type = (Class<?>) componentClass
                        .getMethod("getType").invoke(component);
                String name = (String) componentClass
                        .getMethod("getName").invoke(component);
                out[i] = new RecordComponent(type, name);
            }
            return out;
        } catch (InvocationTargetException | IllegalAccessException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private String getInlinedRecord(Object record)
            throws IOException  {
        possibleCircularReferenceSet.add(record);
        Class<?> recordClass = record.getClass();
        if (!Modifier.isPublic(recordClass.getModifiers())) {
            throw new IllegalArgumentException(
                    "Cannot serialize record type (" + recordClass.getCanonicalName()
                            + ") because it is not public.");
        }

        RecordComponent[] recordComponents = getRecordComponents(recordClass);
        String[] componentAccessors = new String[recordComponents.length];
        for (int i = 0; i < recordComponents.length; i++) {
            Object value;
            Class<?> serializedType = getSerializedType(recordComponents[i].getType());
            if (!recordComponents[i].getType().equals(serializedType)) {
                throw new IllegalArgumentException(
                        "Cannot serialize type (" + recordClass
                                + ") as its component (" + recordComponents[i].getName()
                                + ") uses an implementation of a collection ("
                                + recordComponents[i].getType()
                                + ") instead of the interface type ("
                                + serializedType + ").");
            }
            value = recordComponents[i].getValue(record);
            try {
                componentAccessors[i] = getInlinedObject(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Cannot serialize record type ("
                        + record.getClass().getCanonicalName() + ") because the type of its value ("
                        + value.getClass().getCanonicalName() + ") for its component ("
                        + recordComponents[i].getName() + ") is not serializable.", e);
            }
        }
        // All components serialized, so no circular references
        possibleCircularReferenceSet.remove(record);
        StringBuilder constructorArgs = new StringBuilder();
        for (String componentAccessor : componentAccessors) {
            constructorArgs.append(componentAccessor).append(", ");
        }
        if (componentAccessors.length != 0) {
            constructorArgs.delete(constructorArgs.length() - 2,
                    constructorArgs.length());
        }
        String newIdentifier = nameAllocator.newName(namePrefix
                + recordClass.getSimpleName());
        objectIdentifierMap.put(record, newIdentifier);
        codeWriter.emit("\n$T $N = new $T($L);", recordClass, newIdentifier,
                recordClass, constructorArgs.toString());
        return getObjectIdentifier(record);
    }

    static Class<?> getSerializedType(Class<?> query) {
        if (List.class.isAssignableFrom(query)) {
            return List.class;
        }
        if (Set.class.isAssignableFrom(query)) {
            return Set.class;
        }
        if (Map.class.isAssignableFrom(query)) {
            return Map.class;
        }
        return query;
    }

    private static Method getSetterMethod(Class<?> expectedArgumentType, Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        String fieldName = field.getName();

        String methodName = "set" + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
        try {
            return declaringClass.getMethod(methodName, expectedArgumentType);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Sets the fields of object declared in objectSuperClass and all its superclasses.
     *
     * @param objectSuperClass A class assignable to object containing some of its fields.
     * @param identifier The name of the variable storing the serialized object.
     * @param object The object being serialized.
     */
    private void inlineFieldsOfPojo(Class<?> objectSuperClass, String identifier,
            Object object) throws IOException  {
        if (objectSuperClass == Object.class) {
            // We are the top-level, no more fields to set
            return;
        }
        Field[] fields = objectSuperClass.getDeclaredFields();
        // Sort by name to guarantee a consistent ordering
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                // We do not want to write static fields
                continue;
            }
            if (Modifier.isPublic(field.getModifiers())) {
                try {
                    codeWriter.emit("\n$N.$N = $L;", identifier,
                            field.getName(),
                            getInlinedObject(field.get(object)));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            // Set the field accessible so we can read its value
            field.setAccessible(true);
            Class<?> serializedType = getSerializedType(field.getType());
            Method setterMethod = getSetterMethod(serializedType, field);
            // setterMethod guaranteed to be public
            if (setterMethod == null) {
                if (!field.getType().equals(serializedType)) {
                    throw new IllegalArgumentException(
                            "Cannot serialize type (" + objectSuperClass
                                    + ") as its field (" + field.getName()
                                    + ") uses an implementation of a collection ("
                                    + field.getType()
                                    + ") instead of the interface type ("
                                    + serializedType + ").");
                }
                throw new IllegalArgumentException(
                        "Cannot serialize type (" + objectSuperClass
                                + ") as it is missing a public setter method for field ("
                                + field.getName() + ") of type (" + field.getType() + ").");
            }
            Object value;
            try {
                value = field.get(object);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            try {
                // Convert the field value to code, and call the setter
                // corresponding to the field with the serialized field value.
                codeWriter.emit("\n$N.$N($L);", identifier,
                        setterMethod.getName(),
                        getInlinedObject(value));
            } catch (IllegalArgumentException e) {
                // We trust object, but not necessary value
                throw new IllegalArgumentException("Cannot serialize an instance of type ("
                        + object.getClass().getCanonicalName() + ") because the type of its value ("
                        + value.getClass().getCanonicalName()
                        + ") for its field (" + field.getName()
                        + ") is not serializable.", e);
            }
        }
        try {
            inlineFieldsOfPojo(objectSuperClass.getSuperclass(), identifier, object);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot serialize type ("
                    + objectSuperClass + ") because its superclass ("
                    + objectSuperClass.getSuperclass() + ") is not serializable.", e);
        }
    }
}
