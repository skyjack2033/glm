package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.junit.Test;

import appeng.helpers.WireLessToolHelper.BindResult;

public class WirelessKitRowBinderTest {

    private static final String BINDER_CLASS = "com.github.skyjack2033.wirelessmehatch.me.WirelessKitRowBinder";

    @Test
    public void successAdvancesOnlySource() throws Exception {
        assertAttempts(
            BindResult.SUCCESS,
            Arrays.asList("source-1", "source-2"),
            Collections.singletonList("target-1"),
            Arrays.asList("source-1->target-1", "source-2->target-1"));
    }

    @Test
    public void invalidTargetAdvancesOnlyTarget() throws Exception {
        assertAttempts(
            BindResult.INVALID_TARGET,
            Collections.singletonList("source-1"),
            Arrays.asList("target-1", "target-2"),
            Arrays.asList("source-1->target-1", "source-1->target-2"));
    }

    @Test
    public void invalidSourceAndAlreadyBoundAdvanceOnlySource() throws Exception {
        assertAttempts(
            BindResult.INVALID_SOURCE,
            Arrays.asList("source-1", "source-2"),
            Collections.singletonList("target-1"),
            Arrays.asList("source-1->target-1", "source-2->target-1"));
        assertAttempts(
            BindResult.ALREADY_BIND,
            Arrays.asList("source-1", "source-2"),
            Collections.singletonList("target-1"),
            Arrays.asList("source-1->target-1", "source-2->target-1"));
    }

    @Test
    public void failedAdvancesBothRows() throws Exception {
        assertAttempts(
            BindResult.FAILED,
            Arrays.asList("source-1", "source-2"),
            Arrays.asList("target-1", "target-2"),
            Arrays.asList("source-1->target-1", "source-2->target-2"));
    }

    @Test
    public void targetCapacityIsRecheckedAfterEveryAttempt() throws Exception {
        List<String> attempts = new ArrayList<>();
        Map<String, Integer> capacity = new LinkedHashMap<>();
        capacity.put("hub-1", 2);
        capacity.put("hub-2", 1);

        Predicate<String> canAccept = target -> capacity.get(target) > 0;
        BiFunction<String, String, BindResult> bind = (source, target) -> {
            attempts.add(source + "->" + target);
            capacity.put(target, capacity.get(target) - 1);
            return BindResult.SUCCESS;
        };

        invokeBindRows(
            Arrays.asList("source-1", "source-2", "source-3"),
            Arrays.asList("hub-1", "hub-2"),
            canAccept,
            bind);

        assertEquals(Arrays.asList("source-1->hub-1", "source-2->hub-1", "source-3->hub-2"), attempts);
    }

    private static void assertAttempts(BindResult result, List<String> sources, List<String> targets,
        List<String> expected) throws Exception {
        List<String> attempts = new ArrayList<>();
        invokeBindRows(sources, targets, target -> true, (source, target) -> {
            attempts.add(source + "->" + target);
            return result;
        });
        assertEquals(expected, attempts);
    }

    private static void invokeBindRows(List<String> sources, List<String> targets, Predicate<String> canAccept,
        BiFunction<String, String, BindResult> bind) throws Exception {
        Class<?> binder = Class.forName(BINDER_CLASS);
        Method method = binder.getMethod("bindRows", List.class, List.class, Predicate.class, BiFunction.class);
        try {
            method.invoke(null, sources, targets, canAccept, bind);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }
}
