package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.models.entities.PreEntity;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import jakarta.persistence.criteria.Predicate;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PreControlService {

    private final PreControlRepositoy repo;

    public PreControlService(PreControlRepositoy repo) {
        this.repo = repo;
    }

    @Transactional
    public PreTimeDto create(PreTimeDto dto) {
        if (dto.id() != null) throw new IllegalArgumentException("id must be null on create");

        var entity = new PreEntity();
        entity.setName(sanitizeName(dto.name()));
        entity.setNameClient(sanitizeClient(dto.nameClient()));
        entity.setDate(dto.date() != null ? dto.date() : LocalDateTime.now());
        entity.setTime(dto.time() != null ? dto.time() : "00:00");
        entity.setNegociation(dto.negociation());

        var saved = repo.save(entity);
        return toDto(saved);
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