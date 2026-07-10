-- Complete spatial search, public reporting, dispatch history, and audit-friendly timestamps.

ALTER TABLE work_order
    ADD COLUMN tracking_code VARCHAR(32) UNIQUE,
    ADD COLUMN water_depth_cm NUMERIC(6, 2) CHECK (water_depth_cm BETWEEN 0 AND 1000),
    ADD COLUMN assigned_resource_id BIGINT REFERENCES emergency_resource(id) ON DELETE SET NULL,
    ADD COLUMN dispatched_at TIMESTAMP,
    ADD COLUMN arrived_at TIMESTAMP,
    ADD COLUMN completed_at TIMESTAMP,
    ADD COLUMN route_distance DOUBLE PRECISION CHECK (route_distance IS NULL OR route_distance >= 0),
    ADD COLUMN route_duration DOUBLE PRECISION CHECK (route_duration IS NULL OR route_duration >= 0);

CREATE INDEX idx_work_order_assigned_resource ON work_order(assigned_resource_id);
CREATE INDEX idx_work_order_status_created ON work_order(status, created_at DESC);
CREATE INDEX idx_work_order_unresolved_reports ON work_order(created_at DESC)
    WHERE type = 'REPORT' AND status IN ('PENDING', 'PROCESSING');

CREATE TABLE work_order_status_history (
    id              BIGSERIAL PRIMARY KEY,
    work_order_id   BIGINT NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    operator_name   VARCHAR(50) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_work_order_history_order_time
    ON work_order_status_history(work_order_id, created_at DESC);

CREATE TABLE work_order_attachment (
    id              BIGSERIAL PRIMARY KEY,
    work_order_id   BIGINT NOT NULL REFERENCES work_order(id) ON DELETE CASCADE,
    original_name   VARCHAR(255) NOT NULL,
    stored_name     VARCHAR(255) NOT NULL UNIQUE,
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT NOT NULL CHECK (file_size > 0),
    relative_path   VARCHAR(500) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_work_order_attachment_order ON work_order_attachment(work_order_id);

CREATE TABLE spatial_facility (
    id              VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    facility_type   VARCHAR(20) NOT NULL CHECK (facility_type IN ('waterlogging', 'pump')),
    type_name       VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    status_name     VARCHAR(50) NOT NULL,
    manager         VARCHAR(100),
    phone           VARCHAR(30),
    address         VARCHAR(300),
    water_depth     VARCHAR(30),
    capacity        VARCHAR(50),
    geom            geometry(Point, 4326) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_spatial_facility_geom ON spatial_facility USING GIST(geom);
CREATE INDEX idx_spatial_facility_type_name ON spatial_facility(facility_type, name);

INSERT INTO spatial_facility
    (id, name, facility_type, type_name, status, status_name, manager, phone, address, water_depth, capacity, geom)
VALUES
('F001', '彭城广场地下通道积水点', 'waterlogging', '道路积水点', 'critical', '积水严重', '鼓楼区排水处', '13888889901', '徐州市鼓楼区中山北路与淮海路交叉口地下通道', '0.25m', NULL, ST_SetSRID(ST_MakePoint(117.186, 34.263), 4326)),
('F002', '云龙湖东路防汛雨水泵站', 'pump', '雨水泵站', 'running', '正在运行', '泉山区水务局', '13888889902', '徐州市泉山区金山路云龙湖东岸', NULL, '5000 m³/h', ST_SetSRID(ST_MakePoint(117.142, 34.235), 4326)),
('F003', '徐州东站落客平台下积水点', 'waterlogging', '道路积水点', 'warning', '轻度积水', '开发区住建局', '13888889903', '徐州市鼓楼区徐州东站东广场地下匝道', '0.12m', NULL, ST_SetSRID(ST_MakePoint(117.291, 34.262), 4326)),
('F004', '矿大南湖校区东门雨水排涝站', 'pump', '雨水泵站', 'running', '正在运行', '铜山区水务局', '13888889904', '徐州市铜山区大学路中国矿大南湖校区东门', NULL, '3200 m³/h', ST_SetSRID(ST_MakePoint(117.146, 34.169), 4326)),
('F005', '淮海西路立交桥下积水点', 'waterlogging', '道路积水点', 'critical', '严重积水', '泉山区住建局', '13888889905', '徐州市泉山区淮海西路立交桥下', '0.45m', NULL, ST_SetSRID(ST_MakePoint(117.158, 34.268), 4326)),
('F006', '奎河解放路排水排涝泵站', 'pump', '雨水泵站', 'standby', '设备待机', '云龙区水务局', '13888889906', '徐州市云龙区解放南路奎河大桥旁', NULL, '8000 m³/h', ST_SetSRID(ST_MakePoint(117.182, 34.248), 4326)),
('F007', '金山桥高铁桥下积水点', 'waterlogging', '道路积水点', 'normal', '无明显积水', '开发区排水处', '13888889907', '徐州市金山桥开发区高铁高架桥下方路段', '0.05m', NULL, ST_SetSRID(ST_MakePoint(117.258, 34.298), 4326)),
('F008', '和平路立交桥雨水收集泵站', 'pump', '雨水泵站', 'running', '正在运行', '云龙区排水处', '13888889908', '徐州市云龙区和平大道立交桥下', NULL, '4500 m³/h', ST_SetSRID(ST_MakePoint(117.228, 34.258), 4326));

CREATE OR REPLACE FUNCTION update_warning_affected_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.affected_geom := CASE
        WHEN NEW.affected_area IS NULL OR BTRIM(NEW.affected_area) = '' THEN NULL
        ELSE ST_SetSRID(ST_GeomFromGeoJSON(NEW.affected_area), 4326)
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_warning_affected_geom
    BEFORE INSERT OR UPDATE OF affected_area ON warning_record
    FOR EACH ROW EXECUTE FUNCTION update_warning_affected_geom();

CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sys_user_updated_at BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_sys_config_updated_at BEFORE UPDATE ON sys_config
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_monitor_station_updated_at BEFORE UPDATE ON monitor_station
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_emergency_resource_updated_at BEFORE UPDATE ON emergency_resource
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_work_order_updated_at BEFORE UPDATE ON work_order
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_geo_layer_updated_at BEFORE UPDATE ON geo_layer
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER trg_spatial_facility_updated_at BEFORE UPDATE ON spatial_facility
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

INSERT INTO work_order
    (type, title, description, lat, lng, status, priority, reporter_name, reporter_phone, tracking_code, water_depth_cm)
VALUES
    ('REPORT', '大同街交叉口积水上报', '桥洞下积水严重，车辆无法通行。', 34.271, 117.192, 'PENDING', 'URGENT', '张先生', '13588889999', 'DEMO-A001', 45),
    ('REPORT', '云龙湖东路积水上报', '路面轻微积水，排水井盖反水。', 34.225, 117.155, 'PROCESSING', 'LOW', '刘女士', '13766667777', 'DEMO-A002', 12);

INSERT INTO work_order_status_history (work_order_id, from_status, to_status, operator_name, note)
SELECT id, NULL, status, 'SYSTEM', '演示数据初始化'
FROM work_order WHERE tracking_code IN ('DEMO-A001', 'DEMO-A002');
