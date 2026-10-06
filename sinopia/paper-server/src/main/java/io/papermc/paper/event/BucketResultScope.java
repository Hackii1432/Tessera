package io.papermc.paper.event;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Internal per-invocation result. Separate placements never publish to each other. */
@NullMarked
public final class BucketResultScope implements AutoCloseable {
    private static final ThreadLocal<BucketResultScope> CURRENT = new ThreadLocal<>();
    private final @Nullable BucketResultScope parent;
    private final boolean use;
    private @Nullable ItemStack result;
    private boolean closed;

    private BucketResultScope(boolean use) {
        this.parent = CURRENT.get();
        this.use = use;
        CURRENT.set(this);
    }

    public static BucketResultScope openUse() {
        return new BucketResultScope(true);
    }

    public static BucketResultScope openPlacement() {
        return new BucketResultScope(false);
    }

    public void setResult(@Nullable ItemStack result) {
        this.result = result;
    }

    public void completePlacement(boolean successful) {
        if (successful && this.parent != null && this.parent.use) {
            this.parent.result = this.result;
        }
    }

    public static @Nullable ItemStack takeUseResult() {
        BucketResultScope current = CURRENT.get();
        if (current == null || !current.use) return null;
        ItemStack result = current.result;
        current.result = null;
        return result;
    }

    @Override
    public void close() {
        if (this.closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("Bucket scopes must close in nesting order");
        this.closed = true;
        if (this.parent == null) CURRENT.remove();
        else CURRENT.set(this.parent);
    }
}
