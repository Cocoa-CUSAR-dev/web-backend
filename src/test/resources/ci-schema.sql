-- Minimal schema for jOOQ codegen in CI / sandbox environments.
-- Only includes tables and columns referenced by the production Kotlin source.
-- This file is loaded into a fresh Postgres container so the gradle build can
-- run `generateJooq` and produce the same `com.cocoa.generated.*` classes that
-- a full `pg_dump` of the production NeonDB would produce for these tables.
--
-- Not exhaustive — missing columns/tables would break production builds but the
-- smoke tests pass because repositories are mocked at runtime.

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS ref;
CREATE SCHEMA IF NOT EXISTS agriculture;
CREATE SCHEMA IF NOT EXISTS collection;
CREATE SCHEMA IF NOT EXISTS storage;
CREATE SCHEMA IF NOT EXISTS form;
CREATE SCHEMA IF NOT EXISTS processing;
CREATE SCHEMA IF NOT EXISTS research;

-- auth
CREATE TABLE auth.user_account (
    user_id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_password_reset BOOLEAN NOT NULL DEFAULT FALSE,
    is_requires_password_reset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE auth.role (
    role_id UUID PRIMARY KEY,
    role_name VARCHAR(255) NOT NULL
);
CREATE TABLE auth.user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL
);
CREATE TABLE auth.permission (
    permission_id UUID PRIMARY KEY,
    permission_key VARCHAR(255) NOT NULL
);
CREATE TABLE auth.role_permission (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL
);

-- research
CREATE TABLE research.researcher (
    user_id UUID PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    organization VARCHAR(255)
);

-- agriculture
CREATE TABLE agriculture.farm (
    farm_id UUID PRIMARY KEY,
    farm_name VARCHAR(255) NOT NULL,
    found_date DATE,
    total_area NUMERIC(10,2),
    province_id UUID,
    district_id UUID,
    subdistrict_id UUID,
    geo_id UUID,
    contact_name VARCHAR(255),
    phone_number VARCHAR(50),
    line VARCHAR(255),
    facebook VARCHAR(255)
);
CREATE TABLE agriculture.farmer (
    user_id UUID PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255)
);

-- processing
CREATE TABLE processing.processor (
    user_id UUID PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255)
);

-- ref
CREATE TABLE ref.province_constant (
    province_id UUID PRIMARY KEY,
    province_name_en VARCHAR(255)
);
CREATE TABLE ref.district_constant (
    district_id UUID PRIMARY KEY,
    province_id UUID,
    district_name_en VARCHAR(255)
);
CREATE TABLE ref.subdistrict_constant (
    subdistrict_id UUID PRIMARY KEY,
    district_id UUID,
    subdistrict_name_en VARCHAR(255)
);

-- collection
CREATE TABLE collection.harvest (
    harvest_id UUID PRIMARY KEY,
    farm_id UUID NOT NULL,
    harvest_date TIMESTAMP NOT NULL
);
CREATE TABLE collection.harvest_grade_detail (
    harvest_id UUID NOT NULL,
    grade_code VARCHAR(10) NOT NULL,
    quantity_kg NUMERIC(10,2) NOT NULL,
    weight_gram_per_pod NUMERIC(10,2),
    is_clean BOOLEAN NOT NULL DEFAULT FALSE,
    is_appropriate_size BOOLEAN NOT NULL DEFAULT FALSE,
    is_sprout BOOLEAN NOT NULL DEFAULT FALSE,
    is_dry BOOLEAN NOT NULL DEFAULT FALSE,
    is_shriveled BOOLEAN NOT NULL DEFAULT FALSE,
    cut_test_result VARCHAR(255)
);

-- storage
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE storage.geo (
    geo_id UUID PRIMARY KEY,
    geom GEOMETRY
);

-- form
CREATE TABLE form.task (
    task_id UUID PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    task_type VARCHAR(50),
    open_at TIMESTAMP,
    close_at TIMESTAMP
);
CREATE TABLE form.task_form (
    form_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    title VARCHAR(255),
    content TEXT,
    handler VARCHAR(255),
    is_multiple_submit BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE form.section (
    section_id UUID PRIMARY KEY,
    form_id UUID NOT NULL,
    title VARCHAR(255),
    description TEXT,
    sort_order INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE form.question (
    question_id UUID PRIMARY KEY,
    section_id UUID NOT NULL,
    label VARCHAR(255),
    input_type VARCHAR(50),
    description TEXT,
    field_name VARCHAR(255),
    default_value TEXT,
    is_mandatory BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER
);
CREATE TABLE form.response (
    response_id UUID PRIMARY KEY,
    task_log_id UUID NOT NULL,
    user_id UUID NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answer JSONB,
    status VARCHAR(50)
);
