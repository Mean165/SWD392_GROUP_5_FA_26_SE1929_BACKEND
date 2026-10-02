package com.swd392.aiviva.user.specification;

import com.swd392.aiviva.user.dto.request.UserFilterRequest;
import com.swd392.aiviva.user.entity.Role;
import com.swd392.aiviva.user.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class UserSpecification {

    public static Specification<User> filter(UserFilterRequest filterRequest) {
        return (root, query, cb) -> {
            if (filterRequest == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(filterRequest.getFullName())) {
                predicates.add(cb.like(cb.lower(root.get("fullName")),
                        "%" + filterRequest.getFullName().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(filterRequest.getEmail())) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + filterRequest.getEmail().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(filterRequest.getPhoneNumber())) {
                predicates.add(cb.like(root.get("phoneNumber"),
                        "%" + filterRequest.getPhoneNumber().trim() + "%"));
            }

            if (StringUtils.hasText(filterRequest.getStudentOrStaffCode())) {
                predicates.add(cb.like(cb.lower(root.get("studentOrStaffCode")),
                        "%" + filterRequest.getStudentOrStaffCode().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(filterRequest.getRoleCode())) {
                Join<User, Role> roleJoin = root.join("role", JoinType.INNER);
                predicates.add(cb.equal(cb.upper(roleJoin.get("code")), filterRequest.getRoleCode().trim().toUpperCase()));
            }

            if (StringUtils.hasText(filterRequest.getDepartment())) {
                predicates.add(cb.like(cb.lower(root.get("department")),
                        "%" + filterRequest.getDepartment().trim().toLowerCase() + "%"));
            }

            if (filterRequest.getIsActive() != null) {
                predicates.add(cb.equal(root.get("isActive"), filterRequest.getIsActive()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
