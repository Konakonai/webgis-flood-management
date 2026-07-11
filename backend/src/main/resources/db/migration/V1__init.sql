-- ============================================================
-- WebGIS 内涝监测与应急管理系统 - 数据库初始化脚本
-- 数据库: PostgreSQL 15 + PostGIS 3.x
-- 坐标系: WGS84 (EPSG:4326)
-- 日期: 2026-06-18
-- ============================================================

-- 启用 PostGIS 扩展
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- 1. 系统管理相关表
-- ============================================================

-- 1.1 系统用户表
CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,              -- BCrypt 加密
    real_name   VARCHAR(50),
    email       VARCHAR(100),
    phone       VARCHAR(20),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE, -- 是否启用
    created_at  TIMESTAMP    DEFAULT NOW(),
    updated_at  TIMESTAMP    DEFAULT NOW()
);
COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password IS 'BCrypt加密密码';
COMMENT ON COLUMN sys_user.enabled IS '账号启用状态';

-- 1.2 系统角色表
CREATE TABLE sys_role (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,       -- ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER
    role_name   VARCHAR(50)  NOT NULL,              -- 管理员 / 操作员 / 查看者
    description VARCHAR(200),
    created_at  TIMESTAMP    DEFAULT NOW()
);
COMMENT ON TABLE sys_role IS '系统角色表';

