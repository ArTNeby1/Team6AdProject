package com.loomytrip.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "destination")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 64)
    private String category;

    @Column(name = "opening_hours", length = 255)
    private String openingHours;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 64)
    private String price;

    @Column(name = "external_place_id", length = 128)
    private String externalPlaceId;

    @Column(nullable = false, length = 255)
    private String address;
}
