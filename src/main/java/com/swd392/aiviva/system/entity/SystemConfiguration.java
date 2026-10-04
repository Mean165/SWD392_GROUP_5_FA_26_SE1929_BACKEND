package com.swd392.aiviva.system.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import jakarta.persistence.Entity;
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
@Table(name = "system_configuration")
public class SystemConfiguration extends BaseEntity {

    private String key;
    private String value;
    private String description;
}

