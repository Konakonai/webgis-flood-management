package com.floodgis.service;

import com.floodgis.entity.MonitorData;
import com.floodgis.entity.MonitorStation;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MockDataPushServiceTest {

    @Test
    void publishesAndChecksOnlySamplesGeneratedInCurrentRun() {
        MonitorDataService dataService = mock(MonitorDataService.class);
        MonitorStationService stationService = mock(MonitorStationService.class);
        WarningDetector detector = mock(WarningDetector.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        MonitorData fresh = new MonitorData();
        fresh.setStationId(3L);
        fresh.setDataType("WATER_LEVEL");
        fresh.setValue(1.2);
        fresh.setUnit("m");
        fresh.setWarningLevel(1);
        fresh.setRecordedAt(LocalDateTime.now());
        MonitorStation station = new MonitorStation();
        station.setId(3L);
        station.setName("测试站");
        when(dataService.generateMockData()).thenReturn(List.of(fresh));
        when(stationService.getById(3L)).thenReturn(station);

        new MockDataPushService(dataService, stationService, detector, messaging).pushMonitorData();

        verify(messaging).convertAndSend(eq("/topic/monitor"), any(Map.class));
        verify(detector).detectAndCreate(fresh);
        verify(dataService, never()).getLatestByStations();
    }
}
