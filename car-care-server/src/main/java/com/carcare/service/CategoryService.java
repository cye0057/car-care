package com.carcare.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.BusinessException;
import com.carcare.entity.ServiceCategory;
import com.carcare.entity.ServiceItem;
import com.carcare.mapper.ServiceCategoryMapper;
import com.carcare.mapper.ServiceItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务分类服务：分类是服务项的归属维度，删除前需保证分类下无服务项
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ServiceCategoryMapper categoryMapper;
    private final ServiceItemMapper itemMapper;

    /** 全量分类列表（按 sort 升序），数据量小不做分页 */
    public List<ServiceCategory> list() {
        return categoryMapper.selectList(Wrappers.<ServiceCategory>lambdaQuery()
                .orderByAsc(ServiceCategory::getSort));
    }

    /** 新增分类：名称唯一性先查库校验，给出友好错误而非依赖数据库唯一索引异常 */
    public void save(ServiceCategory category) {
        Long dup = categoryMapper.selectCount(Wrappers.<ServiceCategory>lambdaQuery()
                .eq(ServiceCategory::getName, category.getName()));
        if (dup > 0) {
            throw new BusinessException("分类名称已存在");
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        categoryMapper.insert(category);
    }

    public void update(ServiceCategory category) {
        categoryMapper.updateById(category);
    }

    /** 删除分类：存在关联服务项时拒绝，防止服务项成为孤儿数据 */
    public void delete(Long id) {
        Long used = itemMapper.selectCount(Wrappers.<ServiceItem>lambdaQuery()
                .eq(ServiceItem::getCategoryId, id));
        if (used > 0) {
            throw new BusinessException("该分类下仍有服务项，无法删除");
        }
        categoryMapper.deleteById(id);
    }
}
