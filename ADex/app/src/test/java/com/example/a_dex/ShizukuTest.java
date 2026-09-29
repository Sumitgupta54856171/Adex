package com.example.a_dex;
import org.junit.Test;

import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import rikka.shizuku.Shizuku;

public class ShizukuTest {
    @Test
    public void printClasses() throws Exception {
        URL url = Shizuku.class.getProtectionDomain().getCodeSource().getLocation();
        ZipInputStream zip = new ZipInputStream(url.openStream());
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.getName().contains("newProcess") || entry.getName().contains("Process")) {
                System.out.println("CLASS: " + entry.getName());
            }
        }
    }
}
