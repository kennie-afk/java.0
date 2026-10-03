package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.LandlordTaxSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordTaxSettingsRepository extends JpaRepository<LandlordTaxSettings, UUID> {
}
