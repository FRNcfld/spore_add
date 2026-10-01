package com.frnc.spore_add.network;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.effect.BuffLevels;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本 mod 的网络通道。目前只有一个包：可燃与爆燃的等级同步。
 */
public final class ModNetwork {

    /** 协议版本。只有本 mod 的客户端和服务端互相通信，改包结构时把这个字符串一起改掉即可。 */
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SporeAdd.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    /** 注册所有包。在主类构造时调用一次，必须早于任何一次发包。 */
    public static void init() {
        int index = 0;
        CHANNEL.registerMessage(index++, ClientboundBuffLevelsPacket.class,
                ClientboundBuffLevelsPacket::encode,
                ClientboundBuffLevelsPacket::decode,
                ClientboundBuffLevelsPacket::handle);
    }

    /**
     * 把该实体身上可燃与爆燃的当前等级同步给玩家本人。
     *
     * <p>两个数一起发（不用传参，直接现读），因为调用点关心的时机总是"某一边刚变过"，
     * 而另一边的值顺手带上更省事、也免得两处各发一半。
     *
     * <p><b>为什么只发给玩家、而不是所有能看见它的客户端</b>：buff 图标只会为本地玩家渲染，
     * 别人的等级没有任何人会看到。而 {@code DeflagrationEffect#applyEffectTick} 对<b>每一个</b>带爆燃的
     * 生物每秒都会调用一次这里——如果按"追踪该实体的玩家"广播，一群着火的怪就会变成持续的网络流量，
     * 换来的却是没人看的数字。所以非玩家实体直接返回。
     */
    public static void syncLevels(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientboundBuffLevelsPacket(player.getId(),
                        BuffLevels.ignitable(player), BuffLevels.deflagration(player)));
    }
}
