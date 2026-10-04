package org.eternalrelic.client.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import org.eternalrelic.capability.using.CuratorLensEffect;

/**
 * 举着馆藏透镜时，转动视角的速度与平时一模一样。
 *
 * <p><b>原版举镜为什么会变慢</b>：游戏在算鼠标转向时，对「正在用望远镜」的情况另走一条路——
 * 见 {@code Mouse#updateMouse}，望远镜那一支把位移乘的是 {@code g}，而平时乘的是 {@code g}
 * 的八倍。这是替十倍放大的望远镜准备的：画面拉近十倍之后，原本的灵敏度会让人一动手就甩过头，
 * 于是游戏主动把手感压慢八倍，好让人瞄得准。</p>
 *
 * <p><b>馆藏透镜为什么不要这份照顾</b>：它只放大两倍，画面并没有拉到需要「瞄准」的程度，
 * 压慢八倍反而让日常转个身都费劲。这里把那一支对馆藏透镜关掉，让它走平时那条路，
 * 于是开镜与不开镜的手感完全一致。</p>
 *
 * <p><b>为什么恰好要在这里动手</b>：游戏判断「是不是在用望远镜」用的正是
 * {@code PlayerEntity#isUsingSpyglass}——也就是我们为了让遮罩、举镜姿势跟过来而特意让它
 * 对馆藏透镜返回 {@code true} 的那一处。既然借用了那个开关，就得在这里把不想要的那份副作用
 * 单独摘掉。</p>
 *
 * <p>原版望远镜不受影响：本注入只对馆藏透镜返回 {@code false}，其余情况原样交还给游戏。</p>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    /**
     * 把「正在用望远镜」这一判据对馆藏透镜否掉，让它走平时那条转向公式。
     *
     * <p>注入的是 {@code Mouse#updateMouse} 里对 {@code ClientPlayerEntity#isUsingSpyglass}
     * 的那一次调用。<b>这里必须写子类 {@code ClientPlayerEntity} 而不是方法真正所在的
     * {@code PlayerEntity}</b>——编译后的字节码按接收者的静态类型记录调用方，
     * 写成父类会匹配不上，游戏一启动就会因为找不到注入点而崩。</p>
     *
     * @param player 正在被询问的玩家
     * @return 游戏是否应当按「举着望远镜」那套较慢的手感来处理
     */
    @Redirect(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingSpyglass()Z"))
    private boolean eternal_relic$keepNormalTurnSpeed(ClientPlayerEntity player) {
        return player.isUsingSpyglass() && !CuratorLensEffect.isLookingThroughLens(player);
    }
}
