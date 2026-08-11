package com.mosaicgem.plugin.model;

import java.util.List;

/**
 * 装备上的镶嵌数据：孔数 + 已镶嵌宝石列表。
 */
public record SocketData(int holes, List<SocketedGem> gems) {
}
