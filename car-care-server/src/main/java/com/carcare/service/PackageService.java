package com.carcare.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carcare.common.BusinessException;
import com.carcare.dto.PackageDTO;
import com.carcare.dto.PageQuery;
import com.carcare.entity.PackageItem;
import com.carcare.entity.ServiceItem;
import com.carcare.entity.ServicePackage;
import com.carcare.mapper.PackageItemMapper;
import com.carcare.mapper.ServiceItemMapper;
import com.carcare.mapper.ServicePackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 保养套餐服务：套餐=多个服务项的打包售卖。
 * 业务约束（与外卖平台菜品-套餐联动同构）：
 * 1) 套餐至少包含一个服务项目
 * 2) 停售中的项目不能加入套餐
 * 3) 空套餐不能启用
 * 4) 启用的套餐不能删除（先禁用）
 */
@Service
@RequiredArgsConstructor
public class PackageService {

    private final ServicePackageMapper packageMapper;
    private final PackageItemMapper packageItemMapper;
    private final ServiceItemMapper itemMapper;

    public Page<ServicePackage> page(PageQuery query) {
        return packageMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<ServicePackage>lambdaQuery()
                        .eq(query.getStoreId() != null, ServicePackage::getStoreId, query.getStoreId())
                        .like(StringUtils.hasText(query.getName()), ServicePackage::getName, query.getName())
                        .eq(query.getStatus() != null, ServicePackage::getStatus, query.getStatus())
                        .orderByAsc(ServicePackage::getId));
    }

    /** 套餐详情：主表 + 明细列表，一次返回给编辑弹窗回显 */
    public Map<String, Object> detail(Long id) {
        ServicePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }
        List<PackageItem> items = packageItemMapper.selectList(Wrappers.<PackageItem>lambdaQuery()
                .eq(PackageItem::getPackageId, id));
        return Map.of("package", pkg, "items", items);
    }

    /** 新增套餐：主表与明细在同一事务写入，明细保存项目名称/价格快照 */
    @Transactional
    public void save(PackageDTO dto) {
        if (CollectionUtils.isEmpty(dto.getItemIds())) {
            throw new BusinessException("套餐至少包含一个服务项目");
        }
        ServicePackage pkg = new ServicePackage();
        pkg.setStoreId(dto.getStoreId());
        pkg.setName(dto.getName());
        pkg.setPrice(dto.getPrice());
        pkg.setDescription(dto.getDescription());
        pkg.setStatus(1);
        packageMapper.insert(pkg);
        saveItems(pkg.getId(), dto.getItemIds());
    }

    /** 更新套餐：传入 itemIds 时整组重建明细（先删后插），未传则只改基本信息 */
    @Transactional
    public void update(PackageDTO dto) {
        ServicePackage pkg = packageMapper.selectById(dto.getId());
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }
        if (pkg.getStatus() == 1 && !CollectionUtils.isEmpty(dto.getItemIds())) {
            // 启用的套餐变更内容前项目需全部在售
            checkItemsOnSale(dto.getItemIds());
        }
        pkg.setStoreId(dto.getStoreId());
        pkg.setName(dto.getName());
        pkg.setPrice(dto.getPrice());
        pkg.setDescription(dto.getDescription());
        packageMapper.updateById(pkg);
        if (!CollectionUtils.isEmpty(dto.getItemIds())) {
            packageItemMapper.delete(Wrappers.<PackageItem>lambdaQuery()
                    .eq(PackageItem::getPackageId, dto.getId()));
            saveItems(dto.getId(), dto.getItemIds());
        }
    }

    /** 启用/禁用：启用前校验套餐非空，防止用户买到空套餐 */
    public void updateStatus(Long id, Integer status) {
        ServicePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }
        if (status == 1) {
            Long itemCount = packageItemMapper.selectCount(Wrappers.<PackageItem>lambdaQuery()
                    .eq(PackageItem::getPackageId, id));
            if (itemCount == 0) {
                throw new BusinessException("套餐内没有服务项目，无法启用");
            }
        }
        pkg.setStatus(status);
        packageMapper.updateById(pkg);
    }

    /** 删除：连带清理明细；启用中的套餐拒绝删除 */
    public void delete(Long id) {
        ServicePackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BusinessException("套餐不存在");
        }
        if (pkg.getStatus() == 1) {
            throw new BusinessException("启用的套餐不能删除");
        }
        packageItemMapper.delete(Wrappers.<PackageItem>lambdaQuery()
                .eq(PackageItem::getPackageId, id));
        packageMapper.deleteById(id);
    }

    /** 写入套餐明细，同时生成 JSON 价格快照（项目后续改价/删除不影响已建套餐展示） */
    private void saveItems(Long packageId, List<Long> itemIds) {
        checkItemsOnSale(itemIds);
        List<ServiceItem> items = itemMapper.selectByIds(itemIds);
        for (ServiceItem item : items) {
            PackageItem pi = new PackageItem();
            pi.setPackageId(packageId);
            pi.setItemId(item.getId());
            pi.setItemData(JSONUtil.toJsonStr(Map.of(
                    "name", item.getName(), "price", item.getPrice())));
            packageItemMapper.insert(pi);
        }
    }

    /** 校验所选项目均为在售状态，否则抛出带项目名的错误 */
    private void checkItemsOnSale(List<Long> itemIds) {
        List<ServiceItem> items = itemMapper.selectByIds(itemIds);
        for (ServiceItem item : items) {
            if (item.getStatus() == 0) {
                throw new BusinessException("项目「" + item.getName() + "」已停售，无法加入套餐");
            }
        }
    }
}
