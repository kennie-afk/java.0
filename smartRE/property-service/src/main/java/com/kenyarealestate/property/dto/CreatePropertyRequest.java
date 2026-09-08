package com.kenyarealestate.property.dto;
import jakarta.validation.constraints.*; import lombok.Data;
import java.math.BigDecimal; import java.util.List;
@Data public class CreatePropertyRequest {
    @NotBlank private String title; private String description;
    @NotBlank private String propertyType; @NotBlank private String listingType;
    @NotBlank private String county; private String subCounty, city, locationDescription;
    private Double latitude, longitude;
    @NotNull @DecimalMin("0.01") private BigDecimal price;
    private Integer bedrooms, bathrooms, yearBuilt; private Double areaSqm;
    /**
     * Photos are required for anything that will be advertised, and pointless for a
     * property that never will be. The check therefore moved into the service, where
     * {@link #manageOnly} is known — a bean-validation annotation cannot see it.
     */
    private List<String> imageUrls;

    /**
     * True when the owner wants a management container and not a listing.
     *
     * <p>Defaults to false so every existing caller keeps its current behaviour.
     */
    private boolean manageOnly;
}
