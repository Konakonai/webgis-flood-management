package com.floodgis.dto;

public record ResourcePointView(
        String id,
        String name,
        String type,
        Double lng,
        Double lat,
        String address,
        String status,
        String details
) {
}
