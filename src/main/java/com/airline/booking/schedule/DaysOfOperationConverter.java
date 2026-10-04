package com.airline.booking.schedule;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link DaysOfOperation} to the SMALLINT column and back.
 *
 * <p>MUST convert to {@code Short}, not {@code Integer}: with
 * {@code ddl-auto: validate}, Hibernate compares the converted type against the
 * column's actual SMALLINT and fails at startup on a mismatch.
 */
@Converter(autoApply = false)
public class DaysOfOperationConverter implements AttributeConverter<DaysOfOperation, Short> {

    @Override
    public Short convertToDatabaseColumn(DaysOfOperation attribute) {
        return attribute == null ? null : attribute.mask();
    }

    @Override
    public DaysOfOperation convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : DaysOfOperation.ofMask(dbData);
    }
}
