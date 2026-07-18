package com.github.skyjack2033.wirelessmehatch.me;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import appeng.helpers.WireLessToolHelper.BindResult;

public final class WirelessKitRowBinder {

    private WirelessKitRowBinder() {}

    public static <T> void bindRows(List<T> sources, List<T> targets, Predicate<T> canAccept,
        BiFunction<T, T, BindResult> bind) {
        bindRows(sources, targets, (source, target) -> canAccept.test(target), bind);
    }

    public static <T> void bindRows(List<T> sources, List<T> targets, BiPredicate<T, T> canAccept,
        BiFunction<T, T, BindResult> bind) {
        if (sources.isEmpty() || targets.isEmpty()) return;

        int sourceIndex = 0;
        int targetIndex = 0;
        while (sourceIndex < sources.size() && targetIndex < targets.size()) {
            T source = sources.get(sourceIndex);
            T target = targets.get(targetIndex);
            if (!canAccept.test(source, target)) {
                targetIndex++;
                continue;
            }

            BindResult result = bind.apply(source, target);
            switch (result) {
                case SUCCESS, ALREADY_BIND, INVALID_SOURCE -> sourceIndex++;
                case INVALID_TARGET -> targetIndex++;
                case FAILED -> {
                    sourceIndex++;
                    targetIndex++;
                }
            }
        }
    }
}
