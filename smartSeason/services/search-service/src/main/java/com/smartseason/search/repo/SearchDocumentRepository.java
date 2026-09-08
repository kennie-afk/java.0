package com.smartseason.search.repo;

import com.smartseason.search.domain.SearchDocument;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchDocumentRepository extends JpaRepository<SearchDocument, UUID> {

    Optional<SearchDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SearchDocument> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<SearchDocument> findAllByIndexNameAndTenantId(String indexName, UUID tenantId, Pageable pageable);
    Page<SearchDocument> findAllByDocIdAndTenantId(String docId, UUID tenantId, Pageable pageable);
    Page<SearchDocument> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
    Page<SearchDocument> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
}
