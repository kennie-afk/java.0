
export type Role = 'BUYER' | 'SELLER' | 'AGENT' | 'LANDLORD' | 'ADMIN'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  email: string
  role: Role
  verified: boolean
}

export interface UserResponse {
  id: string
  fullName: string
  email: string
  phone?: string
  role: Role
  verified: boolean
  active: boolean
  superAdmin?: boolean
  profileImage?: string
  accountType?: 'INDIVIDUAL' | 'COMPANY'
  companyName?: string
  companyRegNumber?: string
  kraPin?: string
  paybillNumber?: string
  tillNumber?: string
  bankAccountName?: string
  bankAccountNumber?: string
  bankName?: string
  bankBranch?: string
  bankSwiftCode?: string
  preferredPayoutMethod?: 'MPESA' | 'PAYBILL' | 'TILL' | 'BANK'
  payoutPhone?: string
  createdAt: string
}

export type PropertyType = 'HOUSE' | 'APARTMENT' | 'LAND' | 'COMMERCIAL' | 'TOWNHOUSE' | 'STUDIO' | 'VILLA'
export type ListingType = 'SALE' | 'RENT'
export type ListingStatus = 'DRAFT' | 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'SOLD' | 'RENTED' | 'WITHDRAWN'

export interface PropertyResponse {
  id: string
  sellerId: string
  title: string
  description?: string
  propertyType: PropertyType
  listingType: ListingType
  status: ListingStatus
  county: string
  subCounty?: string
  city?: string
  locationDescription?: string
  latitude?: number
  longitude?: number
  price: number
  bedrooms?: number
  bathrooms?: number
  areaSqm?: number
  yearBuilt?: number
  imageUrls: string[]
  sellerIdentityVerified: boolean
  propertyOwnershipVerified: boolean
  fullyTrusted: boolean
  duplicateParcelFlag?: boolean
  parcelNumber?: string
  titleDeedNumber?: string
  viewCount: number
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type VerifStatus = 'DRAFT'|'SUBMITTED'|'AI_SCREENING'|'HUMAN_REVIEW'|'APPROVED'|'REJECTED'|'REQUIRES_RESUBMISSION'|'EXPIRED'

export interface IdentityDocumentResponse {
  id: string
  documentUrl: string
  documentCategory?: string
  aiAuthenticityScore?: number
  aiTamperDetected?: boolean
  aiSignatureDetected?: boolean
  aiSealDetected?: boolean
  aiMetadataClean?: boolean
  aiFontConsistency?: boolean
  aiScreeningNotes?: string
  extractedIdNumber?: string
  aiDetectedCategory?: string
  aiCategoryConfidence?: number
  aiCategoryMismatch?: boolean
  aiSideDetected?: string
  humanVerified?: boolean
  humanReviewNotes?: string
  uploadedAt?: string
}

export type BadgeLevel = 'NONE' | 'BASIC' | 'VERIFIED' | 'GOLD'

export interface IdentityVerificationResponse {
  id: string
  userId: string
  status: VerifStatus
  identityScore: number
  badgeLevel?: BadgeLevel
  rejectionReason?: string
  resubmissionNotes?: string
  expiresAt?: string
  expired: boolean
  createdAt: string
  updatedAt: string
  documents: IdentityDocumentResponse[]
  missingRequiredDocuments?: string[]
  fraudStrikeCount: number
  permanentlyBanned: boolean
  faceMatchScore?: number
  faceMatchSource?: string
  faceMatchPassed?: boolean
}

export type OwnershipVerifStatus =
  | 'DRAFT' | 'SUBMITTED' | 'AI_SCREENING' | 'MINISTRY_LANDS_CHECK' | 'ENCUMBRANCE_CHECK'
  | 'LEGAL_REVIEW' | 'HUMAN_REVIEW' | 'APPROVED' | 'REJECTED' | 'REQUIRES_RESUBMISSION'

export type OwnershipPropertyType = 'FREEHOLD' | 'LEASEHOLD' | 'SECTIONAL_TITLE' | 'AGRICULTURAL' | 'COMMERCIAL'

export interface DocumentRequirementResponse {
  documentCategory: string
  isMandatory: boolean
  description?: string
  kenyaLawRef?: string
  uploaded: boolean
}

export interface OwnershipDocumentResponse {
  id: string
  documentCategory: string
  documentUrl: string
  isRequired?: boolean
  humanReviewedAt?: string
  lcAdvocateStampPresent?: boolean
  lcAdvocateSignaturePresent?: boolean
  lcCommissionerOathsPresent?: boolean
  lcOfficialSealPresent?: boolean
  lcOwnerSignaturePresent?: boolean
  lcWitnessSignaturesPresent?: boolean
  lcDatePresent?: boolean
  lcParcelNumberMatches?: boolean
  lcOriginalDocumentConfirmed?: boolean
  aiAuthenticityScore?: number
  aiTamperDetected?: boolean
  aiAlterationDetected?: boolean
  aiFontConsistency?: boolean
  aiDateSequenceValid?: boolean
  aiMetadataClean?: boolean
  aiSignatureDetected?: boolean
  aiSealDetected?: boolean
  aiScreeningNotes?: string
  aiDetectedCategory?: string
  aiCategoryConfidence?: number
  aiCategoryMismatch?: boolean
  aiSideDetected?: string
  aiExtractedFields?: string
  humanLegalApproved?: boolean
  humanReviewNotes?: string
  uploadedAt?: string
}

export interface OwnershipVerificationResponse {
  id: string
  propertyId: string
  sellerIdentityVerificationId: string
  status: OwnershipVerifStatus
  propertyType: OwnershipPropertyType
  county?: string
  parcelNumber?: string
  titleDeedNumber?: string
  lrNumber?: string
  ownershipScore?: number
  ministryLandsConfirmed?: boolean
  encumbranceClear?: boolean
  rejectionReason?: string
  createdAt: string
  updatedAt: string
  documents: OwnershipDocumentResponse[]
  missingDocuments?: DocumentRequirementResponse[]
  allRequiredDocuments?: DocumentRequirementResponse[]
}

export interface TrustStatusResponse {
  userId: string
  identityVerified: boolean
  identityStatus?: VerifStatus
  badgeLevel?: BadgeLevel
  identityScore?: number
  identityExpired: boolean
  identityExpiresAt?: string
  propertyId?: string
  ownershipVerified: boolean
  ownershipStatus?: OwnershipVerifStatus
  ownershipScore?: number
  ministryLandsConfirmed?: boolean
  encumbranceClear?: boolean
  fullyTrusted: boolean
  message?: string
}

export type ViewingStatus = 'PENDING_FEE'|'REQUESTED'|'CONFIRMED'|'COMPLETED'|'CANCELLED'|'NO_SHOW'

export interface ViewingResponse {
  id: string
  propertyId: string
  buyerId: string
  sellerId: string
  scheduledAt: string
  completedAt?: string
  status: ViewingStatus
  notes?: string
  buyerConfirmed: boolean
  sellerConfirmed: boolean
  viewingFeePaymentId?: string
  viewingFeeStatus?: string
  cancelledBy?: string
  cancellationReason?: string
  createdAt?: string
  updatedAt?: string
}

export type PaymentStatus = 'PENDING'|'STK_PUSHED'|'COMPLETED'|'FAILED'|'CANCELLED'|'REFUNDED'
export type PaymentType = 'FULL_PAYMENT'|'DEPOSIT'|'VIEWING_FEE'|'COMMISSION'|'PROFILE_ACCESS'

export interface PaymentResponse {
  id: string
  buyerId: string
  sellerId: string
  propertyId: string
  paymentType: PaymentType
  status: PaymentStatus
  currency: string
  phoneNumber: string
  amount: number
  mpesaCheckoutRequestId?: string
  mpesaReceiptNumber?: string
  escrowReleased: boolean
  failureReason?: string
  createdAt?: string
  updatedAt?: string
}

export interface PaymentAuditResponse {
  id: string
  paymentId: string
  revenueId?: string
  eventType: string
  previousStatus?: string
  newStatus: string
  actorId?: string
  actorRole: string
  actorIp?: string
  mpesaReceipt?: string
  amountKes: number
  detail?: string
  createdAt: string
}

export interface PaymentReceiptResponse {
  id: string
  receiptNumber: string
  paymentId: string
  buyerId: string
  sellerId: string
  propertyId: string
  paymentType: string
  grossAmount: number
  platformFee: number
  sellerPayout: number
  currency: string
  mpesaReceipt: string
  payerPhone: string
  issuedAt: string
}

export interface RevenueSummaryResponse {
  totalPlatformFees: number
  viewingFeeRevenue: number
  commissionRevenue: number
  thisMonthRevenue: number
  lastMonthRevenue: number
  totalTransactions?: number
  thisMonthTransactions?: number
  currency: string
}

export interface RevenueResponse {
  id: string
  paymentId: string
  buyerId: string
  sellerId: string
  propertyId: string
  revenueType: string
  grossAmount: number
  platformFee: number
  sellerPayout: number
  feePercentage: number
  currency: string
  status: string
  payoutMethod?: string
  payoutFailureReason?: string
  releasedByAdminId?: string
  releaseNotes?: string
  createdAt: string
}

export interface ReviewResponse {
  id: string
  reviewerId: string
  sellerId: string
  propertyId: string
  paymentId: string
  rating: number
  comment?: string
  verified: boolean
  createdAt: string
}

export interface SellerRatingResponse {
  sellerId: string
  averageRating: number
  reviewCount: number
}

export interface ReviewAdminStatsResponse {
  totalReviews: number
  visibleReviews: number
  hiddenReviews: number
  averageRating: number
  ratingDistribution: { rating: number; count: number }[]
}

export type ReportTargetType = 'LISTING' | 'USER' | 'REVIEW'
export type ReportReason = 'FAKE_LISTING' | 'SCAM_AGENT' | 'DUPLICATE_LISTING' | 'OFF_PLATFORM_PAYMENT_REQUEST' | 'FAKE_REVIEW' | 'OTHER'
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'

export interface ReportResponse {
  id: string
  reporterId: string
  targetType: ReportTargetType
  targetId: string
  reason: ReportReason
  details?: string
  status: ReportStatus
  adminNotes?: string
  resolvedBy?: string
  resolvedAt?: string
  createdAt: string
  updatedAt: string
}

export type AgentApplicationStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export interface AgentApplicationResponse {
  id: string
  userId: string
  status: AgentApplicationStatus
  businessName?: string
  businessDocUrl: string
  rejectionReason?: string
  reviewedBy?: string
  reviewedAt?: string
  createdAt: string
  updatedAt: string
}

export type NotificationCategory =
  | 'ACCOUNT' | 'VERIFICATION' | 'PAYMENT' | 'VIEWING'
  | 'PROPERTY' | 'TENANCY' | 'MAINTENANCE'

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SUPPRESSED'

export interface NotificationResponse {
  id: string
  category: NotificationCategory
  templateCode: string
  subject?: string
  body?: string
  entityType?: string
  entityId?: string
  actionUrl?: string
  read: boolean
  createdAt: string
  readAt?: string
}

export interface UnreadCountResponse {
  unread: number
}

export interface NotificationPreferenceResponse {
  category: NotificationCategory
  emailEnabled: boolean
  smsEnabled: boolean
  inAppEnabled: boolean
}

export interface AdminNotificationResponse {
  id: string
  userId: string
  recipientEmail?: string
  channel: 'EMAIL' | 'SMS' | 'IN_APP'
  category: NotificationCategory
  templateCode: string
  subject?: string
  status: NotificationStatus
  attempts: number
  lastError?: string
  sourceEventType?: string
  sourceEventId?: string
  createdAt: string
  nextAttemptAt?: string
  sentAt?: string
}

export type UnitStatus = 'VACANT' | 'OCCUPIED' | 'UNDER_MAINTENANCE' | 'RESERVED'
export type LeaseStatus = 'DRAFT' | 'ACTIVE' | 'ENDED' | 'TERMINATED' | 'RENEWED'
export type PaymentFrequency = 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY'

export interface UnitResponse {
  id: string
  propertyId: string
  landlordId: string
  label: string
  unitType?: string
  bedrooms?: number
  bathrooms?: number
  sizeSqm?: number
  rentAmount: number
  depositAmount?: number
  status: UnitStatus
  notes?: string
  activeLeaseId?: string
  activeTenantName?: string
  createdAt: string
}

export interface TenantRecord {
  id: string
  landlordId: string
  userId?: string
  fullName: string
  phone: string
  email?: string
  nationalId?: string
  emergencyName?: string
  emergencyPhone?: string
  hasActiveLease: boolean
  createdAt: string
}

export interface LeaseResponse {
  id: string
  unitId: string
  unitLabel?: string
  propertyId?: string
  tenantId: string
  tenantName?: string
  tenantPhone?: string
  landlordId: string
  startDate: string
  endDate?: string
  rentAmount: number
  depositAmount: number
  depositHeld: number
  managementFeePct: number
  billingDay: number
  paymentFrequency: PaymentFrequency
  noticePeriodDays: number
  status: LeaseStatus
  terminatedReason?: string
  terminatedAt?: string
  createdAt: string
}

export type InvoiceStatus = 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'WRITTEN_OFF'
export type RentPaymentMethod = 'MPESA_STK' | 'MPESA_PAYBILL' | 'BANK' | 'CASH'

export interface InvoiceResponse {
  id: string
  leaseId: string
  unitId: string
  unitLabel?: string
  tenantId: string
  tenantName?: string
  tenantPhone?: string
  invoiceNumber: string
  periodStart: string
  periodEnd: string
  dueDate: string
  amountDue: number
  amountPaid: number
  balance: number
  status: InvoiceStatus
  createdAt: string
}

export interface RentPaymentResponse {
  id: string
  invoiceId: string
  paymentId?: string
  amount: number
  method: RentPaymentMethod
  status: 'PENDING' | 'CONFIRMED' | 'FAILED'
  mpesaReceipt?: string
  note?: string
  createdAt: string
  paidAt?: string
}

export interface PortfolioSummaryResponse {
  totalUnits: number
  occupiedUnits: number
  vacantUnits: number
  underMaintenanceUnits: number
  tenants: number
  activeLeases: number
  monthlyRentRoll: number
  occupancyRate: number
  outstandingRent: number
  overdueInvoices: number
  openMaintenance: number
}

export type MaintenanceStatus = 'OPEN' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REJECTED'
export type MaintenancePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface MaintenanceResponse {
  id: string
  unitId: string
  unitLabel?: string
  leaseId?: string
  tenantId?: string
  tenantName?: string
  landlordId: string
  reference: string
  category: string
  priority: MaintenancePriority
  title: string
  description: string
  imageUrls: string[]
  status: MaintenanceStatus
  raisedByRole: 'TENANT' | 'LANDLORD'
  assignedTo?: string
  resolutionNotes?: string
  cost?: number
  createdAt: string
  acknowledgedAt?: string
  resolvedAt?: string
  closedAt?: string
}
