package com.mosaicgem.plugin.model;

import java.util.List;
import java.util.Map;

/**
 * 装备上的镶嵌数据：总孔数 + 各来源打孔器的孔数 + 已镶嵌宝石列表。
 *
 * @param holes       总孔数（所有来源之和，不得超过全局上限）
 * @param holeSources 每个打孔器来源贡献的孔数（key = 打孔器内部名）
 * @param gems        已镶嵌宝石列表
 */
public record SocketData(int holes, Map<String, Integer> holeSources, List<SocketedGem> gems) {
}
