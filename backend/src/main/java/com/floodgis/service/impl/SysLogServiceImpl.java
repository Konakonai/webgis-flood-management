package com.floodgis.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.floodgis.entity.SysLog;
import com.floodgis.mapper.SysLogMapper;
import com.floodgis.service.SysLogService;
import org.springframework.stereotype.Service;

@Service
public class SysLogServiceImpl
        extends ServiceImpl<SysLogMapper, SysLog>
        implements SysLogService {
}
