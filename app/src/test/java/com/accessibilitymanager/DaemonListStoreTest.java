package com.accessibilitymanager;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DaemonListStoreTest {

    @Test
    public void parseIds_ignoresEmptySegmentsAndPreservesOrder() {
        Set<String> result = DaemonListStore.parseIds("serviceA::serviceB:serviceA:");

        assertEquals(new LinkedHashSet<>(Arrays.asList("serviceA", "serviceB")), result);
    }

    @Test
    public void containsId_matchesExactIdOnly() {
        String raw = "pkg/.Service:pkg/.ServiceExtra:";

        assertTrue(DaemonListStore.containsId(raw, "pkg/.Service"));
        assertTrue(DaemonListStore.containsId(raw, "pkg/.ServiceExtra"));
        assertFalse(DaemonListStore.containsId(raw, "pkg/.Serv"));
    }

    @Test
    public void addId_returnsNormalizedStringWithoutDuplicates() {
        String result = DaemonListStore.addId("serviceA:serviceB:", "serviceA");

        assertEquals("serviceA:serviceB:", result);
    }

    @Test
    public void removeId_removesOnlyExactTarget() {
        String result = DaemonListStore.removeId("pkg/.Service:pkg/.ServiceExtra:", "pkg/.Service");

        assertEquals("pkg/.ServiceExtra:", result);
    }
}
