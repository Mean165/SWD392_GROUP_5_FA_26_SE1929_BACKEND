package com.swd392.aiviva.auth.entity;

import com.swd392.aiviva.common.entity.BaseEntity;
import com.swd392.aiviva.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "user_authentication")
public class UserAuthentication extends BaseEntity {

    private String username;
    private String passwordHash;
    private Boolean enabled;
    private String provider;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}

