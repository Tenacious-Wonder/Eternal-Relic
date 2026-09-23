package org.eternalrelic.bodypart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * <h1>「打中玩家哪个部位」的对外出口</h1>
 *
 * <p>判定本身不做任何游戏效果，只负责回答「打中哪儿」。想知道结果的一方通过
 * {@link #register} 挂上自己的处理，命中发生时就会收到通知。这样「判定」与「打中之后干什么」
 * 是分开的：将来要加部位伤害、部位减益之类的玩法，只在这里追加订阅即可，不必改动判定逻辑。</p>
 *
 * <p><b>两处触发来源</b>：弹射物命中（{@link org.eternalrelic.mixin.ProjectileEntityMixin}）
 * 与近战受击（{@link MeleeBodyPartDetector}）。两者归到同一个出口，订阅方因此只需写一次——
 * 想区分「这一下是射来的还是砍来的」，对拿到的记录做 {@code instanceof} 判断即可。</p>
 *
 * <p><b>运行位置</b>：只在服务端触发（两处来源都已滤掉客户端）。服务端算出来的结果才是权威的，
 * 联机时也一致，不会出现「各人看到的部位不一样」。</p>
 *
 * <p>订阅顺序即触发顺序；订阅者抛出的异常会中断其余订阅者，所以订阅方应自行保证不抛异常。</p>
 *
 * @see BodyPartHit 每次命中交给订阅方的公共信息
 * @see ProjectileBodyPartHit 弹射物那一种的完整记录
 * @see MeleeBodyPartHit 近战那一种的完整记录
 * @see BodyPartResolver 弹射物的部位是怎么算出来的
 * @see MeleeBodyPartGeometry 近战的部位是怎么算出来的
 */
public final class BodyPartHits {

    /** 已挂上的处理，按挂载顺序依次通知。 */
    private static final List<Consumer<BodyPartHit>> LISTENERS = new ArrayList<>();

    private BodyPartHits() {
    }

    /**
     * 挂上一个处理，每次有东西打中玩家时都会收到通知。
     *
     * @param listener 处理逻辑
     */
    public static void register(Consumer<BodyPartHit> listener) {
        LISTENERS.add(listener);
    }

    /**
     * 通知所有处理。由两处判定各自调用，玩法代码不需要自己调。
     *
     * @param hit 本次命中
     */
    public static void fire(BodyPartHit hit) {
        for (Consumer<BodyPartHit> listener : LISTENERS) {
            listener.accept(hit);
        }
    }
}
