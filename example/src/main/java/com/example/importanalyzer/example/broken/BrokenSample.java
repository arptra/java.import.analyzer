package com.example.importanalyzer.example.broken;

import org.junit.Assert; // wrong package
import java.util.List;
import java.util.Set; // unused
import demo.missing.UnknownType; // unresolved

public class BrokenSample {
    private List<String> names;

    public void greet() {
        Assertions.assertTrue(names.isEmpty()); // missing import
        Helper helper = new Helper(); // missing import
    }
}
