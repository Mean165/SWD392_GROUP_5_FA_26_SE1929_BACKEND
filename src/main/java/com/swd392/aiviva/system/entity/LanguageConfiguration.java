package com.swd392.aiviva.system.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.system.enums.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "language_configuration")
public class LanguageConfiguration extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private Language language;

    private String displayName;
}