-- 1.3 用户角色关联表
CREATE TABLE sys_user_role (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES sys_role(id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户角色关联表';

-- 1.4 系统配置表
CREATE TABLE sys_config (
    id          BIGSERIAL PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,        -- 配置键
    config_value TEXT,                                -- 配置值（JSON格式）
    description VARCHAR(500),                         -- 配置说明
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE sys_config IS '系统参数配置表';

-- 1.5 操作日志表
CREATE TABLE sys_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(50),
    action      VARCHAR(100) NOT NULL,               -- 操作类型：LOGIN/QUERY/CREATE/UPDATE/DELETE
    module      VARCHAR(100),                         -- 操作模块：STATION/RESOURCE/WORK_ORDER/CONFIG/LAYER/USER
    description TEXT,                                 -- 操作描述
    request_uri VARCHAR(500),                         -- 请求URI
    request_method VARCHAR(10),                      -- GET/POST/PUT/DELETE
    ip_address  VARCHAR(50),
    execution_time BIGINT,                           -- 执行耗时(ms)
    status      VARCHAR(20) DEFAULT 'SUCCESS',       -- SUCCESS/FAIL
    error_msg   TEXT,                                -- 错误信息
    created_at  TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE sys_log IS '操作日志表';

-- 索引：按时间、用户、操作类型查询
CREATE INDEX idx_sys_log_created_at ON sys_log(created_at DESC);
CREATE INDEX idx_sys_log_username ON sys_log(username);
CREATE INDEX idx_sys_log_action ON sys_log(action);

-- ============================================================
-- 2. 业务数据表
-- ============================================================

-- 2.1 监测站点表（含空间字段）
CREATE TABLE monitor_station (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,               -- 站点名称
    station_type VARCHAR(30)  NOT NULL CHECK (station_type IN ('RAIN_GAUGE','WATER_GAUGE','FLOW_METER','PUMP_STATION')),
    lat         DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng         DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    geom        geometry(Point, 4326),               -- PostGIS 空间点
    address     VARCHAR(200),                         -- 地址描述
    area        VARCHAR(100),                         -- 所属区域
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','MAINTENANCE')),
    install_date DATE,                               -- 安装日期
    description TEXT,                                -- 备注
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE monitor_station IS '监测站点表';

-- 空间索引
CREATE INDEX idx_monitor_station_geom ON monitor_station USING GIST(geom);
CREATE INDEX idx_monitor_station_type ON monitor_station(station_type);
CREATE INDEX idx_monitor_station_status ON monitor_station(status);

-- 触发器：自动从 lat/lng 生成 geometry
CREATE OR REPLACE FUNCTION update_monitor_station_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := ST_SetSRID(ST_MakePoint(NEW.lng, NEW.lat), 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_monitor_station_geom
    BEFORE INSERT OR UPDATE OF lat, lng ON monitor_station
    FOR EACH ROW EXECUTE FUNCTION update_monitor_station_geom();

-- 2.2 应急资源表（含空间字段）
CREATE TABLE emergency_resource (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,           -- 资源名称
    resource_type   VARCHAR(30)  NOT NULL CHECK (resource_type IN ('PUMP_TRUCK','SANDBAG','TEAM','WAREHOUSE','SHELTER')),
    lat             DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng             DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    geom            geometry(Point, 4326),
    address         VARCHAR(200),
    area            VARCHAR(100),                    -- 所属区域
    quantity        INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    unit            VARCHAR(20),                     -- 单位
    contact_person  VARCHAR(50),                     -- 联系人
    contact_phone   VARCHAR(20),                     -- 联系电话
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','DISPATCHED','DEPLETED')),
    description     TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE emergency_resource IS '应急资源表';

CREATE INDEX idx_emergency_resource_geom ON emergency_resource USING GIST(geom);
CREATE INDEX idx_emergency_resource_type ON emergency_resource(resource_type);

CREATE OR REPLACE FUNCTION update_emergency_resource_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := ST_SetSRID(ST_MakePoint(NEW.lng, NEW.lat), 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_emergency_resource_geom
    BEFORE INSERT OR UPDATE OF lat, lng ON emergency_resource
    FOR EACH ROW EXECUTE FUNCTION update_emergency_resource_geom();

-- 2.3 工单统一表
CREATE TABLE work_order (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(30)  NOT NULL CHECK (type IN ('REPORT','WARNING','DISPATCH')),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    lat             DOUBLE PRECISION CHECK (lat BETWEEN -90 AND 90),
    lng             DOUBLE PRECISION CHECK (lng BETWEEN -180 AND 180),
    geom            geometry(Point, 4326),
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','COMPLETED','REJECTED')),
    priority        VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    reporter_name   VARCHAR(50),                     -- 上报人
    reporter_phone  VARCHAR(20),                     -- 上报人电话
    images          TEXT,                            -- 图片URL列表(JSON数组)
    handler_id      BIGINT,                          -- 处理人ID
    handler_name    VARCHAR(50),                     -- 处理人姓名
    processed_at    TIMESTAMP,                       -- 处理时间
    result          TEXT,                            -- 处理结果
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE work_order IS '工单统一表';

CREATE INDEX idx_work_order_geom ON work_order USING GIST(geom);
CREATE INDEX idx_work_order_type ON work_order(type);
CREATE INDEX idx_work_order_status ON work_order(status);
CREATE INDEX idx_work_order_created_at ON work_order(created_at DESC);

CREATE OR REPLACE FUNCTION update_work_order_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := CASE WHEN NEW.lng IS NULL OR NEW.lat IS NULL THEN NULL
                     ELSE ST_SetSRID(ST_MakePoint(NEW.lng, NEW.lat), 4326) END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_order_geom
    BEFORE INSERT OR UPDATE OF lat, lng ON work_order
    FOR EACH ROW EXECUTE FUNCTION update_work_order_geom();

-- 2.4 空间图层元数据表
CREATE TABLE geo_layer (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,           -- 图层名称
    layer_type      VARCHAR(30),                     -- POINT/LINE/POLYGON
    description     TEXT,
    coord_system    VARCHAR(50) DEFAULT 'EPSG:4326', -- 坐标系
    feature_count   INTEGER DEFAULT 0,               -- 要素数量
    file_name       VARCHAR(200),                    -- 原始文件名
    file_size       BIGINT,                          -- 文件大小(bytes)
    upload_by       VARCHAR(50),                     -- 上传人
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE geo_layer IS '空间图层元数据表';

-- 2.5 图层要素表（含空间字段）
CREATE TABLE geo_feature (
    id              BIGSERIAL PRIMARY KEY,
    layer_id        BIGINT NOT NULL REFERENCES geo_layer(id) ON DELETE CASCADE,
    name            VARCHAR(200),                    -- 要素名称
    geom            geometry(Geometry, 4326) NOT NULL,
    properties      JSONB DEFAULT '{}',              -- 要素属性(JSON格式)
    created_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE geo_feature IS '图层要素表';

CREATE INDEX idx_geo_feature_layer_id ON geo_feature(layer_id);
CREATE INDEX idx_geo_feature_geom ON geo_feature USING GIST(geom);

-- 2.6 监测时序数据表
CREATE TABLE monitor_data (
    id              BIGSERIAL PRIMARY KEY,
    station_id      BIGINT NOT NULL REFERENCES monitor_station(id) ON DELETE CASCADE,
    data_type       VARCHAR(20) NOT NULL CHECK (data_type IN ('WATER_LEVEL','RAINFALL','FLOW')),
    value           DOUBLE PRECISION NOT NULL,
    warning_level   INT NOT NULL DEFAULT 0 CHECK (warning_level BETWEEN 0 AND 2),
    unit            VARCHAR(20),                      -- 单位(m/mm/m^3/s)
    recorded_at     TIMESTAMP DEFAULT NOW(),
    created_at      TIMESTAMP DEFAULT NOW()
);
COMMENT ON TABLE monitor_data IS '监测时序数据表';

-- 索引：按站点+时间+类型高效查询时序数据
CREATE INDEX idx_monitor_data_station_time ON monitor_data(station_id, recorded_at DESC);
CREATE INDEX idx_monitor_data_type ON monitor_data(data_type);
CREATE INDEX idx_monitor_data_recorded_at ON monitor_data(recorded_at DESC);
CREATE INDEX idx_monitor_data_warning ON monitor_data(warning_level) WHERE warning_level > 0;

-- 2.7 预警记录表（状态机）
CREATE TABLE warning_record (
    id              BIGSERIAL PRIMARY KEY,
    station_id      BIGINT REFERENCES monitor_station(id) ON DELETE SET NULL,
    warning_level   INT NOT NULL CHECK (warning_level IN (1, 2)),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','PUBLISHED','REVOKED','REJECTED')),
    title           VARCHAR(200),
    content         TEXT,
    affected_area   TEXT,                            -- GeoJSON Polygon/MultiPolygon 文本
    affected_geom   geometry(Geometry, 4326),
    measures        TEXT,                            -- 建议措施
    created_by      VARCHAR(50),
    confirmed_by    VARCHAR(50),
    published_by    VARCHAR(50),
    revoked_by      VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    confirmed_at    TIMESTAMP,
    published_at    TIMESTAMP,
    revoked_at      TIMESTAMP
);
COMMENT ON TABLE warning_record IS '预警记录表';

CREATE INDEX idx_warning_record_status ON warning_record(status);
CREATE INDEX idx_warning_record_created_at ON warning_record(created_at DESC);
CREATE INDEX idx_warning_record_station ON warning_record(station_id);
CREATE INDEX idx_warning_record_affected_geom ON warning_record USING GIST(affected_geom);

-- ============================================================
-- 3. 初始化种子数据
-- ============================================================

-- 3.1 初始化角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ROLE_ADMIN',    '管理员',   '系统管理员，拥有全部权限'),
('ROLE_OPERATOR', '操作员',   '业务操作人员，可管理数据和工单'),
('ROLE_VIEWER',   '查看者',   '只读权限，仅可查看数据');

-- 3.2 初始化管理员账号（密码: admin123，BCrypt加密）
INSERT INTO sys_user (username, password, real_name, email) VALUES
('admin',    '$2y$10$hY1Qjre1IVJ9QKq/Hceu6udHz4utQgA.GNeyMN/oNUiP0m6pAOhkW', '系统管理员', 'admin@floodgis.com'),
('operator', '$2y$10$hY1Qjre1IVJ9QKq/Hceu6udHz4utQgA.GNeyMN/oNUiP0m6pAOhkW', '业务操作员', 'operator@floodgis.com'),
('viewer',   '$2y$10$hY1Qjre1IVJ9QKq/Hceu6udHz4utQgA.GNeyMN/oNUiP0m6pAOhkW', '只读用户',   'viewer@floodgis.com');

-- 3.3 分配角色
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), -- admin -> ADMIN
(2, 2), -- operator -> OPERATOR
(3, 3); -- viewer -> VIEWER

-- 3.4 初始化系统配置
INSERT INTO sys_config (config_key, config_value, description) VALUES
('water_level_warning', '{"level": 1, "value": 1.0, "unit": "m", "color": "#E6A23C"}',  '水位预警阈值（黄色）'),
('water_level_danger',  '{"level": 2, "value": 1.5, "unit": "m", "color": "#F56C6C"}',  '水位危险阈值（红色）'),
('rain_warning',        '{"level": 1, "value": 30, "unit": "mm/h", "color": "#E6A23C"}', '雨量预警阈值（黄色）'),
('rain_danger',         '{"level": 2, "value": 50, "unit": "mm/h", "color": "#F56C6C"}', '雨量危险阈值（红色）');

-- 3.5 插入模拟监测站点数据（徐州市范围）
INSERT INTO monitor_station (name, station_type, lat, lng, address, area, status, install_date, description)
VALUES
('鼓楼区雨量站',  'RAIN_GAUGE',  34.2815, 117.1855, '徐州市鼓楼区中心',     '鼓楼区', 'ACTIVE', '2025-01-01', '自动雨量监测站'),
('云龙区水位站',  'WATER_GAUGE', 34.2522, 117.2266, '徐州市云龙区河道',     '云龙区', 'ACTIVE', '2025-01-01', '河道水位自动监测'),
('铜山区雨量站',  'RAIN_GAUGE',  34.1805, 117.1686, '徐州市铜山区中心',     '铜山区', 'ACTIVE', '2025-02-01', '自动雨量监测站'),
('泉山区水位站',  'WATER_GAUGE', 34.2610, 117.1400, '徐州市泉山区云龙湖',   '泉山区', 'ACTIVE', '2025-02-01', '云龙湖水位监测'),
('贾汪区雨量站',  'RAIN_GAUGE',  34.4340, 117.4470, '徐州市贾汪区中心',     '贾汪区', 'ACTIVE', '2025-03-01', '自动雨量监测站'),
('开发区流量站',  'FLOW_METER',  34.2850, 117.2500, '徐州市经济开发区河道', '开发区', 'ACTIVE', '2025-03-01', '河道流量监测');

-- 3.6 插入模拟应急资源数据
INSERT INTO emergency_resource (name, resource_type, lat, lng, address, area, quantity, unit, contact_person, contact_phone)
VALUES
('鼓楼区救援队',    'TEAM',      34.2815, 117.1855, '鼓楼区应急救援中心',   '鼓楼区', 20,  '人',   '张队长', '13800001111'),
('云龙区泵车站',    'PUMP_TRUCK', 34.2522, 117.2266, '云龙区排水泵站',       '云龙区', 5,   '台',   '李站长', '13800002222'),
('沙袋仓库A',       'SANDBAG',   34.2610, 117.1400, '泉山区物资仓库',       '泉山区', 2000,'个',   '王仓管', '13800003333'),
('避险中心A',        'SHELTER',   34.2800, 117.1900, '鼓楼区市民避险中心',   '鼓楼区', 500, '人',   '赵主任', '13800004444'),
('铜山区救援队',    'TEAM',      34.1805, 117.1686, '铜山区应急救援中心',   '铜山区', 15,  '人',   '孙队长', '13800005555'),
('贾汪区沙袋仓库',  'SANDBAG',   34.4340, 117.4470, '贾汪区物资仓库',       '贾汪区', 1500,'个',   '周仓管', '13800006666'),
('开发区泵车站',    'PUMP_TRUCK', 34.2850, 117.2500, '经济开发区排水站',     '开发区', 3,   '台',   '吴站长', '13800007777'),
('云龙区避险中心',  'SHELTER',   34.2500, 117.2200, '云龙区市民避险中心',   '云龙区', 300, '人',   '陈主任', '13800008888');
