package com.example.sillyspringboot.integration.sillytavern;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式生成控制：用于停止生成与协作取消。
 * <p>
 * 业务层与 adapter 之间只传递“可取消”能力，禁止透出 ST 原始连接对象。
 */
public final class StStreamControl {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Runnable> onCancel = new AtomicReference<>(null);

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel.set(onCancel);
        if (cancelled.get() && onCancel != null) {
            onCancel.run();
        }
    }

    /**
     * 追加取消回调（不会覆盖已有回调）。
     * <p>
     * 用于同时取消 future + 关闭流等多个清理动作。
     */
    public void addOnCancel(Runnable more) {
        if (more == null) return;
        onCancel.getAndUpdate(prev -> {
            if (prev == null) return more;
            return () -> {
                try {
                    prev.run();
                } finally {
                    more.run();
                }
            };
        });
        if (cancelled.get()) {
            more.run();
        }
    }

    public void cancel() {
        cancelled.set(true);
        Runnable r = onCancel.get();
        if (r != null) {
            r.run();
        }
    }
}

