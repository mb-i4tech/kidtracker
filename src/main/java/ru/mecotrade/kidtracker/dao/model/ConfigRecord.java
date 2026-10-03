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

import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import ru.mecotrade.kidtracker.model.Config;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Date;

@Data
@Entity
@Table(name="config",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"deviceId", "parameter"})})
public class ConfigRecord {

    @Id
    // Preserve Hibernate 5 global sequence and increment; never reset existing IDs.
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "legacy_sequence")
    @SequenceGenerator(name = "legacy_sequence", sequenceName = "hibernate_sequence", allocationSize = 1)
    private Long id;

    @UpdateTimestamp
    private Date timestamp;

    private String deviceId;

    private String parameter;

    private String value;

    public static ConfigRecord of(Config config) {
        ConfigRecord record = new ConfigRecord();
        record.setParameter(config.getParameter());
        record.setValue(config.getValue());
        return record;
    }

    public Config toConfig() {
        return new Config(parameter, value);
    }
}
