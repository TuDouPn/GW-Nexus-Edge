package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第四轮 EventStreams 语义测试（评审项 1）。
 *
 * <p>验证 {@link EventStreams}（Reactor Sinks replay）：
 * <ul>
 *   <li>多订阅者广播：两个订阅者都收到全部事件；</li>
 *   <li>延迟重放：延迟订阅收到历史事件（有界缓存内）；</li>
 *   <li>事件恰好一次：每个订阅者恰好收到每个事件一次（无重复）；</li>
 *   <li>严格顺序：事件顺序与发布一致；</li>
 *   <li>取消订阅：取消一个订阅者不影响另一个；</li>
 *   <li>有界缓存：超过 {@link EventStreams#HISTORY_LIMIT} 时延迟订阅只收到最近 N 个。</li>
 * </ul>
 */
class EventStreamsSemanticsTest {

    private static AgentEventEnvelope event(int seq) {
        return new AgentEventEnvelope("task-e",
                AgentEventEnvelope.AgentEventType.PROGRESS,
                "agent-" + seq, "e" + seq, "evt-" + seq);
    }

    @Test
    @DisplayName("多订阅者广播 + 顺序一致 + 恰好一次")
    void multicastOrderingExactlyOnce() throws Exception {
        EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
        for (int i = 1; i <= 5; i++) {
            stream.emit(event(i));
        }
        stream.complete(); // 完成，使 replay 订阅者终止

        List<AgentEventEnvelope> s1 = collect(stream, "sub1");
        List<AgentEventEnvelope> s2 = collect(stream, "sub2");

        // 两个订阅者都收到全部事件（延迟重放 + 多订阅者）。
        assertEquals(5, s1.size(), "订阅者 1 应收到全部 5 个事件");
        assertEquals(5, s2.size(), "订阅者 2 应收到全部 5 个事件");
        // 顺序一致。
        for (int i = 0; i < 5; i++) {
            assertEquals("e" + (i + 1), s1.get(i).summary(), "订阅者 1 顺序应一致");
            assertEquals("e" + (i + 1), s2.get(i).summary(), "订阅者 2 顺序应一致");
        }
        // 恰好一次：无重复事件 id。
        assertEquals(5, s1.stream().map(AgentEventEnvelope::eventId).distinct().count(),
                "订阅者 1 应恰好收到每个事件一次（事件 id 无重复）");
        assertEquals(5, s2.stream().map(AgentEventEnvelope::eventId).distinct().count(),
                "订阅者 2 应恰好收到每个事件一次");
    }

    @Test
    @DisplayName("延迟订阅收到历史事件（有界缓存内）")
    void delayedSubscriptionReceivesHistory() throws Exception {
        EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
        for (int i = 1; i <= 3; i++) {
            stream.emit(event(i));
        }
        stream.complete();

        // 完成后再订阅：replay 应回放全部历史。
        List<AgentEventEnvelope> events = collect(stream, "late");
        assertEquals(3, events.size(), "延迟订阅应收到历史事件");
        assertEquals("e1", events.get(0).summary());
        assertEquals("e3", events.get(2).summary());
    }

    @Test
    @DisplayName("有界缓存：超过 HISTORY_LIMIT 时延迟订阅只收到最近 N 个")
    void boundedCacheDropsOldest() throws Exception {
        EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
        int total = EventStreams.HISTORY_LIMIT + 10;
        for (int i = 1; i <= total; i++) {
            stream.emit(event(i));
        }
        stream.complete();

        // 延迟订阅：应只收到最近 HISTORY_LIMIT 个（有界缓存丢弃最旧）。
        List<AgentEventEnvelope> events = collect(stream, "bounded");
        assertEquals(EventStreams.HISTORY_LIMIT, events.size(),
                "有界缓存应只保留最近 " + EventStreams.HISTORY_LIMIT + " 个事件");
        // 最新事件保留。
        assertEquals("e" + total, events.get(events.size() - 1).summary(),
                "最新事件应保留");
        // 最旧事件被丢弃。
        assertFalse(events.stream().anyMatch(e -> e.summary().equals("e1")),
                "超出缓存上限的最旧事件应被丢弃");
    }

    @Test
    @DisplayName("取消订阅不影响其他订阅者")
    void cancelDoesNotAffectOthers() throws Exception {
        EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
        stream.emit(event(1));

        // 订阅者 A 立即取消。
        AtomicReference<Flow.Subscription> subA = new AtomicReference<>();
        List<AgentEventEnvelope> a = new ArrayList<>();
        stream.asFlowPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subA.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentEventEnvelope item) {
                a.add(item);
                subA.get().cancel(); // 取消 A
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        stream.emit(event(2));
        stream.complete(); // 完成，使 B 终止

        // 订阅者 B 应不受 A 取消影响，收到全部（含取消后发布的事件）。
        List<AgentEventEnvelope> b = collect(stream, "subB");
        assertTrue(b.size() >= 2, "订阅者 B 不应受 A 取消影响");
    }

    @Test
    @DisplayName("P0-8/P1-3：request(2) 背压——主线程独立断言只收 2 条，再追加 request 收齐")
    void backpressureWithLimitedRequest() throws Exception {
        EventStreams<AgentEventEnvelope> stream = EventStreams.replayBounded();
        for (int i = 1; i <= 5; i++) {
            stream.emit(event(i));
        }
        stream.complete();

        // 订阅者只 request(2)，由主线程独立断言收到 2 条后再追加请求
        // （P1-3：不在 onNext 回调内立即追加请求）。
        List<AgentEventEnvelope> events = new ArrayList<>();
        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        CountDownLatch firstBatch = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(1);
        stream.asFlowPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionRef.set(subscription);
                subscription.request(2); // 有限背压：只请求 2 个
            }

            @Override
            public void onNext(AgentEventEnvelope item) {
                events.add(item);
                firstBatch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        // 主线程独立等待并断言：第一次 request(2) 后恰好 2 条。
        assertTrue(firstBatch.await(5, TimeUnit.SECONDS), "首次 request(2) 应收到 2 条");
        assertEquals(2, events.size(), "request(2) 后应恰好收到 2 条（背压生效）");
        assertEquals("e1", events.get(0).summary());
        assertEquals("e2", events.get(1).summary());

        // 主线程追加请求，验证剩余事件按需拉取。
        subscriptionRef.get().request(10);
        assertTrue(done.await(5, TimeUnit.SECONDS), "补请求后应完成");
        assertEquals(5, events.size(), "追加 request 后应收到全部 5 个事件");
        assertEquals("e3", events.get(2).summary());
        assertEquals("e5", events.get(4).summary());
    }

    private static List<AgentEventEnvelope> collect(EventStreams<AgentEventEnvelope> stream, String tag)
            throws Exception {
        List<AgentEventEnvelope> events = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        stream.asFlowPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentEventEnvelope item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "订阅 " + tag + " 应在限定时间内结束");
        if (error.get() != null) {
            throw new AssertionError("订阅 " + tag + " 异常: " + error.get(), error.get());
        }
        return events;
    }
}
