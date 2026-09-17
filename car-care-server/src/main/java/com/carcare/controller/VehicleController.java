package com.carcare.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carcare.common.BaseContext;
import com.carcare.common.BusinessException;
import com.carcare.common.Result;
import com.carcare.dto.VehicleDTO;
import com.carcare.entity.Vehicle;
import com.carcare.mapper.VehicleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车主车辆档案：我的车辆列表 + 增删改。
 * 归属一律按登录 token 取 userId，防止越权操作他人车辆
 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "17-车辆档案", description = "我的车辆增删改查")
public class VehicleController {

    private final VehicleMapper vehicleMapper;

    @GetMapping("/my")
    @Operation(summary = "我的车辆列表")
    public Result<List<Vehicle>> my() {
        return Result.success(vehicleMapper.selectList(Wrappers.<Vehicle>lambdaQuery()
                .eq(Vehicle::getUserId, BaseContext.getUserId())
                .orderByDesc(Vehicle::getId)));
    }

    @PostMapping
    @Operation(summary = "新增车辆", description = "plateNumber 必填，其余选填")
    public Result<Vehicle> create(@RequestBody @Valid VehicleDTO dto) {
        Vehicle vehicle = new Vehicle();
        vehicle.setUserId(BaseContext.getUserId());
        apply(vehicle, dto);
        vehicleMapper.insert(vehicle);
        return Result.success(vehicle);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改车辆", description = "只能改自己的车辆")
    public Result<Vehicle> update(@PathVariable Long id, @RequestBody @Valid VehicleDTO dto) {
        Vehicle vehicle = mustMine(id);
        apply(vehicle, dto);
        vehicleMapper.updateById(vehicle);
        return Result.success(vehicle);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除车辆", description = "只能删自己的车辆")
    public Result<Void> delete(@PathVariable Long id) {
        vehicleMapper.deleteById(mustMine(id).getId());
        return Result.success();
    }

    /** 按 id 取车辆并校验归属，不属于当前用户直接抛业务异常 */
    private Vehicle mustMine(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null || !vehicle.getUserId().equals(BaseContext.getUserId())) {
            throw new BusinessException("车辆不存在或无权操作");
        }
        return vehicle;
    }

    /** 将 DTO 可填字段拷贝到实体（userId 由 token 决定，不入 DTO） */
    private void apply(Vehicle vehicle, VehicleDTO dto) {
        vehicle.setPlateNumber(dto.getPlateNumber());
        vehicle.setBrand(dto.getBrand());
        vehicle.setModel(dto.getModel());
        vehicle.setColor(dto.getColor());
        vehicle.setMileage(dto.getMileage());
        vehicle.setRegisterDate(dto.getRegisterDate());
        vehicle.setNextMaintainDate(dto.getNextMaintainDate());
    }
}
