package com.floodgis.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.floodgis.entity.EmergencyResource;

public interface EmergencyResourceService extends IService<EmergencyResource> {
    EmergencyResource createManaged(EmergencyResource resource);
    EmergencyResource updateManaged(Long id, EmergencyResource changes);
    void deleteManaged(Long id);
}
