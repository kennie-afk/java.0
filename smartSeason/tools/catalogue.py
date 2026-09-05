"""SmartSeason service catalogue.

Field DSL:  "name:type[:flags]"
  types: uuid string text int long decimal bool ts date json
         enum(A|B|C)  ref(entity)
  flags: nn (not null), uq (unique), ix (indexed)

Every entity implicitly gets: id (uuid pk), tenant_id (uuid, indexed),
created_at, updated_at, version (optimistic lock).
"""

SERVICES = [
 dict(name="identity-service", port=8081, db="identity_db", group="identity",
      desc="Users, organisations, roles, sessions, KYC, JWT issuance + JWKS",
      publishes=["UserRegistered","UserSuspended","OrgCreated"],
      entities=[
        ("Organisation","organisations",[
          "name:string:nn","orgType:enum(FARM|COOPERATIVE|BUYER|TRANSPORTER|ADMIN):nn",
          "county:string","registrationNo:string:uq","phone:string","email:string",
          "status:enum(ACTIVE|SUSPENDED|PENDING):nn","kycStatus:enum(NONE|PENDING|VERIFIED|REJECTED):nn"]),
        ("User","users",[
          "email:string:uq,nn","phone:string:ix","fullName:string:nn","passwordHash:string:nn",
          "organisationId:uuid:ix","roles:string:nn","status:enum(ACTIVE|SUSPENDED|LOCKED):nn",
          "mfaEnabled:bool:nn","lastLoginAt:ts","failedAttempts:int:nn","locale:string"]),
        ("RefreshToken","refresh_tokens",[
          "userId:uuid:ix,nn","tokenHash:string:uq,nn","expiresAt:ts:nn","revokedAt:ts","userAgent:string","ip:string"]),
        ("KycRecord","kyc_records",[
          "subjectId:uuid:ix,nn","subjectType:enum(USER|ORGANISATION):nn","idNumber:string",
          "documentUrl:string","status:enum(PENDING|VERIFIED|REJECTED):nn","reviewedBy:uuid","reviewNotes:text"]),
        ("OtpChallenge","otp_challenges",[
          "userId:uuid:ix","destination:string:nn","channel:enum(SMS|EMAIL):nn","codeHash:string:nn",
          "purpose:string:nn","expiresAt:ts:nn","consumedAt:ts","attempts:int:nn"]),
      ]),

 dict(name="farm-service", port=8082, db="farm_db", group="farm",
      desc="Farms, plots, geo boundaries, soil profiles, cooperative membership",
      publishes=["FarmCreated","PlotCreated","PlotBoundaryUpdated"],
      entities=[
        ("Farm","farms",[
          "name:string:nn","ownerUserId:uuid:ix","county:string:ix","subCounty:string","ward:string",
          "latitude:decimal","longitude:decimal","totalAreaHa:decimal","status:enum(ACTIVE|ARCHIVED):nn",
          "cooperativeId:uuid:ix","registrationNo:string"]),
        ("Plot","plots",[
          "farmId:uuid:ix,nn","name:string:nn","areaHa:decimal:nn","boundaryGeojson:json",
          "centroidLat:decimal","centroidLng:decimal","irrigated:bool:nn",
          "currentCrop:string","status:enum(ACTIVE|FALLOW|RETIRED):nn"]),
        ("SoilProfile","soil_profiles",[
          "plotId:uuid:ix,nn","sampledAt:date:nn","ph:decimal","nitrogenPpm:decimal","phosphorusPpm:decimal",
          "potassiumPpm:decimal","organicCarbonPct:decimal","texture:string","labName:string","reportUrl:string"]),
        ("FarmMembership","farm_memberships",[
          "farmId:uuid:ix,nn","userId:uuid:ix,nn","role:enum(OWNER|MANAGER|AGRONOMIST|VIEWER):nn",
          "invitedBy:uuid","acceptedAt:ts","status:enum(PENDING|ACTIVE|REVOKED):nn"]),
      ]),

 dict(name="season-service", port=8083, db="season_db", group="farm",
      desc="Crop cycles per plot, stage calendars, planting plans, yields",
      publishes=["SeasonStarted","StageAdvanced","SeasonClosed"],
      entities=[
        ("Season","seasons",[
          "plotId:uuid:ix,nn","farmId:uuid:ix","cropCode:string:nn","variety:string",
          "startDate:date:nn","expectedHarvestDate:date","actualHarvestDate:date",
          "expectedYieldKg:decimal","actualYieldKg:decimal","currentStage:string",
          "status:enum(PLANNED|ACTIVE|HARVESTED|CLOSED|ABANDONED):nn"]),
        ("StageTemplate","stage_templates",[
          "cropCode:string:ix,nn","stageName:string:nn","sequence:int:nn","durationDays:int:nn",
          "description:text","keyActivities:text"]),
        ("SeasonStage","season_stages",[
          "seasonId:uuid:ix,nn","stageName:string:nn","sequence:int:nn","plannedStart:date",
          "plannedEnd:date","actualStart:date","actualEnd:date",
          "status:enum(PENDING|ACTIVE|COMPLETE|SKIPPED):nn","notes:text"]),
        ("PlantingPlan","planting_plans",[
          "seasonId:uuid:ix,nn","seedRateKgHa:decimal","spacingCm:string","targetPopulation:int",
          "fertiliserPlan:text","irrigationPlan:text","approvedBy:uuid","approvedAt:ts"]),
      ]),

 dict(name="agronomy-service", port=8084, db="agronomy_db", group="farm",
      desc="Advisories, pest/disease library, scouting reports, crop playbooks",
      publishes=["AdvisoryIssued","RiskFlagRaised"],
      entities=[
        ("Advisory","advisories",[
          "seasonId:uuid:ix","plotId:uuid:ix","cropCode:string:ix","title:string:nn","body:text:nn",
          "severity:enum(INFO|LOW|MEDIUM|HIGH|CRITICAL):nn","source:enum(RULE|AGRONOMIST|MODEL|WEATHER):nn",
          "issuedAt:ts:nn","acknowledgedAt:ts","acknowledgedBy:uuid"]),
        ("PestDisease","pest_diseases",[
          "code:string:uq,nn","commonName:string:nn","scientificName:string",
          "type:enum(PEST|DISEASE|WEED|DEFICIENCY):nn","affectedCrops:string","symptoms:text",
          "management:text","imageUrl:string"]),
        ("ScoutingReport","scouting_reports",[
          "plotId:uuid:ix,nn","seasonId:uuid:ix","scoutedBy:uuid:nn","scoutedAt:ts:nn",
          "pestDiseaseCode:string","incidencePct:decimal","severityScore:int","latitude:decimal",
          "longitude:decimal","photoUrl:string","notes:text",
          "status:enum(OPEN|ACTIONED|CLOSED):nn"]),
        ("CropPlaybook","crop_playbooks",[
          "cropCode:string:ix,nn","stageName:string:nn","guidance:text:nn","inputRecommendations:text",
          "riskFactors:text","revision:int:nn"]),
      ]),

 dict(name="weather-service", port=8085, db="weather_db", group="farm",
      desc="Weather feeds, forecasts, NDVI, agro-climatic alerts per geo cell",
      publishes=["WeatherForecastUpdated","WeatherAlert"],
      entities=[
        ("WeatherStation","weather_stations",[
          "externalId:string:uq,nn","name:string:nn","latitude:decimal:nn","longitude:decimal:nn",
          "elevationM:decimal","provider:string:nn","county:string:ix","active:bool:nn"]),
        ("Forecast","forecasts",[
          "geoCell:string:ix,nn","forecastFor:date:nn","issuedAt:ts:nn","tempMinC:decimal","tempMaxC:decimal",
          "rainfallMm:decimal","humidityPct:decimal","windKph:decimal","conditions:string","provider:string"]),
        ("WeatherAlertRecord","weather_alerts",[
          "geoCell:string:ix,nn","alertType:enum(DROUGHT|FLOOD|FROST|HAIL|HEATWAVE|STORM):nn",
          "severity:enum(LOW|MEDIUM|HIGH|EXTREME):nn","startsAt:ts:nn","endsAt:ts","headline:string:nn",
          "body:text","source:string"]),
        ("NdviReading","ndvi_readings",[
          "plotId:uuid:ix","geoCell:string:ix","capturedOn:date:nn","ndvi:decimal:nn",
          "cloudCoverPct:decimal","satellite:string","tileUrl:string"]),
      ]),

 dict(name="device-registry-service", port=8086, db="device_db", group="iot",
      desc="Device identity, provisioning, plot mapping, firmware, credentials",
      publishes=["DeviceProvisioned","DeviceDecommissioned"],
      entities=[
        ("Device","devices",[
          "serialNumber:string:uq,nn","deviceType:enum(SOIL_PROBE|WEATHER_NODE|VALVE|PUMP|GATEWAY|SCALE):nn",
          "plotId:uuid:ix","farmId:uuid:ix","model:string","firmwareVersion:string",
          "status:enum(PROVISIONED|ACTIVE|OFFLINE|DECOMMISSIONED):nn","lastSeenAt:ts",
          "latitude:decimal","longitude:decimal"]),
        ("DeviceCredential","device_credentials",[
          "deviceId:uuid:ix,nn","credentialType:enum(CERT|PSK|TOKEN):nn","publicKey:text",
          "fingerprint:string:uq","issuedAt:ts:nn","expiresAt:ts","revokedAt:ts"]),
        ("FirmwareRelease","firmware_releases",[
          "deviceType:string:ix,nn","releaseVersion:string:nn","artifactUrl:string:nn","checksum:string:nn",
          "releaseNotes:text","mandatory:bool:nn","publishedAt:ts"]),
      ]),

 dict(name="telemetry-ingest-service", port=8087, db="telemetry_db", group="iot",
      desc="Raw + downsampled device telemetry, anomaly emission (highest write volume)",
      publishes=["TelemetryReading","TelemetryAnomaly"],
      entities=[
        ("TelemetryReading","telemetry_readings",[
          "deviceId:uuid:ix,nn","plotId:uuid:ix","metric:string:ix,nn","value:decimal:nn","unit:string",
          "recordedAt:ts:nn","receivedAt:ts:nn","quality:enum(GOOD|SUSPECT|BAD):nn","raw:json"]),
        ("TelemetryAnomalyRecord","telemetry_anomalies",[
          "deviceId:uuid:ix,nn","plotId:uuid:ix","metric:string:nn","observedValue:decimal:nn",
          "expectedMin:decimal","expectedMax:decimal","detectedAt:ts:nn",
          "severity:enum(LOW|MEDIUM|HIGH):nn","resolved:bool:nn"]),
        ("DownsampledReading","downsampled_readings",[
          "deviceId:uuid:ix,nn","metric:string:nn","bucketStart:ts:nn","bucketMinutes:int:nn",
          "avgValue:decimal","minValue:decimal","maxValue:decimal","sampleCount:int:nn"]),
      ]),

 dict(name="automation-service", port=8088, db="automation_db", group="iot",
      desc="Automation rules, actuator command pipeline, safety interlocks, digital twin",
      publishes=["CommandIssued","CommandAcknowledged","RuleTriggered"],
      entities=[
        ("AutomationRule","automation_rules",[
          "name:string:nn","plotId:uuid:ix","triggerMetric:string","operator:enum(LT|LTE|GT|GTE|EQ):nn",
          "threshold:decimal","actionType:enum(IRRIGATE|VENTILATE|ALERT|DOSE):nn","actionTargetDeviceId:uuid",
          "durationSeconds:int","cooldownSeconds:int:nn","enabled:bool:nn","lastTriggeredAt:ts"]),
        ("ActuatorCommand","actuator_commands",[
          "deviceId:uuid:ix,nn","ruleId:uuid:ix","commandKey:string:uq,nn","action:string:nn","payload:json",
          "issuedAt:ts:nn","acknowledgedAt:ts","completedAt:ts",
          "status:enum(PENDING|SENT|ACKED|COMPLETED|FAILED|TIMEOUT):nn","attempts:int:nn","failureReason:string"]),
        ("DigitalTwin","digital_twins",[
          "plotId:uuid:uq,nn","soilMoisturePct:decimal","soilTempC:decimal","canopyIndex:decimal",
          "irrigationState:enum(IDLE|RUNNING|FAULT):nn","lastIrrigatedAt:ts","updatedFromEventAt:ts","state:json"]),
        ("SafetyInterlock","safety_interlocks",[
          "deviceId:uuid:ix,nn","interlockType:enum(MAX_RUNTIME|MUTUAL_EXCLUSION|MANUAL_OVERRIDE):nn",
          "maxRuntimeSeconds:int","conflictingDeviceId:uuid","engaged:bool:nn","engagedAt:ts","reason:string"]),
      ]),
]

