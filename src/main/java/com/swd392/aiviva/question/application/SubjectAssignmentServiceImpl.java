package com.swd392.aiviva.question.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.question.dto.response.SubjectAssignmentRecord;
import com.swd392.aiviva.user.service.UserService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectAssignmentServiceImpl implements SubjectAssignmentService {
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final UserService users;

    public SubjectAssignmentServiceImpl(JdbcTemplate jdbc, ObjectMapper mapper, UserService users) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectAssignmentRecord> findAll() {
        return jdbc.query("SELECT subject_id, subject_code, subject_name, assigned_lecturer_ids_json::text "
                        + "FROM subject ORDER BY subject_name, subject_code",
                (rs, row) -> record(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectAssignmentRecord> findForLecturer(UUID lecturerId) {
        users.getLecturer(lecturerId);
        return jdbc.query("SELECT subject_id, subject_code, subject_name, assigned_lecturer_ids_json::text "
                        + "FROM subject WHERE assigned_lecturer_ids_json @> jsonb_build_array(?::text) "
                        + "ORDER BY subject_name, subject_code",
                (rs, row) -> record(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                lecturerId.toString());
    }

    @Override
    @Transactional
    public SubjectAssignmentRecord addLecturers(UUID subjectId, List<UUID> lecturerIds) {
        if (lecturerIds == null || lecturerIds.isEmpty()) {
            throw new BusinessException("At least one Lecturer ID is required");
        }
        LinkedHashSet<UUID> requested = new LinkedHashSet<>(lecturerIds);
        if (requested.contains(null)) throw new BusinessException("Lecturer IDs cannot be null");
        SubjectAssignmentRecord current = lockSubject(subjectId);
        for (UUID lecturerId : requested) users.getLecturer(lecturerId);
        LinkedHashSet<UUID> updatedIds = new LinkedHashSet<>(current.lecturerIds());
        updatedIds.addAll(requested);
        save(subjectId, List.copyOf(updatedIds));
        return new SubjectAssignmentRecord(current.subjectId(), current.subjectCode(), current.subjectName(),
                List.copyOf(updatedIds));
    }

    @Override
    @Transactional
    public SubjectAssignmentRecord removeLecturer(UUID subjectId, UUID lecturerId) {
        SubjectAssignmentRecord current = lockSubject(subjectId);
        List<UUID> updatedIds = current.lecturerIds().stream()
                .filter(id -> !id.equals(lecturerId))
                .toList();
        save(subjectId, updatedIds);
        return new SubjectAssignmentRecord(current.subjectId(), current.subjectCode(), current.subjectName(), updatedIds);
    }

    private SubjectAssignmentRecord lockSubject(UUID subjectId) {
        List<SubjectAssignmentRecord> rows = jdbc.query("SELECT subject_id, subject_code, subject_name, "
                        + "assigned_lecturer_ids_json::text FROM subject WHERE subject_id=? FOR UPDATE",
                (rs, row) -> record(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                subjectId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Subject not found");
        return rows.get(0);
    }

    private void save(UUID subjectId, List<UUID> lecturerIds) {
        String ids;
        try {
            ids = mapper.writeValueAsString(lecturerIds.stream().map(UUID::toString).toList());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize Lecturer assignments", e);
        }
        jdbc.update("UPDATE subject SET assigned_lecturer_ids_json=?::jsonb WHERE subject_id=?", ids, subjectId);
    }

    private SubjectAssignmentRecord record(UUID id, String code, String name, String rawIds) {
        try {
            List<UUID> ids = rawIds == null ? List.of() : mapper.readValue(rawIds, UUID_LIST);
            return new SubjectAssignmentRecord(id, code, name, List.copyOf(new ArrayList<>(new LinkedHashSet<>(ids))));
        } catch (Exception e) {
            throw new IllegalStateException("Subject Lecturer assignments contain invalid data", e);
        }
    }
}
