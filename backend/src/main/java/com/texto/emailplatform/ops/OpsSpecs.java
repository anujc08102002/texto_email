package com.texto.emailplatform.ops;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class OpsSpecs {

    private OpsSpecs() {
    }

    static <T> Specification<T> tenantIdEquals(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenantId"), tenantId);
    }

    static <T> Specification<T> statusEquals(String status) {
        return (root, query, cb) -> blank(status) ? cb.conjunction() : cb.equal(root.get("status"), status.trim());
    }

    static <T> Specification<T> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> between(cb, root.get("createdAt"), from, to);
    }

    static <T> Specification<T> receivedBetween(Instant from, Instant to) {
        return (root, query, cb) -> between(cb, root.get("receivedAt"), from, to);
    }

    static Predicate between(CriteriaBuilder cb, Path<Instant> path, Instant from, Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(path, to));
        }
        if (predicates.isEmpty()) {
            return cb.conjunction();
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    static <T> Specification<T> containsIgnoreCase(String attribute, String value) {
        return (root, query, cb) -> {
            if (blank(value)) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get(attribute)), "%" + value.trim().toLowerCase(Locale.ROOT) + "%");
        };
    }

    static <T> Specification<T> equalsIgnoreCase(String attribute, String value) {
        return (root, query, cb) -> {
            if (blank(value)) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get(attribute)), value.trim().toLowerCase(Locale.ROOT));
        };
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
