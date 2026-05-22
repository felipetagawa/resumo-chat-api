package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.models.entities.PreEntity;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PreControlService {

    private static final String PRE_PERSISTENCE_DISABLED_MESSAGE = "PRE profile persistence is disabled";

    private final PreControlRepositoy repo;

    public PreControlService(PreControlRepositoy repo) {
        this.repo = repo;
    }

    @Transactional
    public PreTimeDto create(PreTimeDto dto) {
        throw new ResponseStatusException(HttpStatus.GONE, PRE_PERSISTENCE_DISABLED_MESSAGE);
    }

    public Page<PreTimeDto> findAll(
            String name,
            Boolean negociation,
            LocalDate dateExact,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        Specification<PreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.trim().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase().trim() + "%"));
            }

            if (negociation != null) {
                predicates.add(cb.equal(root.get("negociation"), negociation));
            }

            if (dateExact != null) {
                LocalDateTime startOfDay = dateExact.atStartOfDay();
                LocalDateTime endOfDay = dateExact.atTime(LocalTime.MAX);
                predicates.add(cb.between(root.get("date"), startOfDay, endOfDay));
            } else {
                if (dateFrom != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("date"), dateFrom.atStartOfDay()));
                }
                if (dateTo != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("date"), dateTo.atTime(LocalTime.MAX)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repo.findAll(spec, pageable).map(this::toDto);
    }

    private String sanitizeName(String v) {
        if (v == null || v.trim().isBlank()) throw new IllegalArgumentException("Name is required");
        return v.trim();
    }

    private String sanitizeClient(String v) {
        return v == null ? "" : v.trim();
    }

    private PreTimeDto toDto(PreEntity e) {
        return new PreTimeDto(
                e.getId(),
                e.getName(),
                e.getNameClient(),
                e.getDate(),
                e.getTime(),
                e.isNegociation()
        );
    }
}
