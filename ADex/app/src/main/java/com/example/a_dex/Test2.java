package com.example.a_dex;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import rikka.shizuku.Shizuku;
public class Test2 {
    public static void test() {
        for (Method m : Shizuku.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                System.out.println(m);
            }
        }
    }
}
