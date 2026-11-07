# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/resources/review`

SQL fixture files referenced by `Excel2RdbmsTest` for incremental Liquibase changelog verification; empty files verify excluded changesets, while non-empty files provide review SQL templates.

| File | Purpose |
| --- | --- |
| `table_1_value_12_type_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_12_type_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_1_value_12_type_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_12_type_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_1_value_12_type_before_hsqldb.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_12_type_before` on HSQLDB. |
| `table_1_value_12_type_before_postgresql.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_12_type_before` on PostgreSQL. |
| `table_1_value_13_type_after_hsqldb.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_13_type_after` on HSQLDB. |
| `table_1_value_13_type_after_postgresql.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_13_type_after` on PostgreSQL. |
| `table_1_value_13_type_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_13_type_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_1_value_13_type_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_13_type_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_1_value_14_create_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_14_create` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_1_value_14_create_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_14_create` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_1_value_2_type_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_2_type_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_1_value_2_type_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_2_type_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_1_value_2_type_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_2_type_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_1_value_2_type_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_1_value_2_type_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_1_value_7_mandatory_hsqldb.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_7_mandatory` on HSQLDB. |
| `table_1_value_7_mandatory_postgresql.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_1_value_7_mandatory` on PostgreSQL. |
| `table_2_table_1_10_to_value_field_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_10_to_value_field_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_10_to_value_field_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_10_to_value_field_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_10_to_value_field_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_10_to_value_field_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_10_to_value_field_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_10_to_value_field_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_11_to_value_field_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_11_to_value_field_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_11_to_value_field_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_11_to_value_field_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_11_to_value_field_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_11_to_value_field_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_11_to_value_field_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_11_to_value_field_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_11_type_before_hsqldb.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_2_table_1_11_type_before` on HSQLDB. |
| `table_2_table_1_11_type_before_postgresql.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_2_table_1_11_type_before` on PostgreSQL. |
| `table_2_table_1_12_to_value_field_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_12_to_value_field_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_12_to_value_field_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_12_to_value_field_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_2_to_foreign_key_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_to_foreign_key_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_2_to_foreign_key_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_to_foreign_key_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_2_to_foreign_key_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_to_foreign_key_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_2_to_foreign_key_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_to_foreign_key_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_2_type_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_type_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_2_type_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_type_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_2_type_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_type_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_2_type_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_2_type_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_3_mandatory_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_mandatory` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_3_mandatory_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_mandatory` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_3_to_foreign_key_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_to_foreign_key_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_3_to_foreign_key_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_to_foreign_key_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_3_to_foreign_key_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_to_foreign_key_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_3_to_foreign_key_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_3_to_foreign_key_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_4_changed_to_foreign_key_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_4_changed_to_foreign_key_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_4_changed_to_foreign_key_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_4_changed_to_foreign_key_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_4_changed_to_foreign_key_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_4_changed_to_foreign_key_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_4_changed_to_foreign_key_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_4_changed_to_foreign_key_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_5_to_foreign_key_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_to_foreign_key_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_5_to_foreign_key_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_to_foreign_key_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_5_type_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_type_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_5_type_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_type_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_5_type_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_type_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_5_type_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_5_type_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_6_to_foreign_key_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_6_to_foreign_key_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_6_to_foreign_key_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_6_to_foreign_key_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_8_to_value_field_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_8_to_value_field_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_8_to_value_field_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_8_to_value_field_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_8_to_value_field_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_8_to_value_field_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_8_to_value_field_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_8_to_value_field_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_9_changed_to_value_field_after_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_9_changed_to_value_field_after` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_9_changed_to_value_field_after_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_9_changed_to_value_field_after` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_9_changed_to_value_field_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_9_changed_to_value_field_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_9_changed_to_value_field_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_9_changed_to_value_field_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_2_table_1_to_value_field_before_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_to_value_field_before` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_2_table_1_to_value_field_before_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_2_table_1_to_value_field_before` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_9_value_1_size_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_1_size` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_9_value_1_size_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_1_size` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_9_value_2_size_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_2_size` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_9_value_2_size_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_2_size` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_9_value_3_size_hsqldb.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_3_size` executed against HSQLDB in `Excel2RdbmsTest`. |
| `table_9_value_3_size_postgresql.sql` | Placeholder review SQL fixture (`--to do`) for `table_9_value_3_size` executed against PostgreSQL in `Excel2RdbmsTest`. |
| `table_9_value_4_size_hsqldb.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_9_value_4_size` on HSQLDB. |
| `table_9_value_4_size_postgresql.sql` | Zero-byte fixture verified by `Excel2RdbmsTest` asserting no changeset references `table_9_value_4_size` on PostgreSQL. |
