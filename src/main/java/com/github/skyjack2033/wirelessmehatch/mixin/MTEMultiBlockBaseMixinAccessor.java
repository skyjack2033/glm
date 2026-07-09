package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;

/**
 * Accessor interface to expose the {@code mDualOutputHatches} field from {@link MTEMultiBlockBaseMixin} to other Mixin
 * classes (e.g. {@link MTESteamMultiBlockBaseMixin}).
 */
public interface MTEMultiBlockBaseMixinAccessor {

    List<?> wirelessmehatch$getDualOutputHatches();
}
