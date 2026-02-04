package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.models.entities.PreEntity;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        entity.setDate(dto.date());
        entity.setTime(dto.time());
        entity.setNegociation(dto.negociation());

        var saved = repo.save(entity);
        return toDto(saved);
    }

    @Transactional
    public List<PreTimeDto> List() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByName(String name) {
        return repo.findByName(sanitizeName(name)).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByDate(LocalDate date) {
        return repo.findByDate(date).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByNameAndDate(String name, LocalDate date) {
        return repo.findByNameAndDate(sanitizeName(name), date).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByNameAndNegociation(String name, boolean negociation) {
        return repo.findByNameAndNegociation(sanitizeName(name), negociation).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByDateAndNegociation(LocalDate date, boolean negociation) {
        return repo.findByDateAndNegociation(date, negociation).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByNameAndDateAndNegociation(String name, LocalDate date, boolean negociation) {
        return repo.findByNameAndDateAndNegociation(sanitizeName(name), date, negociation).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> FindByNegociation(boolean negociation) {
        return repo.findByNegociation(negociation).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> findByPeriod(LocalDate from, LocalDate to) {
        var p = normalizePeriod(from, to);
        return repo.findByDateBetween(p.from(), p.to()).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> findByNameAndPeriod(String name, LocalDate from, LocalDate to) {
        var p = normalizePeriod(from, to);
        return repo.findByNameAndDateBetween(sanitizeName(name), p.from(), p.to()).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> findByPeriodAndNegociation(LocalDate from, LocalDate to, boolean negociation) {
        var p = normalizePeriod(from, to);
        return repo.findByDateBetweenAndNegociation(p.from(), p.to(), negociation).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<PreTimeDto> findByNameAndPeriodAndNegociation(String name, LocalDate from, LocalDate to, boolean negociation) {
        var p = normalizePeriod(from, to);
        return repo.findByNameAndDateBetweenAndNegociation(sanitizeName(name), p.from(), p.to(), negociation)
                .stream().map(this::toDto).toList();
    }

    private String sanitizeName(String v) {
        var s = v == null ? "" : v.trim();
        if (s.isBlank()) throw new IllegalArgumentException("name is required");
        return s;
    }

    private String sanitizeClient(String v) {
        var s = v == null ? "" : v.trim();
        return s;
    }

    private Period normalizePeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            throw new IllegalArgumentException("period requires dateFrom or dateTo");
        }
        LocalDate f = (from != null) ? from : to;
        LocalDate t = (to != null) ? to : from;
        if (f.isAfter(t)) {
            var tmp = f; f = t; t = tmp;
        }
        return new Period(f, t);
    }

    private record Period(LocalDate from, LocalDate to) {}

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