SERVICES += [
 dict(name="workforce-service", port=8089, db="workforce_db", group="workforce",
      desc="Workers, contracts, wage rates, gangs, supervisors, farm assignment",
      publishes=["WorkerOnboarded","ContractChanged"],
      entities=[
        ("Worker","workers",[
          "nationalId:string:ix","fullName:string:nn","phone:string:ix","gender:enum(MALE|FEMALE|OTHER)",
          "dateOfBirth:date","farmId:uuid:ix","payoutPhone:string","payoutAccount:string",
          "biometricRef:string","status:enum(ACTIVE|SUSPENDED|TERMINATED):nn","riskScore:int:nn",
          "onboardedAt:ts","photoUrl:string"]),
        ("WorkerContract","worker_contracts",[
          "workerId:uuid:ix,nn","farmId:uuid:ix,nn","contractType:enum(CASUAL|SEASONAL|PERMANENT|PIECE_RATE):nn",
          "startDate:date:nn","endDate:date","dailyRate:decimal","pieceRate:decimal","pieceUnit:string",
          "supervisorId:uuid","status:enum(DRAFT|ACTIVE|ENDED|TERMINATED):nn","terms:text"]),
        ("WageRate","wage_rates",[
          "farmId:uuid:ix","taskCode:string:ix,nn","rateType:enum(DAILY|HOURLY|PIECE):nn",
          "amount:decimal:nn","currency:string:nn","unit:string","effectiveFrom:date:nn","effectiveTo:date"]),
        ("Gang","gangs",[
          "name:string:nn","farmId:uuid:ix,nn","supervisorId:uuid:ix","targetSize:int",
          "status:enum(ACTIVE|DISBANDED):nn","notes:text"]),
        ("GangMembership","gang_memberships",[
          "gangId:uuid:ix,nn","workerId:uuid:ix,nn","joinedAt:ts:nn","leftAt:ts",
          "role:enum(MEMBER|LEAD):nn"]),
      ]),

 dict(name="attendance-service", port=8090, db="attendance_db", group="workforce",
      desc="Geofenced biometric clock-in/out, shifts, piece-rate tallies, offline sync",
      publishes=["ShiftStarted","ShiftEnded","PieceRateRecorded"],
      entities=[
        ("Geofence","geofences",[
          "farmId:uuid:ix,nn","plotId:uuid:ix","name:string:nn","centerLat:decimal:nn","centerLng:decimal:nn",
          "radiusM:int:nn","active:bool:nn"]),
        ("ClockEvent","clock_events",[
          "workerId:uuid:ix,nn","farmId:uuid:ix,nn","shiftId:uuid:ix",
          "eventType:enum(CLOCK_IN|CLOCK_OUT|BREAK_START|BREAK_END):nn","occurredAt:ts:nn","recordedAt:ts:nn",
          "latitude:decimal","longitude:decimal","accuracyM:decimal","geofenceId:uuid",
          "insideGeofence:bool:nn","biometricScore:decimal","deviceId:string","mockLocation:bool:nn",
          "offlineSynced:bool:nn","clientEventId:string:uq",
          "verdict:enum(ACCEPTED|FLAGGED|REJECTED):nn","flagReason:string"]),
        ("Shift","shifts",[
          "workerId:uuid:ix,nn","farmId:uuid:ix,nn","gangId:uuid:ix","startedAt:ts:nn","endedAt:ts",
          "durationMinutes:int","breakMinutes:int:nn","supervisorId:uuid",
          "status:enum(OPEN|CLOSED|DISPUTED):nn","anomalyFlags:string"]),
        ("PieceRateEntry","piece_rate_entries",[
          "workerId:uuid:ix,nn","shiftId:uuid:ix","farmId:uuid:ix,nn","plotId:uuid:ix","taskCode:string:nn",
          "quantity:decimal:nn","unit:string:nn","recordedAt:ts:nn","recordedBy:uuid",
          "weighStationId:string","verifiedBy:uuid","verifiedAt:ts",
          "status:enum(RECORDED|VERIFIED|DISPUTED|VOID):nn"]),
      ]),

 dict(name="task-service", port=8091, db="task_db", group="workforce",
      desc="Work orders, assignments, checklists, photo/GPS evidence, verification",
      publishes=["TaskAssigned","TaskCompleted","TaskVerified"],
      entities=[
        ("WorkOrder","work_orders",[
          "farmId:uuid:ix,nn","plotId:uuid:ix","seasonId:uuid:ix","taskCode:string:nn","title:string:nn",
          "description:text","dueDate:date","priority:enum(LOW|NORMAL|HIGH|URGENT):nn",
          "estimatedHours:decimal","createdBy:uuid",
          "status:enum(DRAFT|OPEN|IN_PROGRESS|COMPLETED|VERIFIED|CANCELLED):nn"]),
        ("TaskAssignment","task_assignments",[
          "workOrderId:uuid:ix,nn","workerId:uuid:ix","gangId:uuid:ix","assignedBy:uuid:nn","assignedAt:ts:nn",
          "acceptedAt:ts","startedAt:ts","completedAt:ts",
          "status:enum(ASSIGNED|ACCEPTED|IN_PROGRESS|COMPLETED|REJECTED):nn"]),
        ("TaskEvidence","task_evidence",[
          "assignmentId:uuid:ix,nn","workOrderId:uuid:ix","evidenceType:enum(PHOTO|GPS|SIGNATURE|NOTE):nn",
          "mediaUrl:string","perceptualHash:string:ix","latitude:decimal","longitude:decimal",
          "capturedAt:ts","exifTimestamp:ts","mockLocation:bool:nn","notes:text",
          "verdict:enum(PENDING|ACCEPTED|SUSPECT|REJECTED):nn"]),
        ("ChecklistItem","checklist_items",[
          "workOrderId:uuid:ix,nn","label:string:nn","sequence:int:nn","required:bool:nn",
          "completed:bool:nn","completedAt:ts","completedBy:uuid"]),
      ]),

 dict(name="fraud-service", port=8092, db="fraud_db", group="workforce",
      desc="Fraud rules engine, anomaly scoring, cases, evidence bundles, review queue",
      publishes=["FraudCaseOpened","FraudCaseResolved","WorkerRiskScoreUpdated"],
      entities=[
        ("FraudRule","fraud_rules",[
          "code:string:uq,nn","typology:enum(GHOST_WORKER|PROXY_CLOCK_IN|PIECE_RATE_INFLATION|INPUT_DIVERSION|HARVEST_SKIMMING|VEHICLE_FUEL|COLLUSION|EVIDENCE_FRAUD|HOURS_INFLATION|PROCUREMENT_KICKBACK):nn",
          "name:string:nn","description:text","expression:text:nn","threshold:decimal",
          "severity:enum(LOW|MEDIUM|HIGH|CRITICAL):nn","weight:int:nn","enabled:bool:nn","autoHoldPayout:bool:nn"]),
        ("FraudSignal","fraud_signals",[
          "subjectType:enum(WORKER|SUPERVISOR|VEHICLE|VENDOR|BATCH):nn","subjectId:uuid:ix,nn",
          "ruleCode:string:ix,nn","typology:string:nn","score:decimal:nn","detectedAt:ts:nn",
          "sourceEvent:string","details:json","caseId:uuid:ix"]),
        ("FraudCase","fraud_cases",[
          "caseNumber:string:uq,nn","subjectType:enum(WORKER|SUPERVISOR|VEHICLE|VENDOR|BATCH):nn",
          "subjectId:uuid:ix,nn","farmId:uuid:ix","typology:string:nn",
          "severity:enum(LOW|MEDIUM|HIGH|CRITICAL):nn","confidence:decimal:nn","openedAt:ts:nn",
          "status:enum(OPEN|INVESTIGATING|CONFIRMED|DISMISSED|APPEALED|CLOSED):nn",
          "assignedTo:uuid","resolvedAt:ts","resolution:text","payoutHeld:bool:nn",
          "appealedAt:ts","appealOutcome:string"]),
        ("FraudEvidence","fraud_evidence",[
          "caseId:uuid:ix,nn","label:string:nn","evidenceType:string:nn","payload:json","weight:decimal",
          "collectedAt:ts:nn"]),
        ("WorkerRiskScore","worker_risk_scores",[
          "workerId:uuid:uq,nn","farmId:uuid:ix","score:int:nn","band:enum(LOW|MEDIUM|HIGH|CRITICAL):nn",
          "openCases:int:nn","lastSignalAt:ts","decayAppliedAt:ts","components:json"]),
      ]),

 dict(name="catalog-service", port=8093, db="catalog_db", group="market",
      desc="Produce taxonomy, products, variants, grading standards, certifications",
      publishes=["ProductPublished","GradeStandardUpdated"],
      entities=[
        ("Commodity","commodities",[
          "code:string:uq,nn","name:string:nn","category:string:ix","defaultUnit:string:nn",
          "perishable:bool:nn","shelfLifeDays:int","imageUrl:string"]),
        ("Product","products",[
          "commodityCode:string:ix,nn","name:string:nn","description:text","defaultGrade:string",
          "status:enum(DRAFT|PUBLISHED|ARCHIVED):nn"]),
        ("ProductVariant","product_variants",[
          "productId:uuid:ix,nn","sku:string:uq,nn","variantName:string:nn","packSize:decimal",
          "packUnit:string","grade:string","active:bool:nn"]),
        ("GradeStandard","grade_standards",[
          "commodityCode:string:ix,nn","grade:string:nn","criteria:text:nn","minSizeMm:decimal",
          "maxDefectPct:decimal","moisturePctMax:decimal","revision:int:nn"]),
        ("Certification","certifications",[
          "code:string:uq,nn","name:string:nn","issuingBody:string","description:text","validityMonths:int"]),
      ]),

 dict(name="marketplace-service", port=8094, db="marketplace_db", group="market",
      desc="Supply listings, demand posts, offers, buyer-seller matching",
      publishes=["ListingPublished","OfferMade","OfferAccepted","MatchCreated"],
      entities=[
        ("SupplyListing","supply_listings",[
          "sellerOrgId:uuid:ix,nn","farmId:uuid:ix","commodityCode:string:ix,nn","variety:string",
          "grade:string","quantity:decimal:nn","unit:string:nn","askPrice:decimal:nn","currency:string:nn",
          "availableFrom:date","availableTo:date","county:string:ix","latitude:decimal","longitude:decimal",
          "batchId:uuid","photoUrls:text","description:text",
          "status:enum(DRAFT|ACTIVE|RESERVED|SOLD|EXPIRED|WITHDRAWN):nn"]),
        ("DemandPost","demand_posts",[
          "buyerOrgId:uuid:ix,nn","commodityCode:string:ix,nn","grade:string","quantity:decimal:nn",
          "unit:string:nn","bidPrice:decimal","currency:string:nn","neededBy:date","deliveryCounty:string:ix",
          "recurring:bool:nn","status:enum(OPEN|PARTIALLY_FILLED|FILLED|EXPIRED|CANCELLED):nn","notes:text"]),
        ("Offer","offers",[
          "listingId:uuid:ix","demandPostId:uuid:ix","fromOrgId:uuid:ix,nn","toOrgId:uuid:ix,nn",
          "quantity:decimal:nn","unitPrice:decimal:nn","currency:string:nn","expiresAt:ts",
          "status:enum(PENDING|ACCEPTED|REJECTED|COUNTERED|EXPIRED|WITHDRAWN):nn",
          "counterOfferId:uuid","message:text","respondedAt:ts"]),
        ("MarketMatch","market_matches",[
          "listingId:uuid:ix,nn","demandPostId:uuid:ix,nn","score:decimal:nn","matchedAt:ts:nn",
          "quantity:decimal","orderId:uuid","status:enum(SUGGESTED|ACCEPTED|DECLINED|ORDERED):nn"]),
      ]),

 dict(name="pricing-service", port=8095, db="pricing_db", group="market",
      desc="Reference prices, market index per commodity/region, price series, suggestions",
      publishes=["PriceIndexUpdated"],
      entities=[
        ("PriceSeries","price_series",[
          "commodityCode:string:ix,nn","county:string:ix","marketName:string","grade:string",
          "observedOn:date:nn","unit:string:nn","minPrice:decimal","maxPrice:decimal","avgPrice:decimal:nn",
          "currency:string:nn","source:string","volumeKg:decimal"]),
        ("MarketIndex","market_indices",[
          "commodityCode:string:ix,nn","region:string:ix,nn","periodStart:date:nn","periodEnd:date:nn",
          "indexValue:decimal:nn","changePct:decimal","basis:string","computedAt:ts:nn"]),
        ("PriceQuote","price_quotes",[
          "commodityCode:string:ix,nn","grade:string","county:string","quantity:decimal",
          "suggestedPrice:decimal:nn","confidence:decimal","currency:string:nn","validUntil:ts",
          "rationale:text","requestedBy:uuid"]),
      ]),

 dict(name="order-service", port=8096, db="order_db", group="market",
      desc="Carts, orders, fulfillment saga, returns, disputes",
      publishes=["OrderPlaced","OrderConfirmed","OrderFulfilled","OrderCancelled"],
      entities=[
        ("Cart","carts",[
          "buyerOrgId:uuid:ix,nn","buyerUserId:uuid","currency:string:nn",
          "status:enum(OPEN|CHECKED_OUT|ABANDONED):nn","expiresAt:ts"]),
        ("CartItem","cart_items",[
          "cartId:uuid:ix,nn","listingId:uuid:ix,nn","commodityCode:string:nn","quantity:decimal:nn",
          "unit:string:nn","unitPrice:decimal:nn","sellerOrgId:uuid:nn"]),
        ("PurchaseOrder","orders",[
          "orderNumber:string:uq,nn","buyerOrgId:uuid:ix,nn","sellerOrgId:uuid:ix,nn","currency:string:nn",
          "subtotal:decimal:nn","deliveryFee:decimal:nn","platformFee:decimal:nn","totalAmount:decimal:nn",
          "placedAt:ts:nn","confirmedAt:ts","fulfilledAt:ts","cancelledAt:ts","cancellationReason:string",
          "deliveryCounty:string","deliveryAddress:text","deliveryLat:decimal","deliveryLng:decimal",
          "paymentIntentId:uuid:ix","transportJobId:uuid",
          "status:enum(PENDING_PAYMENT|PAID|CONFIRMED|IN_TRANSIT|DELIVERED|COMPLETED|CANCELLED|REFUNDED):nn",
          "idempotencyKey:string:uq"]),
        ("OrderLine","order_lines",[
          "orderId:uuid:ix,nn","listingId:uuid:ix","commodityCode:string:nn","grade:string",
          "quantity:decimal:nn","unit:string:nn","unitPrice:decimal:nn","lineTotal:decimal:nn",
          "batchId:uuid","fulfilledQuantity:decimal:nn"]),
        ("OrderSagaState","order_saga_states",[
          "orderId:uuid:uq,nn","currentStep:string:nn","stepStatus:enum(PENDING|RUNNING|DONE|FAILED|COMPENSATED):nn",
          "attempts:int:nn","lastError:text","startedAt:ts:nn","lastTransitionAt:ts","completedAt:ts","context:json"]),
        ("OrderReturn","order_returns",[
          "orderId:uuid:ix,nn","orderLineId:uuid:ix","quantity:decimal:nn","reason:text:nn",
          "requestedBy:uuid","requestedAt:ts:nn","refundAmount:decimal",
          "status:enum(REQUESTED|APPROVED|REJECTED|REFUNDED):nn"]),
        ("Dispute","disputes",[
          "orderId:uuid:ix,nn","raisedByOrgId:uuid:nn","category:enum(QUALITY|QUANTITY|DELIVERY|PAYMENT|OTHER):nn",
          "description:text:nn","raisedAt:ts:nn","status:enum(OPEN|UNDER_REVIEW|RESOLVED|ESCALATED|CLOSED):nn",
          "resolution:text","resolvedAt:ts","resolvedBy:uuid"]),
      ]),
]

