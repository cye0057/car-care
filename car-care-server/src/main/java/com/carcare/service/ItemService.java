package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.dto.PageQuery;
import com.carcare.entity.PackageItem;
import com.carcare.entity.ServiceItem;
import com.carcare.mapper.PackageItemMapper;
import com.carcare.mapper.ServiceItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 保养/维修服务项服务：门店的最小可售卖单元。
 * 核心约束：被套餐引用的项目不允许停售（保证套餐可下单）
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ServiceItemMapper itemMapper;
    private final PackageItemMapper packageItemMapper;

    /** 分页查询：支持 门店/分类/名称/状态 四个过滤维度 */
    public Page<ServiceItem> page(PageQuery query) {
        return itemMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<ServiceItem>lambdaQuery()
                        .eq(query.getStoreId() != null, ServiceItem::getStoreId, query.getStoreId())
                        .eq(query.getCategoryId() != null, ServiceItem::getCategoryId, query.getCategoryId())
                        .like(StringUtils.hasText(query.getName()), ServiceItem::getName, query.getName())
                        .eq(query.getStatus() != null, ServiceItem::getStatus, query.getStatus())
                        .orderByAsc(ServiceItem::getId));
    }

    /** 指定门店在售项目列表：阶段5 车主端下单选项目的数据源 */
    public List<ServiceItem> listByStore(Long storeId) {
        return itemMapper.selectList(Wrappers.<ServiceItem>lambdaQuery()
                .eq(ServiceItem::getStoreId, storeId)
                .eq(ServiceItem::getStatus, 1));
    }

    public void save(ServiceItem item) {
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        itemMapper.insert(item);
    }

    public void update(ServiceItem item) {
        itemMapper.updateById(item);
    }

    /**
     * 起售/停售切换。停售时校验：被套餐引用的项目不允许停售，
     * 需先在套餐中移除，避免用户买到含停售项目的套餐
     */
    public void updateStatus(Long id, Integer status) {
        ServiceItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("服务项不存在");
        }
        if (status == 0) {
            Long referenced = packageItemMapper.selectCount(Wrappers.<PackageItem>lambdaQuery()
                    .eq(PackageItem::getItemId, id));
            if (referenced > 0) {
                throw new BusinessException("该项目已被套餐引用，请先从套餐中移除");
            }
        }
        item.setStatus(status);
        itemMapper.updateById(item);
    }

    /** 删除：仅允许删除停售项目（在售项目可能被订单引用，删除会破坏数据完整性） */
    public void delete(Long id) {
        ServiceItem item = itemMapper.selectById(id);
        if (item != null && item.getStatus() == 1) {
            throw new BusinessException("起售中的项目不能删除");
        }
        itemMapper.deleteById(id);
    }
}
