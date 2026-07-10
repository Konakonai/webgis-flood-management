package com.floodgis.dto;

public record PumpStationView(
        String id,
        Long resourceId,
        String name,
        String type,
        Double lng,
        Double lat,
        String address,
        String vehicle,
        String contact,
        String status
) {
}