SERVICES += [
 dict(name="inventory-service", port=8097, db="inventory_db", group="market",
      desc="Aggregation-centre stock, batches, grading, reservations, farm-input reconciliation",
      publishes=["StockReceived","StockReserved","BatchGraded","InputIssued","InputConsumed"],
      entities=[
        ("Warehouse","warehouses",[
          "name:string:nn","county:string:ix","latitude:decimal","longitude:decimal","capacityKg:decimal",
          "coldChain:bool:nn","managerUserId:uuid","status:enum(ACTIVE|CLOSED):nn"]),
        ("Batch","batches",[
          "batchCode:string:uq,nn","commodityCode:string:ix,nn","farmId:uuid:ix","plotId:uuid:ix",
          "seasonId:uuid:ix","harvestedOn:date","receivedAt:ts","warehouseId:uuid:ix",
          "grossWeightKg:decimal","netWeightKg:decimal","grade:string","moisturePct:decimal",
          "status:enum(HARVESTED|IN_TRANSIT|RECEIVED|GRADED|RESERVED|DISPATCHED|REJECTED):nn"]),
        ("StockItem","stock_items",[
          "warehouseId:uuid:ix,nn","commodityCode:string:ix,nn","grade:string","batchId:uuid:ix",
          "quantity:decimal:nn","unit:string:nn","reservedQuantity:decimal:nn","expiresOn:date",
          "lastCountedAt:ts"]),
        ("GradingResult","grading_results",[
          "batchId:uuid:ix,nn","gradedBy:uuid","gradedAt:ts:nn","assignedGrade:string:nn",
          "sizeMm:decimal","defectPct:decimal","moisturePct:decimal","rejectedKg:decimal",
          "notes:text","standardVersion:int"]),
        ("Reservation","reservations",[
          "stockItemId:uuid:ix,nn","orderId:uuid:ix","quantity:decimal:nn","reservedAt:ts:nn",
          "expiresAt:ts","releasedAt:ts","status:enum(HELD|CONSUMED|RELEASED|EXPIRED):nn"]),
        ("InputIssue","input_issues",[
          "farmId:uuid:ix,nn","plotId:uuid:ix","seasonId:uuid:ix","inputCode:string:ix,nn",
          "inputName:string:nn","quantity:decimal:nn","unit:string:nn","issuedTo:uuid","issuedBy:uuid",
          "issuedAt:ts:nn","unitCost:decimal","expectedRatePerHa:decimal","status:enum(ISSUED|RECONCILED|VARIANCE):nn"]),
        ("InputConsumption","input_consumptions",[
          "inputIssueId:uuid:ix","farmId:uuid:ix,nn","plotId:uuid:ix","seasonId:uuid:ix",
          "inputCode:string:nn","quantity:decimal:nn","unit:string:nn","appliedAt:ts:nn","appliedBy:uuid",
          "areaCoveredHa:decimal","evidenceUrl:string","varianceKg:decimal"]),
      ]),

 dict(name="logistics-service", port=8098, db="logistics_db", group="market",
      desc="Transport jobs, driver/vehicle assignment, routing, cold chain, proof of delivery",
      publishes=["TransportJobCreated","TransportJobAssigned","DeliveryCompleted"],
      entities=[
        ("Vehicle","vehicles",[
          "registrationNo:string:uq,nn","vehicleType:enum(PICKUP|TRUCK|REFRIGERATED|MOTORCYCLE|TRACTOR):nn",
          "capacityKg:decimal","coldChain:bool:nn","ownerOrgId:uuid:ix","odometerKm:decimal",
          "status:enum(AVAILABLE|ON_JOB|MAINTENANCE|RETIRED):nn","lastServiceAt:date"]),
        ("Driver","drivers",[
          "userId:uuid:ix","fullName:string:nn","phone:string:ix,nn","licenceNumber:string:uq",
          "licenceExpiry:date","assignedVehicleId:uuid:ix","rating:decimal",
          "status:enum(AVAILABLE|ON_JOB|SUSPENDED|OFFLINE):nn"]),
        ("TransportJob","transport_jobs",[
          "jobNumber:string:uq,nn","orderId:uuid:ix","batchId:uuid:ix","vehicleId:uuid:ix","driverId:uuid:ix",
          "pickupCounty:string","pickupLat:decimal","pickupLng:decimal","pickupAt:ts",
          "dropoffCounty:string","dropoffLat:decimal","dropoffLng:decimal","dropoffAt:ts",
          "distanceKm:decimal","weightKg:decimal","freightCost:decimal","currency:string",
          "requiresColdChain:bool:nn",
          "status:enum(CREATED|ASSIGNED|PICKED_UP|IN_TRANSIT|DELIVERED|FAILED|CANCELLED):nn"]),
        ("RouteStop","route_stops",[
          "transportJobId:uuid:ix,nn","sequence:int:nn","stopType:enum(PICKUP|DROPOFF|WAYPOINT|CHECKPOINT):nn",
          "latitude:decimal","longitude:decimal","plannedAt:ts","arrivedAt:ts","departedAt:ts",
          "notes:text","offRoute:bool:nn"]),
        ("ProofOfDelivery","proofs_of_delivery",[
          "transportJobId:uuid:uq,nn","receivedBy:string:nn","receivedAt:ts:nn","signatureUrl:string",
          "photoUrl:string","latitude:decimal","longitude:decimal","deliveredWeightKg:decimal",
          "varianceKg:decimal","notes:text","disputed:bool:nn"]),
        ("ColdChainReading","cold_chain_readings",[
          "transportJobId:uuid:ix,nn","recordedAt:ts:nn","temperatureC:decimal:nn","humidityPct:decimal",
          "deviceId:string","breach:bool:nn"]),
      ]),

 dict(name="payment-service", port=8099, db="payment_db", group="money",
      desc="Payment intents, M-Pesa STK/C2B/B2C, cards, wallets, escrow, callback reconciliation",
      publishes=["PaymentInitiated","PaymentSucceeded","PaymentFailed","EscrowHeld","EscrowReleased"],
      entities=[
        ("PaymentIntent","payment_intents",[
          "reference:string:uq,nn","orderId:uuid:ix","payerOrgId:uuid:ix","payeeOrgId:uuid:ix",
          "amount:decimal:nn","currency:string:nn","method:enum(MPESA_STK|MPESA_C2B|MPESA_B2C|CARD|WALLET|BANK):nn",
          "purpose:enum(ORDER|WALLET_TOPUP|PAYOUT|WAGE|REFUND|FEE):nn","payerPhone:string",
          "status:enum(CREATED|PENDING|PROCESSING|SUCCEEDED|FAILED|CANCELLED|REFUNDED):nn",
          "idempotencyKey:string:uq,nn","initiatedAt:ts:nn","completedAt:ts","failureReason:string",
          "providerRef:string:ix","escrow:bool:nn"]),
        ("MpesaTransaction","mpesa_transactions",[
          "paymentIntentId:uuid:ix","merchantRequestId:string:ix","checkoutRequestId:string:ix",
          "mpesaReceiptNumber:string:uq","phoneNumber:string:ix","amount:decimal:nn",
          "transactionType:enum(STK_PUSH|C2B|B2C|REVERSAL|BALANCE):nn","resultCode:int","resultDesc:string",
          "transactionDate:ts","accountReference:string","rawCallback:json",
          "status:enum(INITIATED|PENDING|SUCCESS|FAILED|TIMEOUT):nn"]),
        ("EscrowHold","escrow_holds",[
          "paymentIntentId:uuid:ix,nn","orderId:uuid:ix,nn","amount:decimal:nn","currency:string:nn",
          "heldAt:ts:nn","releaseDueAt:ts","releasedAt:ts","releasedTo:uuid","refundedAt:ts",
          "status:enum(HELD|RELEASED|REFUNDED|DISPUTED):nn","releaseCondition:string"]),
        ("Wallet","wallets",[
          "ownerOrgId:uuid:ix","ownerUserId:uuid:ix","currency:string:nn","balance:decimal:nn",
          "availableBalance:decimal:nn","status:enum(ACTIVE|FROZEN|CLOSED):nn","lastTransactionAt:ts"]),
        ("ProviderCallback","provider_callbacks",[
          "provider:string:ix,nn","callbackType:string:nn","externalRef:string:ix","signature:string",
          "payload:json:nn","receivedAt:ts:nn","processedAt:ts",
          "status:enum(RECEIVED|PROCESSED|DUPLICATE|INVALID|FAILED):nn","error:text"]),
      ]),

 dict(name="ledger-service", port=8100, db="ledger_db", group="money",
      desc="Double-entry accounts, immutable postings, balances, statements",
      publishes=["JournalPosted"],
      entities=[
        ("Account","accounts",[
          "accountCode:string:uq,nn","name:string:nn",
          "accountType:enum(ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE):nn",
          "ownerOrgId:uuid:ix","ownerUserId:uuid:ix","currency:string:nn",
          "normalBalance:enum(DEBIT|CREDIT):nn","status:enum(ACTIVE|FROZEN|CLOSED):nn","parentAccountId:uuid"]),
        ("JournalEntry","journal_entries",[
          "entryNumber:string:uq,nn","description:string:nn","sourceEvent:string","sourceRef:string:ix",
          "postedAt:ts:nn","effectiveDate:date:nn","currency:string:nn","totalDebit:decimal:nn",
          "totalCredit:decimal:nn","balanced:bool:nn","reversalOfId:uuid","idempotencyKey:string:uq,nn"]),
        ("Posting","postings",[
          "journalEntryId:uuid:ix,nn","accountId:uuid:ix,nn","accountCode:string:nn",
          "direction:enum(DEBIT|CREDIT):nn","amount:decimal:nn","currency:string:nn",
          "postedAt:ts:nn","memo:string"]),
        ("AccountBalance","account_balances",[
          "accountId:uuid:uq,nn","accountCode:string:ix,nn","currency:string:nn",
          "debitTotal:decimal:nn","creditTotal:decimal:nn","balance:decimal:nn",
          "postingCount:long:nn","lastPostedAt:ts"]),
      ]),

 dict(name="payout-service", port=8101, db="payout_db", group="money",
      desc="Farmer settlements, bulk wage disbursement, fees, scheduling, fraud holds",
      publishes=["SettlementCreated","PayoutSent","PayoutHeld","PayoutFailed"],
      entities=[
        ("Settlement","settlements",[
          "settlementNumber:string:uq,nn","payeeOrgId:uuid:ix","payeeUserId:uuid:ix","orderId:uuid:ix",
          "grossAmount:decimal:nn","commission:decimal:nn","fees:decimal:nn","netAmount:decimal:nn",
          "currency:string:nn","periodStart:date","periodEnd:date","dueAt:ts",
          "status:enum(DRAFT|APPROVED|SCHEDULED|PAID|HELD|FAILED|CANCELLED):nn","approvedBy:uuid"]),
        ("PayoutBatch","payout_batches",[
          "batchNumber:string:uq,nn","farmId:uuid:ix","payoutType:enum(WAGE|SETTLEMENT|REFUND|BONUS):nn",
          "itemCount:int:nn","totalAmount:decimal:nn","currency:string:nn","scheduledFor:ts",
          "submittedAt:ts","completedAt:ts","createdBy:uuid",
          "status:enum(DRAFT|APPROVED|SUBMITTED|PROCESSING|COMPLETED|PARTIALLY_FAILED|FAILED):nn"]),
        ("PayoutItem","payout_items",[
          "batchId:uuid:ix","settlementId:uuid:ix","payeeType:enum(WORKER|FARMER|ORG|DRIVER):nn",
          "payeeId:uuid:ix,nn","payeeName:string","payeePhone:string","amount:decimal:nn","currency:string:nn",
          "paymentIntentId:uuid:ix","status:enum(PENDING|SENT|PAID|HELD|FAILED|REVERSED):nn",
          "failureReason:string","sentAt:ts","paidAt:ts","idempotencyKey:string:uq"]),
        ("PayoutHold","payout_holds",[
          "payoutItemId:uuid:ix","payeeId:uuid:ix,nn","reason:enum(FRAUD_CASE|KYC_INCOMPLETE|DISPUTE|MANUAL|SANCTIONS):nn",
          "fraudCaseId:uuid:ix","amount:decimal","heldAt:ts:nn","heldBy:uuid","releasedAt:ts",
          "releasedBy:uuid","status:enum(ACTIVE|RELEASED|ESCALATED):nn","notes:text"]),
      ]),

 dict(name="traceability-service", port=8102, db="traceability_db", group="platform",
      desc="Farm-to-fork lineage graph, certifications, QR pass",
      publishes=["TraceLinkAdded","QrPassIssued"],
      entities=[
        ("TraceBatch","trace_batches",[
          "batchCode:string:uq,nn","commodityCode:string:ix,nn","farmId:uuid:ix","plotId:uuid:ix",
          "seasonId:uuid:ix","harvestedOn:date","originCounty:string","currentHolderOrgId:uuid:ix",
          "quantityKg:decimal","status:enum(ACTIVE|CONSUMED|RECALLED):nn"]),
        ("TraceLink","trace_links",[
          "batchCode:string:ix,nn","sequence:int:nn",
          "nodeType:enum(PLOT|SEASON|INPUT|HARVEST|GRADING|STORAGE|TRANSPORT|BUYER|RETAIL):nn",
          "nodeRef:string:nn","occurredAt:ts:nn","actorOrgId:uuid","location:string",
          "latitude:decimal","longitude:decimal","attributes:json","evidenceUrl:string"]),
        ("QrPass","qr_passes",[
          "batchCode:string:ix,nn","passCode:string:uq,nn","qrUrl:string","issuedAt:ts:nn","expiresAt:ts",
          "scanCount:int:nn","lastScannedAt:ts","publicSummary:json","status:enum(ACTIVE|REVOKED|EXPIRED):nn"]),
        ("CertEvidence","cert_evidence",[
          "batchCode:string:ix","farmId:uuid:ix","certificationCode:string:nn","certificateNo:string",
          "issuedBy:string","issuedOn:date","expiresOn:date","documentUrl:string",
          "verified:bool:nn","verifiedAt:ts"]),
      ]),

 dict(name="notification-service", port=8103, db="notification_db", group="platform",
      desc="SMS, USSD, push, WhatsApp, email; EN/SW templating, delivery tracking, quiet hours",
      publishes=["NotificationSent","NotificationFailed"],
      entities=[
        ("NotificationTemplate","notification_templates",[
          "code:string:nn","channel:enum(SMS|USSD|PUSH|WHATSAPP|EMAIL):nn","locale:string:nn",
          "subject:string","body:text:nn","variables:string","active:bool:nn","revision:int:nn"]),
        ("Notification","notifications",[
          "recipientUserId:uuid:ix","recipientPhone:string:ix","recipientEmail:string",
          "channel:enum(SMS|USSD|PUSH|WHATSAPP|EMAIL):nn","templateCode:string:ix","locale:string:nn",
          "subject:string","body:text:nn","payload:json","priority:enum(LOW|NORMAL|HIGH|CRITICAL):nn",
          "scheduledFor:ts","sentAt:ts","deliveredAt:ts","failedAt:ts","failureReason:string",
          "providerRef:string","attempts:int:nn",
          "status:enum(QUEUED|SENDING|SENT|DELIVERED|FAILED|SUPPRESSED):nn","idempotencyKey:string:uq"]),
        ("UssdSession","ussd_sessions",[
          "sessionId:string:uq,nn","phoneNumber:string:ix,nn","serviceCode:string","currentMenu:string:nn",
          "menuStack:string","context:json","startedAt:ts:nn","lastInputAt:ts","endedAt:ts",
          "status:enum(ACTIVE|COMPLETED|TIMEOUT|ABORTED):nn","hops:int:nn"]),
        ("DeliveryReceipt","delivery_receipts",[
          "notificationId:uuid:ix,nn","provider:string:nn","providerRef:string:ix","statusCode:string",
          "statusText:string","receivedAt:ts:nn","raw:json"]),
      ]),

 dict(name="media-service", port=8104, db="media_db", group="platform",
      desc="Pre-signed uploads, image/video processing, thumbnails, EXIF/GPS extraction",
      publishes=["MediaUploaded","MediaProcessed"],
      entities=[
        ("MediaAsset","media_assets",[
          "storageKey:string:uq,nn","originalFilename:string","contentType:string:nn","sizeBytes:long:nn",
          "checksum:string:ix","ownerUserId:uuid:ix","context:string:ix","contextRef:string:ix",
          "width:int","height:int","durationSeconds:int","perceptualHash:string:ix",
          "exifTimestamp:ts","exifLatitude:decimal","exifLongitude:decimal","publicUrl:string",
          "virusScanned:bool:nn","virusClean:bool:nn",
          "status:enum(PENDING|UPLOADED|PROCESSING|READY|QUARANTINED|DELETED):nn"]),
        ("UploadTicket","upload_tickets",[
          "storageKey:string:uq,nn","uploadUrl:text:nn","method:string:nn","requestedBy:uuid:nn",
          "contentType:string","maxSizeBytes:long","expiresAt:ts:nn","consumedAt:ts",
          "status:enum(ISSUED|CONSUMED|EXPIRED):nn"]),
        ("MediaVariant","media_variants",[
          "assetId:uuid:ix,nn","variantName:string:nn","storageKey:string:nn","width:int","height:int",
          "sizeBytes:long","contentType:string","publicUrl:string"]),
      ]),

 dict(name="search-service", port=8105, db="search_db", group="platform",
      desc="Search indexes kept fresh from domain events (listings, farms, workers, produce)",
      publishes=["IndexUpdated"],
      entities=[
        ("SearchDocument","search_documents",[
          "indexName:string:ix,nn","docId:string:ix,nn","docType:string:nn","title:string",
          "body:text","keywords:text","county:string:ix","commodityCode:string:ix",
          "latitude:decimal","longitude:decimal","boost:decimal","payload:json","indexedAt:ts:nn",
          "status:enum(ACTIVE|STALE|DELETED):nn"]),
        ("IndexJob","index_jobs",[
          "indexName:string:ix,nn","jobType:enum(FULL_REBUILD|INCREMENTAL|DELETE):nn","sourceEvent:string",
          "documentsProcessed:int:nn","startedAt:ts:nn","completedAt:ts",
          "status:enum(QUEUED|RUNNING|COMPLETED|FAILED):nn","error:text"]),
      ]),

 dict(name="analytics-service", port=8106, db="analytics_db", group="platform",
      desc="Event sink, data marts, dashboards API, scheduled reports, exports",
      publishes=["ReportGenerated"],
      entities=[
        ("MetricSnapshot","metric_snapshots",[
          "metricKey:string:ix,nn","dimension:string:ix","dimensionValue:string:ix","periodStart:ts:nn",
          "periodEnd:ts:nn","granularity:enum(HOUR|DAY|WEEK|MONTH|SEASON):nn","value:decimal:nn",
          "unit:string","computedAt:ts:nn"]),
        ("Report","reports",[
          "code:string:uq,nn","name:string:nn","description:text","category:string:ix",
          "querySpec:json:nn","schedule:string","format:enum(JSON|CSV|PDF|XLSX):nn","enabled:bool:nn",
          "ownerUserId:uuid"]),
        ("ReportRun","report_runs",[
          "reportId:uuid:ix,nn","reportCode:string:ix","triggeredBy:uuid","startedAt:ts:nn","completedAt:ts",
          "rowCount:int","outputUrl:string","parameters:json",
          "status:enum(QUEUED|RUNNING|COMPLETED|FAILED):nn","error:text"]),
        ("DashboardWidget","dashboard_widgets",[
          "dashboardCode:string:ix,nn","title:string:nn","widgetType:enum(KPI|LINE|BAR|PIE|TABLE|MAP):nn",
          "metricKey:string","querySpec:json","position:int:nn","width:int","config:json"]),
      ]),

 dict(name="audit-service", port=8107, db="audit_db", group="platform",
      desc="Tamper-evident hash-chained audit log of privileged actions across all services",
      publishes=[],
      entities=[
        ("AuditRecord","audit_records",[
          "sequence:long:nn","serviceName:string:ix,nn","actorUserId:uuid:ix","actorRole:string",
          "action:string:ix,nn","resourceType:string:ix","resourceId:string:ix",
          "outcome:enum(SUCCESS|FAILURE|DENIED):nn","occurredAt:ts:nn","ipAddress:string","userAgent:string",
          "details:json","previousHash:string","recordHash:string:uq,nn"]),
        ("AuditAnchor","audit_anchors",[
          "anchorSequence:long:nn","chainHash:string:nn","recordCount:long:nn","anchoredAt:ts:nn",
          "externalRef:string"]),
      ]),
]
