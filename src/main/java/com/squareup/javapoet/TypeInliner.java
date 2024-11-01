package com.squareup.javapoet;

import java.io.IOException;

/**
 * An interface that allows for additional classes to be inlined
 * by the $V formatter.
 */
public interface TypeInliner {
    /**
     * Returns true if this {@link TypeInliner} can inline an {@link Object} of the
     * given type.
     *
     * @param object The object to be inlined
     * @return True if the {@link TypeInliner} can inline the given object,
     * false otherwise.
     */
    boolean canInline(Object object);

    /**
     * Returns the type of the expression returned by {@link #inline(ObjectEmitter, Object)}.
     * Defaults to the type of the passed instance.
     * @param instance The object to be inlined.
     * @return The type of the expression returned by {@link #inline(ObjectEmitter, Object)}.
     */
    default Class<?> getInlinedType(Object instance) {
        return instance.getClass();
    }

    /**
     * Inlines an {@link Object} that
     * {@link #canInline(Object)} accepted.
     * The argument for `$V` must be
     * obtained by using the {@link ObjectEmitter#inlined(Object)}
     * of the passed {@link ObjectEmitter}.
     * Returns a {@link String} that represents a valid Java expression,
     * that when evaluated, results in an {@link Object} equal to the
     * passed {@link Object}.
     *
     * @param emitter An emitter that can be used to generate code.
     * You can emit any valid Java statements.
     * When {@link #inline(ObjectEmitter, Object)}
     * returns, it is expected the emitted code is a set of complete Java statements.
     *
     * @param instance The object to inline.
     * @return A Java expression that evaluates to the inlined {@link Object}.
     */
    String inline(ObjectEmitter emitter, Object instance) throws IOException;
}
