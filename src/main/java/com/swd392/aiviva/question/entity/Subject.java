package com.swd392.aiviva.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subject")
public class Subject implements Serializable {
    @Id
    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "subject_code", nullable = false)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "description")
    private String description;
}
