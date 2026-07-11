package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.config.ApiException;
import com.floodgis.entity.EmergencyResource;
import com.floodgis.mapper.EmergencyResourceMapper;
import com.floodgis.service.EmergencyResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmergencyResourceServiceImpl
        extends ServiceImpl<EmergencyResourceMapper, EmergencyResource>
        implements EmergencyResourceService {

    @Override
    @Transactional
    public EmergencyResource createManaged(EmergencyResource resource) {
        if ("DISPATCHED".equals(resource.getStatus())) {
            throw ApiException.conflict("DISPATCHED 状态只能由工单派发产生");
        }
        baseMapper.insert(resource);
        return resource;
    }

    @Override
    @Transactional
    public EmergencyResource updateManaged(Long id, EmergencyResource changes) {
        EmergencyResource current = baseMapper.lockById(id);
        if (current == null) throw ApiException.notFound("资源不存在");
        if ("DISPATCHED".equals(current.getStatus())
                && !"DISPATCHED".equals(changes.getStatus())) {
            throw ApiException.conflict("已派发资源的状态只能由工单完成或拒绝后释放");
        }
        if (!"DISPATCHED".equals(current.getStatus())
                && "DISPATCHED".equals(changes.getStatus())) {
            throw ApiException.conflict("DISPATCHED 状态只能由工单派发产生");
        }
        changes.setId(id);
        baseMapper.updateById(changes);
        return changes;
    }

    @Override
    @Transactional
    public void deleteManaged(Long id) {
        EmergencyResource current = baseMapper.lockById(id);
        if (current == null) throw ApiException.notFound("资源不存在");
        if ("DISPATCHED".equals(current.getStatus())
                || baseMapper.countProcessingAssignments(id) > 0) {
            throw ApiException.conflict("资源正在执行工单，不能删除");
        }
        baseMapper.deleteById(id);
    }
}
