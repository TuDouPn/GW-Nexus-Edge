package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 带 replay 语义的事件源（P0-2 修复）。
 *
 * <p>解决 {@link java.util.concurrent.SubmissionPublisher} 不给迟到订阅者回放历史事件的
 * 问题：本实现缓冲执行期间的全部事件，订阅者（包括延迟订阅）都会收到：
 * <ol>
 *   <li>已缓冲的全部历史事件（按顺序）；</li>
 *   <li>之后实时产生的新事件。</li>
 * </ol>
 * 完成/异常后新订阅者收到缓冲事件并立即收到终止信号。
 *
 * <p>线程安全：内部同步，支持单生产者（执行流）多消费者（订阅者）。
 */
public final class EventSink<T> implements Flow.Publisher<T> {

    private final Object lock = new Object();
    private final Deque<T> buffer = new ArrayDeque<>();
    private final List<ReplaySubscription> subscriptions = new ArrayList<>();
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private volatile Throwable error;
    private volatile boolean completed;

    /** 创建带 replay 语义的事件源。 */
    public static <T> EventSink<T> replay() {
        return new EventSink<>();
    }

    /** 发布一个事件（写入缓冲并推送给所有订阅者）。 */
    public void emit(T item) {
        synchronized (lock) {
            if (terminated.get()) {
                throw new IllegalStateException("EventSink 已终止，不能再发布事件");
            }
            buffer.add(item);
            List<ReplaySubscription> toNotify = new ArrayList<>(subscriptions);
            for (ReplaySubscription sub : toNotify) {
                sub.drain();
            }
        }
    }

    /** 正常完成（通知所有订阅者，并标记缓冲可重放）。 */
    public void complete() {
        synchronized (lock) {
            if (terminated.getAndSet(true)) {
                return;
            }
            completed = true;
            List<ReplaySubscription> toNotify = new ArrayList<>(subscriptions);
            for (ReplaySubscription sub : toNotify) {
                sub.drain();
            }
        }
    }

    /** 异常终止。 */
    public void completeExceptionally(Throwable t) {
        synchronized (lock) {
            if (terminated.getAndSet(true)) {
                return;
            }
            error = t;
            List<ReplaySubscription> toNotify = new ArrayList<>(subscriptions);
            for (ReplaySubscription sub : toNotify) {
                sub.drain();
            }
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        ReplaySubscription sub = new ReplaySubscription(subscriber);
        synchronized (lock) {
            subscriptions.add(sub);
            // 订阅者加入时立即重放历史缓冲。
            sub.replayBuffer.addAll(buffer);
            sub.drain();
        }
    }

    /** 单个订阅者状态。 */
    private final class ReplaySubscription implements Flow.Subscription {
        final Flow.Subscriber<? super T> subscriber;
        final Deque<T> replayBuffer = new ArrayDeque<>();
        long requested = 0;
        boolean cancelled = false;
        boolean deliveredTerminal = false;

        ReplaySubscription(Flow.Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancel();
                subscriber.onError(new IllegalArgumentException("request(n) 要求 n > 0"));
                return;
            }
            synchronized (lock) {
                requested = saturatingAdd(requested, n);
            }
            drain();
        }

        @Override
        public void cancel() {
            synchronized (lock) {
                cancelled = true;
                subscriptions.remove(this);
            }
        }

        void drain() {
            while (true) {
                T item;
                synchronized (lock) {
                    if (cancelled) {
                        return;
                    }
                    if (requested == 0) {
                        return;
                    }
                    item = replayBuffer.poll();
                    if (item == null) {
                        // 缓冲为空但新事件已写入 buffer——从共享缓冲取。
                        item = buffer.poll();
                    }
                    if (item == null) {
                        // 缓冲为空：检查终止。
                        if (deliveredTerminal) {
                            return;
                        }
                        if (terminated.get()) {
                            deliveredTerminal = true;
                            if (error != null) {
                                subscriber.onError(error);
                            } else if (completed) {
                                subscriber.onComplete();
                            }
                        }
                        return;
                    }
                    requested--;
                }
                // 在锁外投递（避免回调死锁）。
                subscriber.onNext(item);
            }
        }
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
