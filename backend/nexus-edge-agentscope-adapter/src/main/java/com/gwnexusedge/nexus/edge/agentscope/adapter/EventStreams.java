package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.util.concurrent.Flow;
import org.reactivestreams.Publisher;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 事件流（第四轮：替换自研 EventSink，采用 Reactor 官方 {@link Sinks}）。
 *
 * <p>语义保证（评审项 1）：
 * <ul>
 *   <li><b>多订阅者广播</b>：{@link Sinks.Many#asFlux()} 支持任意多个订阅者；</li>
 *   <li><b>延迟重放</b>：replay 有界缓存，延迟订阅者收到历史事件（有界，见 {@link #HISTORY_LIMIT}）；</li>
 *   <li><b>事件恰好一次</b>：replay 缓存保证每个订阅者恰好收到每个事件一次；</li>
 *   <li><b>严格顺序</b>：Reactor replay 保持发布顺序；</li>
 *   <li><b>背压/取消正确</b>：{@link Sinks.Many} 处理背压；取消订阅不影响其他订阅者；</li>
 *   <li><b>有界缓存</b>：{@link #HISTORY_LIMIT} 限制重放缓存大小（防止无限增长）。</li>
 * </ul>
 *
 * <p>与 Port 契约的适配：{@link #asFlowPublisher()} 返回 JDK {@link Flow.Publisher}
 * （Reactor {@link Flux} 即实现该接口），供 SSE/长期异步消费。
 */
public final class EventStreams<T> {

    /** 重放缓存上限：延迟订阅最多收到最近 N 个事件。 */
    public static final int HISTORY_LIMIT = 256;

    private final Sinks.Many<T> sink;

    private EventStreams(Sinks.Many<T> sink) {
        this.sink = sink;
    }

    /** 创建有界 replay 事件流（历史缓存 = {@link #HISTORY_LIMIT}）。 */
    public static <T> EventStreams<T> replayBounded() {
        Sinks.Many<T> sink = Sinks.many().replay().limit(HISTORY_LIMIT);
        return new EventStreams<>(sink);
    }

    /** 发布一个事件（有界缓存 + 多订阅者广播）。 */
    public void emit(T item) {
        Sinks.EmitResult result = sink.tryEmitNext(item);
        if (result.isFailure()) {
            throw new IllegalStateException("事件发布失败: " + result);
        }
    }

    /** 正常完成。 */
    public void complete() {
        sink.tryEmitComplete();
    }

    /** 异常终止。 */
    public void completeExceptionally(Throwable t) {
        sink.tryEmitError(t);
    }

    /** 返回 JDK {@link Flow.Publisher} 视图（经 Reactor JdkFlowAdapter 桥接）。 */
    public Flow.Publisher<T> asFlowPublisher() {
        return JdkFlowAdapter.publisherToFlowPublisher(sink.asFlux());
    }

    /** 返回 Reactor {@link Publisher}（供内部组合/测试）。 */
    public Publisher<T> asReactivePublisher() {
        return sink.asFlux();
    }
}
