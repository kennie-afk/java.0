package com.kenyarealestate.pms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data @NoArgsConstructor @AllArgsConstructor
public class PropertySummary {
    private UUID id;
    private UUID sellerId;
    private String title;
    private String listingType;
    private String county;
    private String city;
}
