-- 最终交付数据：保留具备真实业务语义的模拟记录，移除演示和浏览器验收痕迹。
UPDATE work_order
SET tracking_code = 'FR-XZ-20260710-001'
WHERE tracking_code = 'DEMO-A001';

UPDATE work_order
SET tracking_code = 'FR-XZ-20260710-002'
WHERE tracking_code = 'DEMO-A002';

UPDATE work_order_status_history
SET note = '系统初始数据导入'
WHERE note = '演示数据初始化'
  AND work_order_id IN (
    SELECT id
    FROM work_order
    WHERE tracking_code IN ('FR-XZ-20260710-001', 'FR-XZ-20260710-002')
  );

DELETE FROM work_order
WHERE tracking_code = 'FR-5329413D4DFD'
   OR description LIKE '浏览器交付验收：%';
