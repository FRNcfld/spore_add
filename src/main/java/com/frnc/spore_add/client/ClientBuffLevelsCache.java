package com.frnc.spore_add.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端持有的 buff 等级缓存，由 {@code ClientboundBuffLevelsPacket} 写入、由显示代码读取。
 *
 * <p>为什么需要缓存：可燃与爆燃的等级都存在实体的持久数据里（为的是绕开 amplifier 的 127 字节上限），
 * 而持久数据是纯服务端的。所以服务端要把这两个数同步过来，这里只是接收端。
 *
 * <p>纯展示用：这里的数字只决定图标上画几，任何数值判定都在服务端做。所以即使缓存是空的或者过期的，
 * 也不会影响伤害结算。
 *
 * <p>索引用的是实体 id，但实际只会用到本地玩家那一条——buff 图标只画玩家自己的效果。
 * 退出世界时残留的少量条目由 GC 随类一起回收，量级可以忽略，不做额外清理。
 */
public final class ClientBuffLevelsCache {

    /** 一个实体身上的两个等级。 */
    private record Levels(int ignitable, int deflagration) {
    }

    private static final Map<Integer, Levels> BY_ENTITY = new HashMap<>();

    private ClientBuffLevelsCache() {
    }

    /** 两个都为 0 时清除该实体的缓存。 */
    public static void set(int entityId, int ignitable, int deflagration) {
        if (ignitable <= 0 && deflagration <= 0) {
            BY_ENTITY.remove(entityId);
        } else {
            BY_ENTITY.put(entityId, new Levels(Math.max(0, ignitable), Math.max(0, deflagration)));
        }
    }

    public static int ignitable(int entityId) {
        Levels levels = BY_ENTITY.get(entityId);
        return levels == null ? 0 : levels.ignitable();
    }

    public static int deflagration(int entityId) {
        Levels levels = BY_ENTITY.get(entityId);
        return levels == null ? 0 : levels.deflagration();
    }
}
