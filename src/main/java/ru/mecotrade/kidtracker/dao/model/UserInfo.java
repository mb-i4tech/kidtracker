/*
 * Copyright 2020 Sergey Shadchin (sergei.shadchin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.mecotrade.kidtracker.dao.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Collection;
import java.util.Date;

@Data
@ToString(exclude = {"kids", "password"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="\"user\"",
        indexes = {@Index(columnList = "admin")},
        uniqueConstraints = {@UniqueConstraint(columnNames = {"username"})})
public class UserInfo {

    @Id
    // Preserve Hibernate 5 global sequence and increment; never reset existing IDs.
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "legacy_sequence")
    @SequenceGenerator(name = "legacy_sequence", sequenceName = "hibernate_sequence", allocationSize = 1)
    private Long id;

    @CreationTimestamp
    private Date timestamp;

    @ManyToOne()
    @JoinColumn(name="createdBy")
    private UserInfo createdBy;

    private String username;

    private String password;

    @OneToMany(mappedBy = "user")
    private Collection<KidInfo> kids;

    private String name;

    private String phone;

    private Boolean admin;

    public boolean isAdmin() {
        return admin != null && admin;
    }
}
