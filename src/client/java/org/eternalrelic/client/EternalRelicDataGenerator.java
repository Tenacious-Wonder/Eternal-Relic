package org.eternalrelic.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * 数据生成入口。
 *
 * <p>游戏的「数据生成」功能可以在不启动游戏世界的情况下，批量产出配方、战利品表、模型等
 * JSON 文件。本模组目前没有需要自动生成的内容，因此这里只创建一个空的产出包，
 * 等以后加入配方或战利品表时再往其中添加生成器。</p>
 */
public class EternalRelicDataGenerator implements DataGeneratorEntrypoint {

    /**
     * 由数据生成任务调用，用于登记本模组要自动生成哪些文件。
     *
     * @param fabricDataGenerator 本次数据生成的入口，产出包由它创建
     */
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
    }
}
