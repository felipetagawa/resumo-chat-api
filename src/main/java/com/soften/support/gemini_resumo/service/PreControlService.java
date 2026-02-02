package com.soften.support.gemini_resumo.service;

import com.soften.support.gemini_resumo.models.dtos.PreTimeDto;
import com.soften.support.gemini_resumo.models.entities.PreEntity;
import com.soften.support.gemini_resumo.repositorys.PreControlRepositoy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class PreControlService {

    private final PreControlRepositoy preControlRepositoy;

    public PreControlService(PreControlRepositoy preControlRepositoy) {
        this.preControlRepositoy = preControlRepositoy;
    }

    @Transactional
    public  PreTimeDto create(PreTimeDto dto) {
        if (dto.id() != null) throw new IllegalArgumentException("id must be null on create");

        var entity = new PreEntity();
        entity.setName(dto.name().trim());
        entity.setNameClient(dto.nameClient().trim());
        entity.setDate(dto.date());
        entity.setTime(dto.time());

        var saved = preControlRepositoy.save(entity);
        return toDto(saved);
    }

    @Transactional
    public List<PreTimeDto> List() {
        return preControlRepositoy.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<PreTimeDto> FindByName(String name) {
        return preControlRepositoy.findByName(name.trim())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<PreTimeDto> FindByDate(Date date) {
        return preControlRepositoy.findByDate(date)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<PreTimeDto> FindByNameAndDate(String name, Date date) {
        return preControlRepositoy.findByNameAndDate(name.trim(), date)
                .stream().map(this::toDto).toList();
    }

    private PreTimeDto toDto(PreEntity e) {
        return new PreTimeDto(
                e.getId(),
                e.getName(),
                e.getNameClient(),
                e.getDate(),
                e.getTime()
        );
    }
}
