package com.inventory.repository;

import com.inventory.entity.SampleReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface SampleReturnRepository extends JpaRepository<SampleReturn, Long>, JpaSpecificationExecutor<SampleReturn> {

    @Override
    @EntityGraph(attributePaths = {"sampleIssue", "sampleIssue.representative"})
    Page<SampleReturn> findAll(Specification<SampleReturn> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"sampleIssue", "sampleIssue.representative", "items", "items.product"})
    @Query("select r from SampleReturn r where r.id = :id")
    Optional<SampleReturn> findDetailedById(@Param("id") Long id);

    /** Returns of one sample, oldest first. */
    @Query("select r from SampleReturn r where r.sampleIssue.id = :sampleId order by r.returnDate, r.id")
    List<SampleReturn> findBySample(@Param("sampleId") Long sampleId);

    /** Per issued line: [line id, quantity returned so far, amount returned so far]. */
    @Query("""
            select i.sampleIssueItem.id, sum(i.quantity), sum(i.lineTotal)
            from SampleReturnItem i
            where i.sampleReturn.sampleIssue.id = :sampleId
            group by i.sampleIssueItem.id
            """)
    List<Object[]> returnedPerLine(@Param("sampleId") Long sampleId);

    final class Filters {

        private Filters() {
        }

        /** Return number, sample number or representative name. */
        public static Specification<SampleReturn> matches(String search) {
            if (search == null || search.isBlank()) return null;
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            return (root, q, cb) -> {
                var sample = root.join("sampleIssue");
                var rep = sample.join("representative");
                return cb.or(
                        cb.like(cb.lower(root.get("returnNumber")), term),
                        cb.like(cb.lower(sample.get("sampleNumber")), term),
                        cb.like(cb.lower(rep.get("name")), term));
            };
        }

        public static Specification<SampleReturn> sample(Long sampleId) {
            return sampleId == null ? null : (root, q, cb) -> cb.equal(root.get("sampleIssue").get("id"), sampleId);
        }

        public static Specification<SampleReturn> representative(Long representativeId) {
            return representativeId == null ? null
                    : (root, q, cb) -> cb.equal(root.get("sampleIssue").get("representative").get("id"), representativeId);
        }

        public static Specification<SampleReturn> dateFrom(LocalDate from) {
            return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("returnDate"), from);
        }

        public static Specification<SampleReturn> dateTo(LocalDate to) {
            return to == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("returnDate"), to);
        }
    }
}
