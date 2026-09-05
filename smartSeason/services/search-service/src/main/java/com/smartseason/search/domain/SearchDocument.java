package com.smartseason.search.domain;

import com.smartseason.search.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "search_documents", indexes = {
        @Index(name = "ix_search_documents_index_name", columnList = "index_name"),
        @Index(name = "ix_search_documents_doc_id", columnList = "doc_id"),
        @Index(name = "ix_search_documents_county", columnList = "county"),
        @Index(name = "ix_search_documents_commodity_code", columnList = "commodity_code")
})
public class SearchDocument extends BaseEntity {

    @Column(name = "index_name", nullable = false)
    private String indexName;

    @Column(name = "doc_id", nullable = false)
    private String docId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "title")
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "county")
    private String county;

    @Column(name = "commodity_code")
    private String commodityCode;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "boost")
    private BigDecimal boost;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getCounty() { return county; }
    public void setCounty(String county) { this.county = county; }

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getBoost() { return boost; }
    public void setBoost(BigDecimal boost) { this.boost = boost; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ACTIVE, STALE, DELETED }

}
