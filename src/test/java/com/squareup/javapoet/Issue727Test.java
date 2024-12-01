package com.squareup.javapoet;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Issue727Test {
    @Test
    public void positiveTest(){
        List<ParameterizedTypeName> l = new ArrayList<ParameterizedTypeName>();
        ParameterizedTypeName a = ParameterizedTypeName.get(List.class, String.class);
        ParameterizedTypeName b = ParameterizedTypeName.get(List.class, Integer.class);
        l.add(a);
        l.add(b);
        Iterator<? extends TypeName> i = l.iterator();
        TypeName t = ParameterizedTypeName.get(i);
        Assert.assertEquals(a, t);
        t = ParameterizedTypeName.get(i);
        Assert.assertEquals(b, t);
    }

    @Test
    public void negativeTest(){
        List<ParameterizedTypeName> l = new ArrayList<ParameterizedTypeName>();
        ParameterizedTypeName a = ParameterizedTypeName.get(List.class, String.class);
        ParameterizedTypeName b = ParameterizedTypeName.get(List.class, Integer.class);
        l.add(a);
        l.add(b);
        Iterator<? extends TypeName> i = l.iterator();
        TypeName t = ParameterizedTypeName.get(i);
        Assert.assertNotEquals(b, t);
        t = ParameterizedTypeName.get(i);
        Assert.assertNotEquals(a, t);
    }

}





