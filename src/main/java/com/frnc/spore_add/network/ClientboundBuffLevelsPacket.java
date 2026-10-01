package com.frnc.spore_add.network;

import java.util.function.Supplier;

import com.frnc.spore_add.client.ClientBuffLevelsCache;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 → 客户端：某个玩家身上可燃与爆燃的等级。
 *
 * <p>两个数都要自己发：它们都存在实体的持久数据里（见 {@code BuffLevels}），而持久数据是纯服务端的；
 * vanilla 那条 buff 同步通道只带 amplifier，而 amplifier 有 127 的字节上限，正是我们绕开它的原因。
 * 两个数打在同一个包里，因为它们总是一起变、也总是一起用。
 *
 * <p>只用于显示：客户端把它塞进 {@link ClientBuffLevelsCache}，一切数值判定仍以服务端为准。
 */
public final class ClientboundBuffLevelsPacket {

    private final int entityId;
    private final int ignitable;
    private final int deflagration;

    public ClientboundBuffLevelsPacket(int entityId, int ignitable, int deflagration) {
        this.entityId = entityId;
        this.ignitable = ignitable;
        this.deflagration = deflagration;
    }

    public static void encode(ClientboundBuffLevelsPacket message, FriendlyByteBuf buffer) {
        // 用 VarInt：等级不封顶，可能是很大的正数，却往往不大，两种情况下都省字节
        buffer.writeVarInt(message.entityId);
        buffer.writeVarInt(message.ignitable);
        buffer.writeVarInt(message.deflagration);
    }

    public static ClientboundBuffLevelsPacket decode(FriendlyByteBuf buffer) {
        return new ClientboundBuffLevelsPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    /**
     * 处理包。本包只在客户端收发（通道方向是 PLAY_TO_CLIENT），所以这里引用客户端类是安全的：
     * 那个 lambda 在服务端永远不会被执行，{@code ClientBuffLevelsCache} 也就不会被加载。
     */
    public static void handle(ClientboundBuffLevelsPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientBuffLevelsCache.set(message.entityId, message.ignitable, message.deflagration));
        context.setPacketHandled(true);
    }
}
