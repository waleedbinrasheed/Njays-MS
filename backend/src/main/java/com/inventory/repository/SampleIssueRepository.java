package com.inventory.repository;

import com.inventory.entity.SampleIssue;
import com.inventory.entity.SampleIssueItem;
import com.inventory.entity.SampleStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public interface SampleIssueRepository extends JpaRepository<SampleIssue, Long>, JpaSpecificationExecutor<SampleIssue> {

    @Override
    @EntityGraph(attributePaths = {"representative"})
    Page<SampleIssue> findAll(Specification<SampleIssue> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"representative", "items", "items.product", "cancelledBy"})
    @Query("select s from SampleIssue s where s.id = :id")
    Optional<SampleIssue> findDetailedById(@Param("id") Long id);

    /** Serialises returns and cancellation of the same sample, so returns can never exceed what is out. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SampleIssue s where s.id = :id")
    Optional<SampleIssue> lockById(@Param("id") Long id);

    boolean existsByRepresentativeId(Long representativeId);

    final class Filters {

        private Filters() {
        }

        /** Sample number or representative name/area. */
        public static Specification<SampleIssue> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> {
                var rep = root.join("representative");
                return cb.or(
                        cb.like(cb.lower(root.get("sampleNumber")), term),
                        cb.like(cb.lower(rep.get("name")), term),
                        cb.like(cb.lower(cb.coalesce(rep.get("area"), "")), term));
            };
        }

        public static Specification<SampleIssue> representative(Long representativeId) {
            return representativeId == null ? null
                    : (root, q, cb) -> cb.equal(root.get("representative").get("id"), representativeId);
        }

        public static Specification<SampleIssue> product(Long productId) {
            if (productId == null) return null;
            return (root, q, cb) -> {
                Subquery<Long> sub = q.subquery(Long.class);
                var item = sub.from(SampleIssueItem.class);
                sub.select(item.get("id")).where(
                        cb.equal(item.get("sampleIssue"), root),
                        cb.equal(item.get("product").get("id"), productId));
                return cb.exists(sub);
            };
        }

        public static Specification<SampleIssue> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("issueDate"), from);
        }

        public static Specification<SampleIssue> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("issueDate"), to);
        }

        public static Specification<SampleIssue> status(SampleStatus status) {
            return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
        }
    }
}
